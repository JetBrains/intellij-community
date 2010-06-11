# patch.py - patch file parsing routines
#
# Copyright 2006 Brendan Cully <brendan@kublai.com>
# Copyright 2007 Chris Mason <chris.mason@oracle.com>
#
# This software may be used and distributed according to the terms of the
# GNU General Public License version 2 or any later version.

from i18n import _
from node import hex, nullid, short
import base85, cmdutil, mdiff, util, diffhelpers, copies
import cStringIO, email.Parser, os, re
import sys, tempfile, zlib

gitre = re.compile('diff --git a/(.*) b/(.*)')

class PatchError(Exception):
    pass

class NoHunks(PatchError):
    pass

# helper functions

def copyfile(src, dst, basedir):
    abssrc, absdst = [util.canonpath(basedir, basedir, x) for x in [src, dst]]
    if os.path.exists(absdst):
        raise util.Abort(_("cannot create %s: destination already exists") %
                         dst)

    dstdir = os.path.dirname(absdst)
    if dstdir and not os.path.isdir(dstdir):
        try:
            os.makedirs(dstdir)
        except IOError:
            raise util.Abort(
                _("cannot create %s: unable to create destination directory")
                % dst)

    util.copyfile(abssrc, absdst)

# public functions

def split(stream):
    '''return an iterator of individual patches from a stream'''
    def isheader(line, inheader):
        if inheader and line[0] in (' ', '\t'):
            # continuation
            return True
        if line[0] in (' ', '-', '+'):
            # diff line - don't check for header pattern in there
            return False
        l = line.split(': ', 1)
        return len(l) == 2 and ' ' not in l[0]

    def chunk(lines):
        return cStringIO.StringIO(''.join(lines))

    def hgsplit(stream, cur):
        inheader = True

        for line in stream:
            if not line.strip():
                inheader = False
            if not inheader and line.startswith('# HG changeset patch'):
                yield chunk(cur)
                cur = []
                inheader = True

            cur.append(line)

        if cur:
            yield chunk(cur)

    def mboxsplit(stream, cur):
        for line in stream:
            if line.startswith('From '):
                for c in split(chunk(cur[1:])):
                    yield c
                cur = []

            cur.append(line)

        if cur:
            for c in split(chunk(cur[1:])):
                yield c

    def mimesplit(stream, cur):
        def msgfp(m):
            fp = cStringIO.StringIO()
            g = email.Generator.Generator(fp, mangle_from_=False)
            g.flatten(m)
            fp.seek(0)
            return fp

        for line in stream:
            cur.append(line)
        c = chunk(cur)

        m = email.Parser.Parser().parse(c)
        if not m.is_multipart():
            yield msgfp(m)
        else:
            ok_types = ('text/plain', 'text/x-diff', 'text/x-patch')
            for part in m.walk():
                ct = part.get_content_type()
                if ct not in ok_types:
                    continue
                yield msgfp(part)

    def headersplit(stream, cur):
        inheader = False

        for line in stream:
            if not inheader and isheader(line, inheader):
                yield chunk(cur)
                cur = []
                inheader = True
            if inheader and not isheader(line, inheader):
                inheader = False

            cur.append(line)

        if cur:
            yield chunk(cur)

    def remainder(cur):
        yield chunk(cur)

    class fiter(object):
        def __init__(self, fp):
            self.fp = fp

        def __iter__(self):
            return self

        def next(self):
            l = self.fp.readline()
            if not l:
                raise StopIteration
            return l

    inheader = False
    cur = []

    mimeheaders = ['content-type']

    if not hasattr(stream, 'next'):
        # http responses, for example, have readline but not next
        stream = fiter(stream)

    for line in stream:
        cur.append(line)
        if line.startswith('# HG changeset patch'):
            return hgsplit(stream, cur)
        elif line.startswith('From '):
            return mboxsplit(stream, cur)
        elif isheader(line, inheader):
            inheader = True
            if line.split(':', 1)[0].lower() in mimeheaders:
                # let email parser handle this
                return mimesplit(stream, cur)
        elif line.startswith('--- ') and inheader:
            # No evil headers seen by diff start, split by hand
            return headersplit(stream, cur)
        # Not enough info, keep reading

    # if we are here, we have a very plain patch
    return remainder(cur)

def extract(ui, fileobj):
    '''extract patch from data read from fileobj.

    patch can be a normal patch or contained in an email message.

    return tuple (filename, message, user, date, node, p1, p2).
    Any item in the returned tuple can be None. If filename is None,
    fileobj did not contain a patch. Caller must unlink filename when done.'''

    # attempt to detect the start of a patch
    # (this heuristic is borrowed from quilt)
    diffre = re.compile(r'^(?:Index:[ \t]|diff[ \t]|RCS file: |'
                        r'retrieving revision [0-9]+(\.[0-9]+)*$|'
                        r'---[ \t].*?^\+\+\+[ \t]|'
                        r'\*\*\*[ \t].*?^---[ \t])', re.MULTILINE|re.DOTALL)

    fd, tmpname = tempfile.mkstemp(prefix='hg-patch-')
    tmpfp = os.fdopen(fd, 'w')
    try:
        msg = email.Parser.Parser().parse(fileobj)

        subject = msg['Subject']
        user = msg['From']
        if not subject and not user:
            # Not an email, restore parsed headers if any
            subject = '\n'.join(': '.join(h) for h in msg.items()) + '\n'

        gitsendmail = 'git-send-email' in msg.get('X-Mailer', '')
        # should try to parse msg['Date']
        date = None
        nodeid = None
        branch = None
        parents = []

        if subject:
            if subject.startswith('[PATCH'):
                pend = subject.find(']')
                if pend >= 0:
                    subject = subject[pend + 1:].lstrip()
            subject = subject.replace('\n\t', ' ')
            ui.debug('Subject: %s\n' % subject)
        if user:
            ui.debug('From: %s\n' % user)
        diffs_seen = 0
        ok_types = ('text/plain', 'text/x-diff', 'text/x-patch')
        message = ''
        for part in msg.walk():
            content_type = part.get_content_type()
            ui.debug('Content-Type: %s\n' % content_type)
            if content_type not in ok_types:
                continue
            payload = part.get_payload(decode=True)
            m = diffre.search(payload)
            if m:
                hgpatch = False
                ignoretext = False

                ui.debug('found patch at byte %d\n' % m.start(0))
                diffs_seen += 1
                cfp = cStringIO.StringIO()
                for line in payload[:m.start(0)].splitlines():
                    if line.startswith('# HG changeset patch'):
                        ui.debug('patch generated by hg export\n')
                        hgpatch = True
                        # drop earlier commit message content
                        cfp.seek(0)
                        cfp.truncate()
                        subject = None
                    elif hgpatch:
                        if line.startswith('# User '):
                            user = line[7:]
                            ui.debug('From: %s\n' % user)
                        elif line.startswith("# Date "):
                            date = line[7:]
                        elif line.startswith("# Branch "):
                            branch = line[9:]
                        elif line.startswith("# Node ID "):
                            nodeid = line[10:]
                        elif line.startswith("# Parent "):
                            parents.append(line[10:])
                    elif line == '---' and gitsendmail:
                        ignoretext = True
                    if not line.startswith('# ') and not ignoretext:
                        cfp.write(line)
                        cfp.write('\n')
                message = cfp.getvalue()
                if tmpfp:
                    tmpfp.write(payload)
                    if not payload.endswith('\n'):
                        tmpfp.write('\n')
            elif not diffs_seen and message and content_type == 'text/plain':
                message += '\n' + payload
    except:
        tmpfp.close()
        os.unlink(tmpname)
        raise

    if subject and not message.startswith(subject):
        message = '%s\n%s' % (subject, message)
    tmpfp.close()
    if not diffs_seen:
        os.unlink(tmpname)
        return None, message, user, date, branch, None, None, None
    p1 = parents and parents.pop(0) or None
    p2 = parents and parents.pop(0) or None
    return tmpname, message, user, date, branch, nodeid, p1, p2

GP_PATCH  = 1 << 0  # we have to run patch
GP_FILTER = 1 << 1  # there's some copy/rename operation
GP_BINARY = 1 << 2  # there's a binary patch

class patchmeta(object):
    """Patched file metadata

    'op' is the performed operation within ADD, DELETE, RENAME, MODIFY
    or COPY.  'path' is patched file path. 'oldpath' is set to the
    origin file when 'op' is either COPY or RENAME, None otherwise. If
    file mode is changed, 'mode' is a tuple (islink, isexec) where
    'islink' is True if the file is a symlink and 'isexec' is True if
    the file is executable. Otherwise, 'mode' is None.
    """
    def __init__(self, path):
        self.path = path
        self.oldpath = None
        self.mode = None
        self.op = 'MODIFY'
        self.lineno = 0
        self.binary = False

    def setmode(self, mode):
        islink = mode & 020000
        isexec = mode & 0100
        self.mode = (islink, isexec)

def readgitpatch(lr):
    """extract git-style metadata about patches from <patchname>"""

    # Filter patch for git information
    gp = None
    gitpatches = []
    # Can have a git patch with only metadata, causing patch to complain
    dopatch = 0

    lineno = 0
    for line in lr:
        lineno += 1
        line = line.rstrip(' \r\n')
        if line.startswith('diff --git'):
            m = gitre.match(line)
            if m:
                if gp:
                    gitpatches.append(gp)
                dst = m.group(2)
                gp = patchmeta(dst)
                gp.lineno = lineno
        elif gp:
            if line.startswith('--- '):
                if gp.op in ('COPY', 'RENAME'):
                    dopatch |= GP_FILTER
                gitpatches.append(gp)
                gp = None
                dopatch |= GP_PATCH
                continue
            if line.startswith('rename from '):
                gp.op = 'RENAME'
                gp.oldpath = line[12:]
            elif line.startswith('rename to '):
                gp.path = line[10:]
            elif line.startswith('copy from '):
                gp.op = 'COPY'
                gp.oldpath = line[10:]
            elif line.startswith('copy to '):
                gp.path = line[8:]
            elif line.startswith('deleted file'):
                gp.op = 'DELETE'
                # is the deleted file a symlink?
                gp.setmode(int(line[-6:], 8))
            elif line.startswith('new file mode '):
                gp.op = 'ADD'
                gp.setmode(int(line[-6:], 8))
            elif line.startswith('new mode '):
                gp.setmode(int(line[-6:], 8))
            elif line.startswith('GIT binary patch'):
                dopatch |= GP_BINARY
                gp.binary = True
    if gp:
        gitpatches.append(gp)

    if not gitpatches:
        dopatch = GP_PATCH

    return (dopatch, gitpatches)

class linereader(object):
    # simple class to allow pushing lines back into the input stream
    def __init__(self, fp, textmode=False):
        self.fp = fp
        self.buf = []
        self.textmode = textmode
        self.eol = None

    def push(self, line):
        if line is not None:
            self.buf.append(line)

    def readline(self):
        if self.buf:
            l = self.buf[0]
            del self.buf[0]
            return l
        l = self.fp.readline()
        if not self.eol:
            if l.endswith('\r\n'):
                self.eol = '\r\n'
            elif l.endswith('\n'):
                self.eol = '\n'
        if self.textmode and l.endswith('\r\n'):
            l = l[:-2] + '\n'
        return l

    def __iter__(self):
        while 1:
            l = self.readline()
            if not l:
                break
            yield l

# @@ -start,len +start,len @@ or @@ -start +start @@ if len is 1
unidesc = re.compile('@@ -(\d+)(,(\d+))? \+(\d+)(,(\d+))? @@')
contextdesc = re.compile('(---|\*\*\*) (\d+)(,(\d+))? (---|\*\*\*)')
eolmodes = ['strict', 'crlf', 'lf', 'auto']

class patchfile(object):
    def __init__(self, ui, fname, opener, missing=False, eolmode='strict'):
        self.fname = fname
        self.eolmode = eolmode
        self.eol = None
        self.opener = opener
        self.ui = ui
        self.lines = []
        self.exists = False
        self.missing = missing
        if not missing:
            try:
                self.lines = self.readlines(fname)
                self.exists = True
            except IOError:
                pass
        else:
            self.ui.warn(_("unable to find '%s' for patching\n") % self.fname)

        self.hash = {}
        self.dirty = 0
        self.offset = 0
        self.skew = 0
        self.rej = []
        self.fileprinted = False
        self.printfile(False)
        self.hunks = 0

    def readlines(self, fname):
        if os.path.islink(fname):
            return [os.readlink(fname)]
        fp = self.opener(fname, 'r')
        try:
            lr = linereader(fp, self.eolmode != 'strict')
            lines = list(lr)
            self.eol = lr.eol
            return lines
        finally:
            fp.close()

    def writelines(self, fname, lines):
        # Ensure supplied data ends in fname, being a regular file or
        # a symlink. updatedir() will -too magically- take care of
        # setting it to the proper type afterwards.
        islink = os.path.islink(fname)
        if islink:
            fp = cStringIO.StringIO()
        else:
            fp = self.opener(fname, 'w')
        try:
            if self.eolmode == 'auto':
                eol = self.eol
            elif self.eolmode == 'crlf':
                eol = '\r\n'
            else:
                eol = '\n'

            if self.eolmode != 'strict' and eol and eol != '\n':
                for l in lines:
                    if l and l[-1] == '\n':
                        l = l[:-1] + eol
                    fp.write(l)
            else:
                fp.writelines(lines)
            if islink:
                self.opener.symlink(fp.getvalue(), fname)
        finally:
            fp.close()

    def unlink(self, fname):
        os.unlink(fname)

    def printfile(self, warn):
        if self.fileprinted:
            return
        if warn or self.ui.verbose:
            self.fileprinted = True
        s = _("patching file %s\n") % self.fname
        if warn:
            self.ui.warn(s)
        else:
            self.ui.note(s)


    def findlines(self, l, linenum):
        # looks through the hash and finds candidate lines.  The
        # result is a list of line numbers sorted based on distance
        # from linenum

        cand = self.hash.get(l, [])
        if len(cand) > 1:
            # resort our list of potentials forward then back.
            cand.sort(key=lambda x: abs(x - linenum))
        return cand

    def hashlines(self):
        self.hash = {}
        for x, s in enumerate(self.lines):
            self.hash.setdefault(s, []).append(x)

    def write_rej(self):
        # our rejects are a little different from patch(1).  This always
        # creates rejects in the same form as the original patch.  A file
        # header is inserted so that you can run the reject through patch again
        # without having to type the filename.

        if not self.rej:
            return

        fname = self.fname + ".rej"
        self.ui.warn(
            _("%d out of %d hunks FAILED -- saving rejects to file %s\n") %
            (len(self.rej), self.hunks, fname))

        def rejlines():
            base = os.path.basename(self.fname)
            yield "--- %s\n+++ %s\n" % (base, base)
            for x in self.rej:
                for l in x.hunk:
                    yield l
                    if l[-1] != '\n':
                        yield "\n\ No newline at end of file\n"

        self.writelines(fname, rejlines())

    def write(self, dest=None):
        if not self.dirty:
            return
        if not dest:
            dest = self.fname
        self.writelines(dest, self.lines)

    def close(self):
        self.write()
        self.write_rej()

    def apply(self, h):
        if not h.complete():
            raise PatchError(_("bad hunk #%d %s (%d %d %d %d)") %
                            (h.number, h.desc, len(h.a), h.lena, len(h.b),
                            h.lenb))

        self.hunks += 1

        if self.missing:
            self.rej.append(h)
            return -1

        if self.exists and h.createfile():
            self.ui.warn(_("file %s already exists\n") % self.fname)
            self.rej.append(h)
            return -1

        if isinstance(h, binhunk):
            if h.rmfile():
                self.unlink(self.fname)
            else:
                self.lines[:] = h.new()
                self.offset += len(h.new())
                self.dirty = 1
            return 0

        horig = h
        if (self.eolmode in ('crlf', 'lf')
            or self.eolmode == 'auto' and self.eol):
            # If new eols are going to be normalized, then normalize
            # hunk data before patching. Otherwise, preserve input
            # line-endings.
            h = h.getnormalized()

        # fast case first, no offsets, no fuzz
        old = h.old()
        # patch starts counting at 1 unless we are adding the file
        if h.starta == 0:
            start = 0
        else:
            start = h.starta + self.offset - 1
        orig_start = start
        # if there's skew we want to emit the "(offset %d lines)" even
        # when the hunk cleanly applies at start + skew, so skip the
        # fast case code
        if self.skew == 0 and diffhelpers.testhunk(old, self.lines, start) == 0:
            if h.rmfile():
                self.unlink(self.fname)
            else:
                self.lines[start : start + h.lena] = h.new()
                self.offset += h.lenb - h.lena
                self.dirty = 1
            return 0

        # ok, we couldn't match the hunk.  Lets look for offsets and fuzz it
        self.hashlines()
        if h.hunk[-1][0] != ' ':
            # if the hunk tried to put something at the bottom of the file
            # override the start line and use eof here
            search_start = len(self.lines)
        else:
            search_start = orig_start + self.skew

        for fuzzlen in xrange(3):
            for toponly in [True, False]:
                old = h.old(fuzzlen, toponly)

                cand = self.findlines(old[0][1:], search_start)
                for l in cand:
                    if diffhelpers.testhunk(old, self.lines, l) == 0:
                        newlines = h.new(fuzzlen, toponly)
                        self.lines[l : l + len(old)] = newlines
                        self.offset += len(newlines) - len(old)
                        self.skew = l - orig_start
                        self.dirty = 1
                        offset = l - orig_start - fuzzlen
                        if fuzzlen:
                            msg = _("Hunk #%d succeeded at %d "
                                    "with fuzz %d "
                                    "(offset %d lines).\n")
                            self.printfile(True)
                            self.ui.warn(msg %
                                (h.number, l + 1, fuzzlen, offset))
                        else:
                            msg = _("Hunk #%d succeeded at %d "
                                    "(offset %d lines).\n")
                            self.ui.note(msg % (h.number, l + 1, offset))
                        return fuzzlen
        self.printfile(True)
        self.ui.warn(_("Hunk #%d FAILED at %d\n") % (h.number, orig_start))
        self.rej.append(horig)
        return -1

class hunk(object):
    def __init__(self, desc, num, lr, context, create=False, remove=False):
        self.number = num
        self.desc = desc
        self.hunk = [desc]
        self.a = []
        self.b = []
        self.starta = self.lena = None
        self.startb = self.lenb = None
        if lr is not None:
            if context:
                self.read_context_hunk(lr)
            else:
                self.read_unified_hunk(lr)
        self.create = create
        self.remove = remove and not create

    def getnormalized(self):
        """Return a copy with line endings normalized to LF."""

        def normalize(lines):
            nlines = []
            for line in lines:
                if line.endswith('\r\n'):
                    line = line[:-2] + '\n'
                nlines.append(line)
            return nlines

        # Dummy object, it is rebuilt manually
        nh = hunk(self.desc, self.number, None, None, False, False)
        nh.number = self.number
        nh.desc = self.desc
        nh.hunk = self.hunk
        nh.a = normalize(self.a)
        nh.b = normalize(self.b)
        nh.starta = self.starta
        nh.startb = self.startb
        nh.lena = self.lena
        nh.lenb = self.lenb
        nh.create = self.create
        nh.remove = self.remove
        return nh

    def read_unified_hunk(self, lr):
        m = unidesc.match(self.desc)
        if not m:
            raise PatchError(_("bad hunk #%d") % self.number)
        self.starta, foo, self.lena, self.startb, foo2, self.lenb = m.groups()
        if self.lena is None:
            self.lena = 1
        else:
            self.lena = int(self.lena)
        if self.lenb is None:
            self.lenb = 1
        else:
            self.lenb = int(self.lenb)
        self.starta = int(self.starta)
        self.startb = int(self.startb)
        diffhelpers.addlines(lr, self.hunk, self.lena, self.lenb, self.a, self.b)
        # if we hit eof before finishing out the hunk, the last line will
        # be zero length.  Lets try to fix it up.
        while len(self.hunk[-1]) == 0:
            del self.hunk[-1]
            del self.a[-1]
            del self.b[-1]
            self.lena -= 1
            self.lenb -= 1

    def read_context_hunk(self, lr):
        self.desc = lr.readline()
        m = contextdesc.match(self.desc)
        if not m:
            raise PatchError(_("bad hunk #%d") % self.number)
        foo, self.starta, foo2, aend, foo3 = m.groups()
        self.starta = int(self.starta)
        if aend is None:
            aend = self.starta
        self.lena = int(aend) - self.starta
        if self.starta:
            self.lena += 1
        for x in xrange(self.lena):
            l = lr.readline()
            if l.startswith('---'):
                lr.push(l)
                break
            s = l[2:]
            if l.startswith('- ') or l.startswith('! '):
                u = '-' + s
            elif l.startswith('  '):
                u = ' ' + s
            else:
                raise PatchError(_("bad hunk #%d old text line %d") %
                                 (self.number, x))
            self.a.append(u)
            self.hunk.append(u)

        l = lr.readline()
        if l.startswith('\ '):
            s = self.a[-1][:-1]
            self.a[-1] = s
            self.hunk[-1] = s
            l = lr.readline()
        m = contextdesc.match(l)
        if not m:
            raise PatchError(_("bad hunk #%d") % self.number)
        foo, self.startb, foo2, bend, foo3 = m.groups()
        self.startb = int(self.startb)
        if bend is None:
            bend = self.startb
        self.lenb = int(bend) - self.startb
        if self.startb:
            self.lenb += 1
        hunki = 1
        for x in xrange(self.lenb):
            l = lr.readline()
            if l.startswith('\ '):
                s = self.b[-1][:-1]
                self.b[-1] = s
                self.hunk[hunki - 1] = s
                continue
            if not l:
                lr.push(l)
                break
            s = l[2:]
            if l.startswith('+ ') or l.startswith('! '):
                u = '+' + s
            elif l.startswith('  '):
                u = ' ' + s
            elif len(self.b) == 0:
                # this can happen when the hunk does not add any lines
                lr.push(l)
                break
            else:
                raise PatchError(_("bad hunk #%d old text line %d") %
                                 (self.number, x))
            self.b.append(s)
            while True:
                if hunki >= len(self.hunk):
                    h = ""
                else:
                    h = self.hunk[hunki]
                hunki += 1
                if h == u:
                    break
                elif h.startswith('-'):
                    continue
                else:
                    self.hunk.insert(hunki - 1, u)
                    break

        if not self.a:
            # this happens when lines were only added to the hunk
            for x in self.hunk:
                if x.startswith('-') or x.startswith(' '):
                    self.a.append(x)
        if not self.b:
            # this happens when lines were only deleted from the hunk
            for x in self.hunk:
                if x.startswith('+') or x.startswith(' '):
                    self.b.append(x[1:])
        # @@ -start,len +start,len @@
        self.desc = "@@ -%d,%d +%d,%d @@\n" % (self.starta, self.lena,
                                             self.startb, self.lenb)
        self.hunk[0] = self.desc

    def fix_newline(self):
        diffhelpers.fix_newline(self.hunk, self.a, self.b)

    def complete(self):
        return len(self.a) == self.lena and len(self.b) == self.lenb

    def createfile(self):
        return self.starta == 0 and self.lena == 0 and self.create

    def rmfile(self):
        return self.startb == 0 and self.lenb == 0 and self.remove

    def fuzzit(self, l, fuzz, toponly):
        # this removes context lines from the top and bottom of list 'l'.  It
        # checks the hunk to make sure only context lines are removed, and then
        # returns a new shortened list of lines.
        fuzz = min(fuzz, len(l)-1)
        if fuzz:
            top = 0
            bot = 0
            hlen = len(self.hunk)
            for x in xrange(hlen - 1):
                # the hunk starts with the @@ line, so use x+1
                if self.hunk[x + 1][0] == ' ':
                    top += 1
                else:
                    break
            if not toponly:
                for x in xrange(hlen - 1):
                    if self.hunk[hlen - bot - 1][0] == ' ':
                        bot += 1
                    else:
                        break

            # top and bot now count context in the hunk
            # adjust them if either one is short
            context = max(top, bot, 3)
            if bot < context:
                bot = max(0, fuzz - (context - bot))
            else:
                bot = min(fuzz, bot)
            if top < context:
                top = max(0, fuzz - (context - top))
            else:
                top = min(fuzz, top)

            return l[top:len(l)-bot]
        return l

    def old(self, fuzz=0, toponly=False):
        return self.fuzzit(self.a, fuzz, toponly)

    def new(self, fuzz=0, toponly=False):
        return self.fuzzit(self.b, fuzz, toponly)

class binhunk:
    'A binary patch file. Only understands literals so far.'
    def __init__(self, gitpatch):
        self.gitpatch = gitpatch
        self.text = None
        self.hunk = ['GIT binary patch\n']

    def createfile(self):
        return self.gitpatch.op in ('ADD', 'RENAME', 'COPY')

    def rmfile(self):
        return self.gitpatch.op == 'DELETE'

    def complete(self):
        return self.text is not None

    def new(self):
        return [self.text]

    def extract(self, lr):
        line = lr.readline()
        self.hunk.append(line)
        while line and not line.startswith('literal '):
            line = lr.readline()
            self.hunk.append(line)
        if not line:
            raise PatchError(_('could not extract binary patch'))
        size = int(line[8:].rstrip())
        dec = []
        line = lr.readline()
        self.hunk.append(line)
        while len(line) > 1:
            l = line[0]
            if l <= 'Z' and l >= 'A':
                l = ord(l) - ord('A') + 1
            else:
                l = ord(l) - ord('a') + 27
            dec.append(base85.b85decode(line[1:-1])[:l])
            line = lr.readline()
            self.hunk.append(line)
        text = zlib.decompress(''.join(dec))
        if len(text) != size:
            raise PatchError(_('binary patch is %d bytes, not %d') %
                             len(text), size)
        self.text = text

def parsefilename(str):
    # --- filename \t|space stuff
    s = str[4:].rstrip('\r\n')
    i = s.find('\t')
    if i < 0:
        i = s.find(' ')
        if i < 0:
            return s
    return s[:i]

def selectfile(afile_orig, bfile_orig, hunk, strip):
    def pathstrip(path, count=1):
        pathlen = len(path)
        i = 0
        if count == 0:
            return '', path.rstrip()
        while count > 0:
            i = path.find('/', i)
            if i == -1:
                raise PatchError(_("unable to strip away %d dirs from %s") %
                                 (count, path))
            i += 1
            # consume '//' in the path
            while i < pathlen - 1 and path[i] == '/':
                i += 1
            count -= 1
        return path[:i].lstrip(), path[i:].rstrip()

    nulla = afile_orig == "/dev/null"
    nullb = bfile_orig == "/dev/null"
    abase, afile = pathstrip(afile_orig, strip)
    gooda = not nulla and util.lexists(afile)
    bbase, bfile = pathstrip(bfile_orig, strip)
    if afile == bfile:
        goodb = gooda
    else:
        goodb = not nullb and os.path.exists(bfile)
    createfunc = hunk.createfile
    missing = not goodb and not gooda and not createfunc()

    # some diff programs apparently produce create patches where the
    # afile is not /dev/null, but afile starts with bfile
    abasedir = afile[:afile.rfind('/') + 1]
    bbasedir = bfile[:bfile.rfind('/') + 1]
    if missing and abasedir == bbasedir and afile.startswith(bfile):
        # this isn't very pretty
        hunk.create = True
        if createfunc():
            missing = False
        else:
            hunk.create = False

    # If afile is "a/b/foo" and bfile is "a/b/foo.orig" we assume the
    # diff is between a file and its backup. In this case, the original
    # file should be patched (see original mpatch code).
    isbackup = (abase == bbase and bfile.startswith(afile))
    fname = None
    if not missing:
        if gooda and goodb:
            fname = isbackup and afile or bfile
        elif gooda:
            fname = afile

    if not fname:
        if not nullb:
            fname = isbackup and afile or bfile
        elif not nulla:
            fname = afile
        else:
            raise PatchError(_("undefined source and destination files"))

    return fname, missing

def scangitpatch(lr, firstline):
    """
    Git patches can emit:
    - rename a to b
    - change b
    - copy a to c
    - change c

    We cannot apply this sequence as-is, the renamed 'a' could not be
    found for it would have been renamed already. And we cannot copy
    from 'b' instead because 'b' would have been changed already. So
    we scan the git patch for copy and rename commands so we can
    perform the copies ahead of time.
    """
    pos = 0
    try:
        pos = lr.fp.tell()
        fp = lr.fp
    except IOError:
        fp = cStringIO.StringIO(lr.fp.read())
    gitlr = linereader(fp, lr.textmode)
    gitlr.push(firstline)
    (dopatch, gitpatches) = readgitpatch(gitlr)
    fp.seek(pos)
    return dopatch, gitpatches

def iterhunks(ui, fp, sourcefile=None):
    """Read a patch and yield the following events:
    - ("file", afile, bfile, firsthunk): select a new target file.
    - ("hunk", hunk): a new hunk is ready to be applied, follows a
    "file" event.
    - ("git", gitchanges): current diff is in git format, gitchanges
    maps filenames to gitpatch records. Unique event.
    """
    changed = {}
    current_hunk = None
    afile = ""
    bfile = ""
    state = None
    hunknum = 0
    emitfile = False
    git = False

    # our states
    BFILE = 1
    context = None
    lr = linereader(fp)
    # gitworkdone is True if a git operation (copy, rename, ...) was
    # performed already for the current file. Useful when the file
    # section may have no hunk.
    gitworkdone = False
    empty = None

    while True:
        newfile = newgitfile = False
        x = lr.readline()
        if not x:
            break
        if current_hunk:
            if x.startswith('\ '):
                current_hunk.fix_newline()
            yield 'hunk', current_hunk
            current_hunk = None
            empty = False
        if ((sourcefile or state == BFILE) and ((not context and x[0] == '@') or
            ((context is not False) and x.startswith('***************')))):
            try:
                if context is None and x.startswith('***************'):
                    context = True
                gpatch = changed.get(bfile)
                create = afile == '/dev/null' or gpatch and gpatch.op == 'ADD'
                remove = bfile == '/dev/null' or gpatch and gpatch.op == 'DELETE'
                current_hunk = hunk(x, hunknum + 1, lr, context, create, remove)
            except PatchError, err:
                ui.debug(err)
                current_hunk = None
                continue
            hunknum += 1
            if emitfile:
                emitfile = False
                yield 'file', (afile, bfile, current_hunk)
                empty = False
        elif state == BFILE and x.startswith('GIT binary patch'):
            current_hunk = binhunk(changed[bfile])
            hunknum += 1
            if emitfile:
                emitfile = False
                yield 'file', ('a/' + afile, 'b/' + bfile, current_hunk)
                empty = False
            current_hunk.extract(lr)
        elif x.startswith('diff --git'):
            # check for git diff, scanning the whole patch file if needed
            m = gitre.match(x)
            gitworkdone = False
            if m:
                afile, bfile = m.group(1, 2)
                if not git:
                    git = True
                    gitpatches = scangitpatch(lr, x)[1]
                    yield 'git', gitpatches
                    for gp in gitpatches:
                        changed[gp.path] = gp
                # else error?
                # copy/rename + modify should modify target, not source
                gp = changed.get(bfile)
                if gp and (gp.op in ('COPY', 'DELETE', 'RENAME', 'ADD')
                           or gp.mode):
                    afile = bfile
                    gitworkdone = True
                newgitfile = True
        elif x.startswith('---'):
            # check for a unified diff
            l2 = lr.readline()
            if not l2.startswith('+++'):
                lr.push(l2)
                continue
            newfile = True
            context = False
            afile = parsefilename(x)
            bfile = parsefilename(l2)
        elif x.startswith('***'):
            # check for a context diff
            l2 = lr.readline()
            if not l2.startswith('---'):
                lr.push(l2)
                continue
            l3 = lr.readline()
            lr.push(l3)
            if not l3.startswith("***************"):
                lr.push(l2)
                continue
            newfile = True
            context = True
            afile = parsefilename(x)
            bfile = parsefilename(l2)

        if newfile:
            if empty:
                raise NoHunks
            empty = not gitworkdone
            gitworkdone = False

        if newgitfile or newfile:
            emitfile = True
            state = BFILE
            hunknum = 0
    if current_hunk:
        if current_hunk.complete():
            yield 'hunk', current_hunk
            empty = False
        else:
            raise PatchError(_("malformed patch %s %s") % (afile,
                             current_hunk.desc))

    if (empty is None and not gitworkdone) or empty:
        raise NoHunks

def applydiff(ui, fp, changed, strip=1, sourcefile=None, eolmode='strict'):
    """
    Reads a patch from fp and tries to apply it.

    The dict 'changed' is filled in with all of the filenames changed
    by the patch. Returns 0 for a clean patch, -1 if any rejects were
    found and 1 if there was any fuzz.

    If 'eolmode' is 'strict', the patch content and patched file are
    read in binary mode. Otherwise, line endings are ignored when
    patching then normalized according to 'eolmode'.
    """
    rejects = 0
    err = 0
    current_file = None
    gitpatches = None
    opener = util.opener(os.getcwd())

    def closefile():
        if not current_file:
            return 0
        current_file.close()
        return len(current_file.rej)

    for state, values in iterhunks(ui, fp, sourcefile):
        if state == 'hunk':
            if not current_file:
                continue
            current_hunk = values
            ret = current_file.apply(current_hunk)
            if ret >= 0:
                changed.setdefault(current_file.fname, None)
                if ret > 0:
                    err = 1
        elif state == 'file':
            rejects += closefile()
            afile, bfile, first_hunk = values
            try:
                if sourcefile:
                    current_file = patchfile(ui, sourcefile, opener,
                                             eolmode=eolmode)
                else:
                    current_file, missing = selectfile(afile, bfile,
                                                       first_hunk, strip)
                    current_file = patchfile(ui, current_file, opener,
                                             missing, eolmode)
            except PatchError, err:
                ui.warn(str(err) + '\n')
                current_file, current_hunk = None, None
                rejects += 1
                continue
        elif state == 'git':
            gitpatches = values
            cwd = os.getcwd()
            for gp in gitpatches:
                if gp.op in ('COPY', 'RENAME'):
                    copyfile(gp.oldpath, gp.path, cwd)
                changed[gp.path] = gp
        else:
            raise util.Abort(_('unsupported parser state: %s') % state)

    rejects += closefile()

    if rejects:
        return -1
    return err

def diffopts(ui, opts=None, untrusted=False):
    def get(key, name=None, getter=ui.configbool):
        return ((opts and opts.get(key)) or
                getter('diff', name or key, None, untrusted=untrusted))
    return mdiff.diffopts(
        text=opts and opts.get('text'),
        git=get('git'),
        nodates=get('nodates'),
        showfunc=get('show_function', 'showfunc'),
        ignorews=get('ignore_all_space', 'ignorews'),
        ignorewsamount=get('ignore_space_change', 'ignorewsamount'),
        ignoreblanklines=get('ignore_blank_lines', 'ignoreblanklines'),
        context=get('unified', getter=ui.config))

def updatedir(ui, repo, patches, similarity=0):
    '''Update dirstate after patch application according to metadata'''
    if not patches:
        return
    copies = []
    removes = set()
    cfiles = patches.keys()
    cwd = repo.getcwd()
    if cwd:
        cfiles = [util.pathto(repo.root, cwd, f) for f in patches.keys()]
    for f in patches:
        gp = patches[f]
        if not gp:
            continue
        if gp.op == 'RENAME':
            copies.append((gp.oldpath, gp.path))
            removes.add(gp.oldpath)
        elif gp.op == 'COPY':
            copies.append((gp.oldpath, gp.path))
        elif gp.op == 'DELETE':
            removes.add(gp.path)
    for src, dst in copies:
        repo.copy(src, dst)
    if (not similarity) and removes:
        repo.remove(sorted(removes), True)
    for f in patches:
        gp = patches[f]
        if gp and gp.mode:
            islink, isexec = gp.mode
            dst = repo.wjoin(gp.path)
            # patch won't create empty files
            if gp.op == 'ADD' and not os.path.exists(dst):
                flags = (isexec and 'x' or '') + (islink and 'l' or '')
                repo.wwrite(gp.path, '', flags)
            elif gp.op != 'DELETE':
                util.set_flags(dst, islink, isexec)
    cmdutil.addremove(repo, cfiles, similarity=similarity)
    files = patches.keys()
    files.extend([r for r in removes if r not in files])
    return sorted(files)

def externalpatch(patcher, args, patchname, ui, strip, cwd, files):
    """use <patcher> to apply <patchname> to the working directory.
    returns whether patch was applied with fuzz factor."""

    fuzz = False
    if cwd:
        args.append('-d %s' % util.shellquote(cwd))
    fp = util.popen('%s %s -p%d < %s' % (patcher, ' '.join(args), strip,
                                       util.shellquote(patchname)))

    for line in fp:
        line = line.rstrip()
        ui.note(line + '\n')
        if line.startswith('patching file '):
            pf = util.parse_patch_output(line)
            printed_file = False
            files.setdefault(pf, None)
        elif line.find('with fuzz') >= 0:
            fuzz = True
            if not printed_file:
                ui.warn(pf + '\n')
                printed_file = True
            ui.warn(line + '\n')
        elif line.find('saving rejects to file') >= 0:
            ui.warn(line + '\n')
        elif line.find('FAILED') >= 0:
            if not printed_file:
                ui.warn(pf + '\n')
                printed_file = True
            ui.warn(line + '\n')
    code = fp.close()
    if code:
        raise PatchError(_("patch command failed: %s") %
                         util.explain_exit(code)[0])
    return fuzz

def internalpatch(patchobj, ui, strip, cwd, files=None, eolmode='strict'):
    """use builtin patch to apply <patchobj> to the working directory.
    returns whether patch was applied with fuzz factor."""

    if files is None:
        files = {}
    if eolmode is None:
        eolmode = ui.config('patch', 'eol', 'strict')
    if eolmode.lower() not in eolmodes:
        raise util.Abort(_('Unsupported line endings type: %s') % eolmode)
    eolmode = eolmode.lower()

    try:
        fp = open(patchobj, 'rb')
    except TypeError:
        fp = patchobj
    if cwd:
        curdir = os.getcwd()
        os.chdir(cwd)
    try:
        ret = applydiff(ui, fp, files, strip=strip, eolmode=eolmode)
    finally:
        if cwd:
            os.chdir(curdir)
        if fp != patchobj:
            fp.close()
    if ret < 0:
        raise PatchError
    return ret > 0

def patch(patchname, ui, strip=1, cwd=None, files=None, eolmode='strict'):
    """Apply <patchname> to the working directory.

    'eolmode' specifies how end of lines should be handled. It can be:
    - 'strict': inputs are read in binary mode, EOLs are preserved
    - 'crlf': EOLs are ignored when patching and reset to CRLF
    - 'lf': EOLs are ignored when patching and reset to LF
    - None: get it from user settings, default to 'strict'
    'eolmode' is ignored when using an external patcher program.

    Returns whether patch was applied with fuzz factor.
    """
    patcher = ui.config('ui', 'patch')
    args = []
    if files is None:
        files = {}
    try:
        if patcher:
            return externalpatch(patcher, args, patchname, ui, strip, cwd,
                                 files)
        else:
            try:
                return internalpatch(patchname, ui, strip, cwd, files, eolmode)
            except NoHunks:
                patcher = (util.find_exe('gpatch') or util.find_exe('patch')
                           or 'patch')
                ui.debug('no valid hunks found; trying with %r instead\n' %
                         patcher)
                if util.needbinarypatch():
                    args.append('--binary')
                return externalpatch(patcher, args, patchname, ui, strip, cwd,
                                     files)
    except PatchError, err:
        s = str(err)
        if s:
            raise util.Abort(s)
        else:
            raise util.Abort(_('patch failed to apply'))

def b85diff(to, tn):
    '''print base85-encoded binary diff'''
    def gitindex(text):
        if not text:
            return '0' * 40
        l = len(text)
        s = util.sha1('blob %d\0' % l)
        s.update(text)
        return s.hexdigest()

    def fmtline(line):
        l = len(line)
        if l <= 26:
            l = chr(ord('A') + l - 1)
        else:
            l = chr(l - 26 + ord('a') - 1)
        return '%c%s\n' % (l, base85.b85encode(line, True))

    def chunk(text, csize=52):
        l = len(text)
        i = 0
        while i < l:
            yield text[i:i + csize]
            i += csize

    tohash = gitindex(to)
    tnhash = gitindex(tn)
    if tohash == tnhash:
        return ""

    # TODO: deltas
    ret = ['index %s..%s\nGIT binary patch\nliteral %s\n' %
           (tohash, tnhash, len(tn))]
    for l in chunk(zlib.compress(tn)):
        ret.append(fmtline(l))
    ret.append('\n')
    return ''.join(ret)

class GitDiffRequired(Exception):
    pass

def diff(repo, node1=None, node2=None, match=None, changes=None, opts=None,
         losedatafn=None):
    '''yields diff of changes to files between two nodes, or node and
    working directory.

    if node1 is None, use first dirstate parent instead.
    if node2 is None, compare node1 with working directory.

    losedatafn(**kwarg) is a callable run when opts.upgrade=True and
    every time some change cannot be represented with the current
    patch format. Return False to upgrade to git patch format, True to
    accept the loss or raise an exception to abort the diff. It is
    called with the name of current file being diffed as 'fn'. If set
    to None, patches will always be upgraded to git format when
    necessary.
    '''

    if opts is None:
        opts = mdiff.defaultopts

    if not node1 and not node2:
        node1 = repo.dirstate.parents()[0]

    def lrugetfilectx():
        cache = {}
        order = []
        def getfilectx(f, ctx):
            fctx = ctx.filectx(f, filelog=cache.get(f))
            if f not in cache:
                if len(cache) > 20:
                    del cache[order.pop(0)]
                cache[f] = fctx.filelog()
            else:
                order.remove(f)
            order.append(f)
            return fctx
        return getfilectx
    getfilectx = lrugetfilectx()

    ctx1 = repo[node1]
    ctx2 = repo[node2]

    if not changes:
        changes = repo.status(ctx1, ctx2, match=match)
    modified, added, removed = changes[:3]

    if not modified and not added and not removed:
        return []

    revs = None
    if not repo.ui.quiet:
        hexfunc = repo.ui.debugflag and hex or short
        revs = [hexfunc(node) for node in [node1, node2] if node]

    copy = {}
    if opts.git or opts.upgrade:
        copy = copies.copies(repo, ctx1, ctx2, repo[nullid])[0]

    difffn = lambda opts, losedata: trydiff(repo, revs, ctx1, ctx2,
                 modified, added, removed, copy, getfilectx, opts, losedata)
    if opts.upgrade and not opts.git:
        try:
            def losedata(fn):
                if not losedatafn or not losedatafn(fn=fn):
                    raise GitDiffRequired()
            # Buffer the whole output until we are sure it can be generated
            return list(difffn(opts.copy(git=False), losedata))
        except GitDiffRequired:
            return difffn(opts.copy(git=True), None)
    else:
        return difffn(opts, None)

def _addmodehdr(header, omode, nmode):
    if omode != nmode:
        header.append('old mode %s\n' % omode)
        header.append('new mode %s\n' % nmode)

def trydiff(repo, revs, ctx1, ctx2, modified, added, removed,
            copy, getfilectx, opts, losedatafn):

    date1 = util.datestr(ctx1.date())
    man1 = ctx1.manifest()

    gone = set()
    gitmode = {'l': '120000', 'x': '100755', '': '100644'}

    copyto = dict([(v, k) for k, v in copy.items()])

    if opts.git:
        revs = None

    for f in sorted(modified + added + removed):
        to = None
        tn = None
        dodiff = True
        header = []
        if f in man1:
            to = getfilectx(f, ctx1).data()
        if f not in removed:
            tn = getfilectx(f, ctx2).data()
        a, b = f, f
        if opts.git or losedatafn:
            if f in added:
                mode = gitmode[ctx2.flags(f)]
                if f in copy or f in copyto:
                    if opts.git:
                        if f in copy:
                            a = copy[f]
                        else:
                            a = copyto[f]
                        omode = gitmode[man1.flags(a)]
                        _addmodehdr(header, omode, mode)
                        if a in removed and a not in gone:
                            op = 'rename'
                            gone.add(a)
                        else:
                            op = 'copy'
                        header.append('%s from %s\n' % (op, a))
                        header.append('%s to %s\n' % (op, f))
                        to = getfilectx(a, ctx1).data()
                    else:
                        losedatafn(f)
                else:
                    if opts.git:
                        header.append('new file mode %s\n' % mode)
                    elif ctx2.flags(f):
                        losedatafn(f)
                if util.binary(tn):
                    if opts.git:
                        dodiff = 'binary'
                    else:
                        losedatafn(f)
                if not opts.git and not tn:
                    # regular diffs cannot represent new empty file
                    losedatafn(f)
            elif f in removed:
                if opts.git:
                    # have we already reported a copy above?
                    if ((f in copy and copy[f] in added
                         and copyto[copy[f]] == f) or
                        (f in copyto and copyto[f] in added
                         and copy[copyto[f]] == f)):
                        dodiff = False
                    else:
                        header.append('deleted file mode %s\n' %
                                      gitmode[man1.flags(f)])
                elif not to:
                    # regular diffs cannot represent empty file deletion
                    losedatafn(f)
            else:
                oflag = man1.flags(f)
                nflag = ctx2.flags(f)
                binary = util.binary(to) or util.binary(tn)
                if opts.git:
                    _addmodehdr(header, gitmode[oflag], gitmode[nflag])
                    if binary:
                        dodiff = 'binary'
                elif binary or nflag != oflag:
                    losedatafn(f)
            if opts.git:
                header.insert(0, mdiff.diffline(revs, a, b, opts))

        if dodiff:
            if dodiff == 'binary':
                text = b85diff(to, tn)
            else:
                text = mdiff.unidiff(to, date1,
                                    # ctx2 date may be dynamic
                                    tn, util.datestr(ctx2.date()),
                                    a, b, revs, opts=opts)
            if header and (text or len(header) > 1):
                yield ''.join(header)
            if text:
                yield text

def export(repo, revs, template='hg-%h.patch', fp=None, switch_parent=False,
           opts=None):
    '''export changesets as hg patches.'''

    total = len(revs)
    revwidth = max([len(str(rev)) for rev in revs])

    def single(rev, seqno, fp):
        ctx = repo[rev]
        node = ctx.node()
        parents = [p.node() for p in ctx.parents() if p]
        branch = ctx.branch()
        if switch_parent:
            parents.reverse()
        prev = (parents and parents[0]) or nullid

        if not fp:
            fp = cmdutil.make_file(repo, template, node, total=total,
                                   seqno=seqno, revwidth=revwidth,
                                   mode='ab')
        if fp != sys.stdout and hasattr(fp, 'name'):
            repo.ui.note("%s\n" % fp.name)

        fp.write("# HG changeset patch\n")
        fp.write("# User %s\n" % ctx.user())
        fp.write("# Date %d %d\n" % ctx.date())
        if branch and (branch != 'default'):
            fp.write("# Branch %s\n" % branch)
        fp.write("# Node ID %s\n" % hex(node))
        fp.write("# Parent  %s\n" % hex(prev))
        if len(parents) > 1:
            fp.write("# Parent  %s\n" % hex(parents[1]))
        fp.write(ctx.description().rstrip())
        fp.write("\n\n")

        for chunk in diff(repo, prev, node, opts=opts):
            fp.write(chunk)

    for seqno, rev in enumerate(revs):
        single(rev, seqno + 1, fp)

def diffstatdata(lines):
    filename, adds, removes = None, 0, 0
    for line in lines:
        if line.startswith('diff'):
            if filename:
                isbinary = adds == 0 and removes == 0
                yield (filename, adds, removes, isbinary)
            # set numbers to 0 anyway when starting new file
            adds, removes = 0, 0
            if line.startswith('diff --git'):
                filename = gitre.search(line).group(1)
            else:
                # format: "diff -r ... -r ... filename"
                filename = line.split(None, 5)[-1]
        elif line.startswith('+') and not line.startswith('+++'):
            adds += 1
        elif line.startswith('-') and not line.startswith('---'):
            removes += 1
    if filename:
        isbinary = adds == 0 and removes == 0
        yield (filename, adds, removes, isbinary)

def diffstat(lines, width=80, git=False):
    output = []
    stats = list(diffstatdata(lines))

    maxtotal, maxname = 0, 0
    totaladds, totalremoves = 0, 0
    hasbinary = False
    for filename, adds, removes, isbinary in stats:
        totaladds += adds
        totalremoves += removes
        maxname = max(maxname, len(filename))
        maxtotal = max(maxtotal, adds + removes)
        if isbinary:
            hasbinary = True

    countwidth = len(str(maxtotal))
    if hasbinary and countwidth < 3:
        countwidth = 3
    graphwidth = width - countwidth - maxname - 6
    if graphwidth < 10:
        graphwidth = 10

    def scale(i):
        if maxtotal <= graphwidth:
            return i
        # If diffstat runs out of room it doesn't print anything,
        # which isn't very useful, so always print at least one + or -
        # if there were at least some changes.
        return max(i * graphwidth // maxtotal, int(bool(i)))

    for filename, adds, removes, isbinary in stats:
        if git and isbinary:
            count = 'Bin'
        else:
            count = adds + removes
        pluses = '+' * scale(adds)
        minuses = '-' * scale(removes)
        output.append(' %-*s |  %*s %s%s\n' % (maxname, filename, countwidth,
                                               count, pluses, minuses))

    if stats:
        output.append(_(' %d files changed, %d insertions(+), %d deletions(-)\n')
                      % (len(stats), totaladds, totalremoves))

    return ''.join(output)
