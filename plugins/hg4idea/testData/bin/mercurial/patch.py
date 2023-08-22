# patch.py - patch file parsing routines
#
# Copyright 2006 Brendan Cully <brendan@kublai.com>
# Copyright 2007 Chris Mason <chris.mason@oracle.com>
#
# This software may be used and distributed according to the terms of the
# GNU General Public License version 2 or any later version.

from __future__ import absolute_import, print_function

import collections
import contextlib
import copy
import errno
import os
import re
import shutil
import zlib

from .i18n import _
from .node import (
    hex,
    sha1nodeconstants,
    short,
)
from .pycompat import open
from . import (
    copies,
    diffhelper,
    diffutil,
    encoding,
    error,
    mail,
    mdiff,
    pathutil,
    pycompat,
    scmutil,
    similar,
    util,
    vfs as vfsmod,
)
from .utils import (
    dateutil,
    hashutil,
    procutil,
    stringutil,
)

stringio = util.stringio

gitre = re.compile(br'diff --git a/(.*) b/(.*)')
tabsplitter = re.compile(br'(\t+|[^\t]+)')
wordsplitter = re.compile(
    br'(\t+| +|[a-zA-Z0-9_\x80-\xff]+|[^ \ta-zA-Z0-9_\x80-\xff])'
)

PatchError = error.PatchError

# public functions


def split(stream):
    '''return an iterator of individual patches from a stream'''

    def isheader(line, inheader):
        if inheader and line.startswith((b' ', b'\t')):
            # continuation
            return True
        if line.startswith((b' ', b'-', b'+')):
            # diff line - don't check for header pattern in there
            return False
        l = line.split(b': ', 1)
        return len(l) == 2 and b' ' not in l[0]

    def chunk(lines):
        return stringio(b''.join(lines))

    def hgsplit(stream, cur):
        inheader = True

        for line in stream:
            if not line.strip():
                inheader = False
            if not inheader and line.startswith(b'# HG changeset patch'):
                yield chunk(cur)
                cur = []
                inheader = True

            cur.append(line)

        if cur:
            yield chunk(cur)

    def mboxsplit(stream, cur):
        for line in stream:
            if line.startswith(b'From '):
                for c in split(chunk(cur[1:])):
                    yield c
                cur = []

            cur.append(line)

        if cur:
            for c in split(chunk(cur[1:])):
                yield c

    def mimesplit(stream, cur):
        def msgfp(m):
            fp = stringio()
            g = mail.Generator(fp, mangle_from_=False)
            g.flatten(m)
            fp.seek(0)
            return fp

        for line in stream:
            cur.append(line)
        c = chunk(cur)

        m = mail.parse(c)
        if not m.is_multipart():
            yield msgfp(m)
        else:
            ok_types = (b'text/plain', b'text/x-diff', b'text/x-patch')
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

        __next__ = next

    inheader = False
    cur = []

    mimeheaders = [b'content-type']

    if not util.safehasattr(stream, b'next'):
        # http responses, for example, have readline but not next
        stream = fiter(stream)

    for line in stream:
        cur.append(line)
        if line.startswith(b'# HG changeset patch'):
            return hgsplit(stream, cur)
        elif line.startswith(b'From '):
            return mboxsplit(stream, cur)
        elif isheader(line, inheader):
            inheader = True
            if line.split(b':', 1)[0].lower() in mimeheaders:
                # let email parser handle this
                return mimesplit(stream, cur)
        elif line.startswith(b'--- ') and inheader:
            # No evil headers seen by diff start, split by hand
            return headersplit(stream, cur)
        # Not enough info, keep reading

    # if we are here, we have a very plain patch
    return remainder(cur)


## Some facility for extensible patch parsing:
# list of pairs ("header to match", "data key")
patchheadermap = [
    (b'Date', b'date'),
    (b'Branch', b'branch'),
    (b'Node ID', b'nodeid'),
]


@contextlib.contextmanager
def extract(ui, fileobj):
    """extract patch from data read from fileobj.

    patch can be a normal patch or contained in an email message.

    return a dictionary. Standard keys are:
      - filename,
      - message,
      - user,
      - date,
      - branch,
      - node,
      - p1,
      - p2.
    Any item can be missing from the dictionary. If filename is missing,
    fileobj did not contain a patch. Caller must unlink filename when done."""

    fd, tmpname = pycompat.mkstemp(prefix=b'hg-patch-')
    tmpfp = os.fdopen(fd, 'wb')
    try:
        yield _extract(ui, fileobj, tmpname, tmpfp)
    finally:
        tmpfp.close()
        os.unlink(tmpname)


def _extract(ui, fileobj, tmpname, tmpfp):

    # attempt to detect the start of a patch
    # (this heuristic is borrowed from quilt)
    diffre = re.compile(
        br'^(?:Index:[ \t]|diff[ \t]-|RCS file: |'
        br'retrieving revision [0-9]+(\.[0-9]+)*$|'
        br'---[ \t].*?^\+\+\+[ \t]|'
        br'\*\*\*[ \t].*?^---[ \t])',
        re.MULTILINE | re.DOTALL,
    )

    data = {}

    msg = mail.parse(fileobj)

    subject = msg['Subject'] and mail.headdecode(msg['Subject'])
    data[b'user'] = msg['From'] and mail.headdecode(msg['From'])
    if not subject and not data[b'user']:
        # Not an email, restore parsed headers if any
        subject = (
            b'\n'.join(
                b': '.join(map(encoding.strtolocal, h)) for h in msg.items()
            )
            + b'\n'
        )

    # should try to parse msg['Date']
    parents = []

    nodeid = msg['X-Mercurial-Node']
    if nodeid:
        data[b'nodeid'] = nodeid = mail.headdecode(nodeid)
        ui.debug(b'Node ID: %s\n' % nodeid)

    if subject:
        if subject.startswith(b'[PATCH'):
            pend = subject.find(b']')
            if pend >= 0:
                subject = subject[pend + 1 :].lstrip()
        subject = re.sub(br'\n[ \t]+', b' ', subject)
        ui.debug(b'Subject: %s\n' % subject)
    if data[b'user']:
        ui.debug(b'From: %s\n' % data[b'user'])
    diffs_seen = 0
    ok_types = (b'text/plain', b'text/x-diff', b'text/x-patch')
    message = b''
    for part in msg.walk():
        content_type = pycompat.bytestr(part.get_content_type())
        ui.debug(b'Content-Type: %s\n' % content_type)
        if content_type not in ok_types:
            continue
        payload = part.get_payload(decode=True)
        m = diffre.search(payload)
        if m:
            hgpatch = False
            hgpatchheader = False
            ignoretext = False

            ui.debug(b'found patch at byte %d\n' % m.start(0))
            diffs_seen += 1
            cfp = stringio()
            for line in payload[: m.start(0)].splitlines():
                if line.startswith(b'# HG changeset patch') and not hgpatch:
                    ui.debug(b'patch generated by hg export\n')
                    hgpatch = True
                    hgpatchheader = True
                    # drop earlier commit message content
                    cfp.seek(0)
                    cfp.truncate()
                    subject = None
                elif hgpatchheader:
                    if line.startswith(b'# User '):
                        data[b'user'] = line[7:]
                        ui.debug(b'From: %s\n' % data[b'user'])
                    elif line.startswith(b"# Parent "):
                        parents.append(line[9:].lstrip())
                    elif line.startswith(b"# "):
                        for header, key in patchheadermap:
                            prefix = b'# %s ' % header
                            if line.startswith(prefix):
                                data[key] = line[len(prefix) :]
                                ui.debug(b'%s: %s\n' % (header, data[key]))
                    else:
                        hgpatchheader = False
                elif line == b'---':
                    ignoretext = True
                if not hgpatchheader and not ignoretext:
                    cfp.write(line)
                    cfp.write(b'\n')
            message = cfp.getvalue()
            if tmpfp:
                tmpfp.write(payload)
                if not payload.endswith(b'\n'):
                    tmpfp.write(b'\n')
        elif not diffs_seen and message and content_type == b'text/plain':
            message += b'\n' + payload

    if subject and not message.startswith(subject):
        message = b'%s\n%s' % (subject, message)
    data[b'message'] = message
    tmpfp.close()
    if parents:
        data[b'p1'] = parents.pop(0)
        if parents:
            data[b'p2'] = parents.pop(0)

    if diffs_seen:
        data[b'filename'] = tmpname

    return data


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
        self.op = b'MODIFY'
        self.binary = False

    def setmode(self, mode):
        islink = mode & 0o20000
        isexec = mode & 0o100
        self.mode = (islink, isexec)

    def copy(self):
        other = patchmeta(self.path)
        other.oldpath = self.oldpath
        other.mode = self.mode
        other.op = self.op
        other.binary = self.binary
        return other

    def _ispatchinga(self, afile):
        if afile == b'/dev/null':
            return self.op == b'ADD'
        return afile == b'a/' + (self.oldpath or self.path)

    def _ispatchingb(self, bfile):
        if bfile == b'/dev/null':
            return self.op == b'DELETE'
        return bfile == b'b/' + self.path

    def ispatching(self, afile, bfile):
        return self._ispatchinga(afile) and self._ispatchingb(bfile)

    def __repr__(self):
        return "<patchmeta %s %r>" % (self.op, self.path)


def readgitpatch(lr):
    """extract git-style metadata about patches from <patchname>"""

    # Filter patch for git information
    gp = None
    gitpatches = []
    for line in lr:
        line = line.rstrip(b'\r\n')
        if line.startswith(b'diff --git a/'):
            m = gitre.match(line)
            if m:
                if gp:
                    gitpatches.append(gp)
                dst = m.group(2)
                gp = patchmeta(dst)
        elif gp:
            if line.startswith(b'--- '):
                gitpatches.append(gp)
                gp = None
                continue
            if line.startswith(b'rename from '):
                gp.op = b'RENAME'
                gp.oldpath = line[12:]
            elif line.startswith(b'rename to '):
                gp.path = line[10:]
            elif line.startswith(b'copy from '):
                gp.op = b'COPY'
                gp.oldpath = line[10:]
            elif line.startswith(b'copy to '):
                gp.path = line[8:]
            elif line.startswith(b'deleted file'):
                gp.op = b'DELETE'
            elif line.startswith(b'new file mode '):
                gp.op = b'ADD'
                gp.setmode(int(line[-6:], 8))
            elif line.startswith(b'new mode '):
                gp.setmode(int(line[-6:], 8))
            elif line.startswith(b'GIT binary patch'):
                gp.binary = True
    if gp:
        gitpatches.append(gp)

    return gitpatches


class linereader(object):
    # simple class to allow pushing lines back into the input stream
    def __init__(self, fp):
        self.fp = fp
        self.buf = []

    def push(self, line):
        if line is not None:
            self.buf.append(line)

    def readline(self):
        if self.buf:
            l = self.buf[0]
            del self.buf[0]
            return l
        return self.fp.readline()

    def __iter__(self):
        return iter(self.readline, b'')


class abstractbackend(object):
    def __init__(self, ui):
        self.ui = ui

    def getfile(self, fname):
        """Return target file data and flags as a (data, (islink,
        isexec)) tuple. Data is None if file is missing/deleted.
        """
        raise NotImplementedError

    def setfile(self, fname, data, mode, copysource):
        """Write data to target file fname and set its mode. mode is a
        (islink, isexec) tuple. If data is None, the file content should
        be left unchanged. If the file is modified after being copied,
        copysource is set to the original file name.
        """
        raise NotImplementedError

    def unlink(self, fname):
        """Unlink target file."""
        raise NotImplementedError

    def writerej(self, fname, failed, total, lines):
        """Write rejected lines for fname. total is the number of hunks
        which failed to apply and total the total number of hunks for this
        files.
        """

    def exists(self, fname):
        raise NotImplementedError

    def close(self):
        raise NotImplementedError


class fsbackend(abstractbackend):
    def __init__(self, ui, basedir):
        super(fsbackend, self).__init__(ui)
        self.opener = vfsmod.vfs(basedir)

    def getfile(self, fname):
        if self.opener.islink(fname):
            return (self.opener.readlink(fname), (True, False))

        isexec = False
        try:
            isexec = self.opener.lstat(fname).st_mode & 0o100 != 0
        except OSError as e:
            if e.errno != errno.ENOENT:
                raise
        try:
            return (self.opener.read(fname), (False, isexec))
        except IOError as e:
            if e.errno != errno.ENOENT:
                raise
            return None, None

    def setfile(self, fname, data, mode, copysource):
        islink, isexec = mode
        if data is None:
            self.opener.setflags(fname, islink, isexec)
            return
        if islink:
            self.opener.symlink(data, fname)
        else:
            self.opener.write(fname, data)
            if isexec:
                self.opener.setflags(fname, False, True)

    def unlink(self, fname):
        rmdir = self.ui.configbool(b'experimental', b'removeemptydirs')
        self.opener.unlinkpath(fname, ignoremissing=True, rmdir=rmdir)

    def writerej(self, fname, failed, total, lines):
        fname = fname + b".rej"
        self.ui.warn(
            _(b"%d out of %d hunks FAILED -- saving rejects to file %s\n")
            % (failed, total, fname)
        )
        fp = self.opener(fname, b'w')
        fp.writelines(lines)
        fp.close()

    def exists(self, fname):
        return self.opener.lexists(fname)


class workingbackend(fsbackend):
    def __init__(self, ui, repo, similarity):
        super(workingbackend, self).__init__(ui, repo.root)
        self.repo = repo
        self.similarity = similarity
        self.removed = set()
        self.changed = set()
        self.copied = []

    def _checkknown(self, fname):
        if self.repo.dirstate[fname] == b'?' and self.exists(fname):
            raise PatchError(_(b'cannot patch %s: file is not tracked') % fname)

    def setfile(self, fname, data, mode, copysource):
        self._checkknown(fname)
        super(workingbackend, self).setfile(fname, data, mode, copysource)
        if copysource is not None:
            self.copied.append((copysource, fname))
        self.changed.add(fname)

    def unlink(self, fname):
        self._checkknown(fname)
        super(workingbackend, self).unlink(fname)
        self.removed.add(fname)
        self.changed.add(fname)

    def close(self):
        wctx = self.repo[None]
        changed = set(self.changed)
        for src, dst in self.copied:
            scmutil.dirstatecopy(self.ui, self.repo, wctx, src, dst)
        if self.removed:
            wctx.forget(sorted(self.removed))
            for f in self.removed:
                if f not in self.repo.dirstate:
                    # File was deleted and no longer belongs to the
                    # dirstate, it was probably marked added then
                    # deleted, and should not be considered by
                    # marktouched().
                    changed.discard(f)
        if changed:
            scmutil.marktouched(self.repo, changed, self.similarity)
        return sorted(self.changed)


class filestore(object):
    def __init__(self, maxsize=None):
        self.opener = None
        self.files = {}
        self.created = 0
        self.maxsize = maxsize
        if self.maxsize is None:
            self.maxsize = 4 * (2 ** 20)
        self.size = 0
        self.data = {}

    def setfile(self, fname, data, mode, copied=None):
        if self.maxsize < 0 or (len(data) + self.size) <= self.maxsize:
            self.data[fname] = (data, mode, copied)
            self.size += len(data)
        else:
            if self.opener is None:
                root = pycompat.mkdtemp(prefix=b'hg-patch-')
                self.opener = vfsmod.vfs(root)
            # Avoid filename issues with these simple names
            fn = b'%d' % self.created
            self.opener.write(fn, data)
            self.created += 1
            self.files[fname] = (fn, mode, copied)

    def getfile(self, fname):
        if fname in self.data:
            return self.data[fname]
        if not self.opener or fname not in self.files:
            return None, None, None
        fn, mode, copied = self.files[fname]
        return self.opener.read(fn), mode, copied

    def close(self):
        if self.opener:
            shutil.rmtree(self.opener.base)


class repobackend(abstractbackend):
    def __init__(self, ui, repo, ctx, store):
        super(repobackend, self).__init__(ui)
        self.repo = repo
        self.ctx = ctx
        self.store = store
        self.changed = set()
        self.removed = set()
        self.copied = {}

    def _checkknown(self, fname):
        if fname not in self.ctx:
            raise PatchError(_(b'cannot patch %s: file is not tracked') % fname)

    def getfile(self, fname):
        try:
            fctx = self.ctx[fname]
        except error.LookupError:
            return None, None
        flags = fctx.flags()
        return fctx.data(), (b'l' in flags, b'x' in flags)

    def setfile(self, fname, data, mode, copysource):
        if copysource:
            self._checkknown(copysource)
        if data is None:
            data = self.ctx[fname].data()
        self.store.setfile(fname, data, mode, copysource)
        self.changed.add(fname)
        if copysource:
            self.copied[fname] = copysource

    def unlink(self, fname):
        self._checkknown(fname)
        self.removed.add(fname)

    def exists(self, fname):
        return fname in self.ctx

    def close(self):
        return self.changed | self.removed


# @@ -start,len +start,len @@ or @@ -start +start @@ if len is 1
unidesc = re.compile(br'@@ -(\d+)(?:,(\d+))? \+(\d+)(?:,(\d+))? @@')
contextdesc = re.compile(br'(?:---|\*\*\*) (\d+)(?:,(\d+))? (?:---|\*\*\*)')
eolmodes = [b'strict', b'crlf', b'lf', b'auto']


class patchfile(object):
    def __init__(self, ui, gp, backend, store, eolmode=b'strict'):
        self.fname = gp.path
        self.eolmode = eolmode
        self.eol = None
        self.backend = backend
        self.ui = ui
        self.lines = []
        self.exists = False
        self.missing = True
        self.mode = gp.mode
        self.copysource = gp.oldpath
        self.create = gp.op in (b'ADD', b'COPY', b'RENAME')
        self.remove = gp.op == b'DELETE'
        if self.copysource is None:
            data, mode = backend.getfile(self.fname)
        else:
            data, mode = store.getfile(self.copysource)[:2]
        if data is not None:
            self.exists = self.copysource is None or backend.exists(self.fname)
            self.missing = False
            if data:
                self.lines = mdiff.splitnewlines(data)
            if self.mode is None:
                self.mode = mode
            if self.lines:
                # Normalize line endings
                if self.lines[0].endswith(b'\r\n'):
                    self.eol = b'\r\n'
                elif self.lines[0].endswith(b'\n'):
                    self.eol = b'\n'
                if eolmode != b'strict':
                    nlines = []
                    for l in self.lines:
                        if l.endswith(b'\r\n'):
                            l = l[:-2] + b'\n'
                        nlines.append(l)
                    self.lines = nlines
        else:
            if self.create:
                self.missing = False
            if self.mode is None:
                self.mode = (False, False)
        if self.missing:
            self.ui.warn(_(b"unable to find '%s' for patching\n") % self.fname)
            self.ui.warn(
                _(
                    b"(use '--prefix' to apply patch relative to the "
                    b"current directory)\n"
                )
            )

        self.hash = {}
        self.dirty = 0
        self.offset = 0
        self.skew = 0
        self.rej = []
        self.fileprinted = False
        self.printfile(False)
        self.hunks = 0

    def writelines(self, fname, lines, mode):
        if self.eolmode == b'auto':
            eol = self.eol
        elif self.eolmode == b'crlf':
            eol = b'\r\n'
        else:
            eol = b'\n'

        if self.eolmode != b'strict' and eol and eol != b'\n':
            rawlines = []
            for l in lines:
                if l and l.endswith(b'\n'):
                    l = l[:-1] + eol
                rawlines.append(l)
            lines = rawlines

        self.backend.setfile(fname, b''.join(lines), mode, self.copysource)

    def printfile(self, warn):
        if self.fileprinted:
            return
        if warn or self.ui.verbose:
            self.fileprinted = True
        s = _(b"patching file %s\n") % self.fname
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

    def write_rej(self):
        # our rejects are a little different from patch(1).  This always
        # creates rejects in the same form as the original patch.  A file
        # header is inserted so that you can run the reject through patch again
        # without having to type the filename.
        if not self.rej:
            return
        base = os.path.basename(self.fname)
        lines = [b"--- %s\n+++ %s\n" % (base, base)]
        for x in self.rej:
            for l in x.hunk:
                lines.append(l)
                if l[-1:] != b'\n':
                    lines.append(b'\n' + diffhelper.MISSING_NEWLINE_MARKER)
        self.backend.writerej(self.fname, len(self.rej), self.hunks, lines)

    def apply(self, h):
        if not h.complete():
            raise PatchError(
                _(b"bad hunk #%d %s (%d %d %d %d)")
                % (h.number, h.desc, len(h.a), h.lena, len(h.b), h.lenb)
            )

        self.hunks += 1

        if self.missing:
            self.rej.append(h)
            return -1

        if self.exists and self.create:
            if self.copysource:
                self.ui.warn(
                    _(b"cannot create %s: destination already exists\n")
                    % self.fname
                )
            else:
                self.ui.warn(_(b"file %s already exists\n") % self.fname)
            self.rej.append(h)
            return -1

        if isinstance(h, binhunk):
            if self.remove:
                self.backend.unlink(self.fname)
            else:
                l = h.new(self.lines)
                self.lines[:] = l
                self.offset += len(l)
                self.dirty = True
            return 0

        horig = h
        if (
            self.eolmode in (b'crlf', b'lf')
            or self.eolmode == b'auto'
            and self.eol
        ):
            # If new eols are going to be normalized, then normalize
            # hunk data before patching. Otherwise, preserve input
            # line-endings.
            h = h.getnormalized()

        # fast case first, no offsets, no fuzz
        old, oldstart, new, newstart = h.fuzzit(0, False)
        oldstart += self.offset
        orig_start = oldstart
        # if there's skew we want to emit the "(offset %d lines)" even
        # when the hunk cleanly applies at start + skew, so skip the
        # fast case code
        if self.skew == 0 and diffhelper.testhunk(old, self.lines, oldstart):
            if self.remove:
                self.backend.unlink(self.fname)
            else:
                self.lines[oldstart : oldstart + len(old)] = new
                self.offset += len(new) - len(old)
                self.dirty = True
            return 0

        # ok, we couldn't match the hunk. Lets look for offsets and fuzz it
        self.hash = {}
        for x, s in enumerate(self.lines):
            self.hash.setdefault(s, []).append(x)

        for fuzzlen in pycompat.xrange(
            self.ui.configint(b"patch", b"fuzz") + 1
        ):
            for toponly in [True, False]:
                old, oldstart, new, newstart = h.fuzzit(fuzzlen, toponly)
                oldstart = oldstart + self.offset + self.skew
                oldstart = min(oldstart, len(self.lines))
                if old:
                    cand = self.findlines(old[0][1:], oldstart)
                else:
                    # Only adding lines with no or fuzzed context, just
                    # take the skew in account
                    cand = [oldstart]

                for l in cand:
                    if not old or diffhelper.testhunk(old, self.lines, l):
                        self.lines[l : l + len(old)] = new
                        self.offset += len(new) - len(old)
                        self.skew = l - orig_start
                        self.dirty = True
                        offset = l - orig_start - fuzzlen
                        if fuzzlen:
                            msg = _(
                                b"Hunk #%d succeeded at %d "
                                b"with fuzz %d "
                                b"(offset %d lines).\n"
                            )
                            self.printfile(True)
                            self.ui.warn(
                                msg % (h.number, l + 1, fuzzlen, offset)
                            )
                        else:
                            msg = _(
                                b"Hunk #%d succeeded at %d "
                                b"(offset %d lines).\n"
                            )
                            self.ui.note(msg % (h.number, l + 1, offset))
                        return fuzzlen
        self.printfile(True)
        self.ui.warn(_(b"Hunk #%d FAILED at %d\n") % (h.number, orig_start))
        self.rej.append(horig)
        return -1

    def close(self):
        if self.dirty:
            self.writelines(self.fname, self.lines, self.mode)
        self.write_rej()
        return len(self.rej)


class header(object):
    """patch header"""

    diffgit_re = re.compile(b'diff --git a/(.*) b/(.*)$')
    diff_re = re.compile(b'diff -r .* (.*)$')
    allhunks_re = re.compile(b'(?:index|deleted file) ')
    pretty_re = re.compile(b'(?:new file|deleted file) ')
    special_re = re.compile(b'(?:index|deleted|copy|rename|new mode) ')
    newfile_re = re.compile(b'(?:new file|copy to|rename to)')

    def __init__(self, header):
        self.header = header
        self.hunks = []

    def binary(self):
        return any(h.startswith(b'index ') for h in self.header)

    def pretty(self, fp):
        for h in self.header:
            if h.startswith(b'index '):
                fp.write(_(b'this modifies a binary file (all or nothing)\n'))
                break
            if self.pretty_re.match(h):
                fp.write(h)
                if self.binary():
                    fp.write(_(b'this is a binary file\n'))
                break
            if h.startswith(b'---'):
                fp.write(
                    _(b'%d hunks, %d lines changed\n')
                    % (
                        len(self.hunks),
                        sum([max(h.added, h.removed) for h in self.hunks]),
                    )
                )
                break
            fp.write(h)

    def write(self, fp):
        fp.write(b''.join(self.header))

    def allhunks(self):
        return any(self.allhunks_re.match(h) for h in self.header)

    def files(self):
        match = self.diffgit_re.match(self.header[0])
        if match:
            fromfile, tofile = match.groups()
            if fromfile == tofile:
                return [fromfile]
            return [fromfile, tofile]
        else:
            return self.diff_re.match(self.header[0]).groups()

    def filename(self):
        return self.files()[-1]

    def __repr__(self):
        return '<header %s>' % (
            ' '.join(pycompat.rapply(pycompat.fsdecode, self.files()))
        )

    def isnewfile(self):
        return any(self.newfile_re.match(h) for h in self.header)

    def special(self):
        # Special files are shown only at the header level and not at the hunk
        # level for example a file that has been deleted is a special file.
        # The user cannot change the content of the operation, in the case of
        # the deleted file he has to take the deletion or not take it, he
        # cannot take some of it.
        # Newly added files are special if they are empty, they are not special
        # if they have some content as we want to be able to change it
        nocontent = len(self.header) == 2
        emptynewfile = self.isnewfile() and nocontent
        return emptynewfile or any(
            self.special_re.match(h) for h in self.header
        )


class recordhunk(object):
    """patch hunk

    XXX shouldn't we merge this with the other hunk class?
    """

    def __init__(
        self,
        header,
        fromline,
        toline,
        proc,
        before,
        hunk,
        after,
        maxcontext=None,
    ):
        def trimcontext(lines, reverse=False):
            if maxcontext is not None:
                delta = len(lines) - maxcontext
                if delta > 0:
                    if reverse:
                        return delta, lines[delta:]
                    else:
                        return delta, lines[:maxcontext]
            return 0, lines

        self.header = header
        trimedbefore, self.before = trimcontext(before, True)
        self.fromline = fromline + trimedbefore
        self.toline = toline + trimedbefore
        _trimedafter, self.after = trimcontext(after, False)
        self.proc = proc
        self.hunk = hunk
        self.added, self.removed = self.countchanges(self.hunk)

    def __eq__(self, v):
        if not isinstance(v, recordhunk):
            return False

        return (
            (v.hunk == self.hunk)
            and (v.proc == self.proc)
            and (self.fromline == v.fromline)
            and (self.header.files() == v.header.files())
        )

    def __hash__(self):
        return hash(
            (
                tuple(self.hunk),
                tuple(self.header.files()),
                self.fromline,
                self.proc,
            )
        )

    def countchanges(self, hunk):
        """hunk -> (n+,n-)"""
        add = len([h for h in hunk if h.startswith(b'+')])
        rem = len([h for h in hunk if h.startswith(b'-')])
        return add, rem

    def reversehunk(self):
        """return another recordhunk which is the reverse of the hunk

        If this hunk is diff(A, B), the returned hunk is diff(B, A). To do
        that, swap fromline/toline and +/- signs while keep other things
        unchanged.
        """
        m = {b'+': b'-', b'-': b'+', b'\\': b'\\'}
        hunk = [b'%s%s' % (m[l[0:1]], l[1:]) for l in self.hunk]
        return recordhunk(
            self.header,
            self.toline,
            self.fromline,
            self.proc,
            self.before,
            hunk,
            self.after,
        )

    def write(self, fp):
        delta = len(self.before) + len(self.after)
        if self.after and self.after[-1] == diffhelper.MISSING_NEWLINE_MARKER:
            delta -= 1
        fromlen = delta + self.removed
        tolen = delta + self.added
        fp.write(
            b'@@ -%d,%d +%d,%d @@%s\n'
            % (
                self.fromline,
                fromlen,
                self.toline,
                tolen,
                self.proc and (b' ' + self.proc),
            )
        )
        fp.write(b''.join(self.before + self.hunk + self.after))

    pretty = write

    def filename(self):
        return self.header.filename()

    @encoding.strmethod
    def __repr__(self):
        return b'<hunk %r@%d>' % (self.filename(), self.fromline)


def getmessages():
    return {
        b'multiple': {
            b'apply': _(b"apply change %d/%d to '%s'?"),
            b'discard': _(b"discard change %d/%d to '%s'?"),
            b'keep': _(b"keep change %d/%d to '%s'?"),
            b'record': _(b"record change %d/%d to '%s'?"),
        },
        b'single': {
            b'apply': _(b"apply this change to '%s'?"),
            b'discard': _(b"discard this change to '%s'?"),
            b'keep': _(b"keep this change to '%s'?"),
            b'record': _(b"record this change to '%s'?"),
        },
        b'help': {
            b'apply': _(
                b'[Ynesfdaq?]'
                b'$$ &Yes, apply this change'
                b'$$ &No, skip this change'
                b'$$ &Edit this change manually'
                b'$$ &Skip remaining changes to this file'
                b'$$ Apply remaining changes to this &file'
                b'$$ &Done, skip remaining changes and files'
                b'$$ Apply &all changes to all remaining files'
                b'$$ &Quit, applying no changes'
                b'$$ &? (display help)'
            ),
            b'discard': _(
                b'[Ynesfdaq?]'
                b'$$ &Yes, discard this change'
                b'$$ &No, skip this change'
                b'$$ &Edit this change manually'
                b'$$ &Skip remaining changes to this file'
                b'$$ Discard remaining changes to this &file'
                b'$$ &Done, skip remaining changes and files'
                b'$$ Discard &all changes to all remaining files'
                b'$$ &Quit, discarding no changes'
                b'$$ &? (display help)'
            ),
            b'keep': _(
                b'[Ynesfdaq?]'
                b'$$ &Yes, keep this change'
                b'$$ &No, skip this change'
                b'$$ &Edit this change manually'
                b'$$ &Skip remaining changes to this file'
                b'$$ Keep remaining changes to this &file'
                b'$$ &Done, skip remaining changes and files'
                b'$$ Keep &all changes to all remaining files'
                b'$$ &Quit, keeping all changes'
                b'$$ &? (display help)'
            ),
            b'record': _(
                b'[Ynesfdaq?]'
                b'$$ &Yes, record this change'
                b'$$ &No, skip this change'
                b'$$ &Edit this change manually'
                b'$$ &Skip remaining changes to this file'
                b'$$ Record remaining changes to this &file'
                b'$$ &Done, skip remaining changes and files'
                b'$$ Record &all changes to all remaining files'
                b'$$ &Quit, recording no changes'
                b'$$ &? (display help)'
            ),
        },
    }


def filterpatch(ui, headers, match, operation=None):
    """Interactively filter patch chunks into applied-only chunks"""
    messages = getmessages()

    if operation is None:
        operation = b'record'

    def prompt(skipfile, skipall, query, chunk):
        """prompt query, and process base inputs

        - y/n for the rest of file
        - y/n for the rest
        - ? (help)
        - q (quit)

        Return True/False and possibly updated skipfile and skipall.
        """
        newpatches = None
        if skipall is not None:
            return skipall, skipfile, skipall, newpatches
        if skipfile is not None:
            return skipfile, skipfile, skipall, newpatches
        while True:
            resps = messages[b'help'][operation]
            # IMPORTANT: keep the last line of this prompt short (<40 english
            # chars is a good target) because of issue6158.
            r = ui.promptchoice(b"%s\n(enter ? for help) %s" % (query, resps))
            ui.write(b"\n")
            if r == 8:  # ?
                for c, t in ui.extractchoices(resps)[1]:
                    ui.write(b'%s - %s\n' % (c, encoding.lower(t)))
                continue
            elif r == 0:  # yes
                ret = True
            elif r == 1:  # no
                ret = False
            elif r == 2:  # Edit patch
                if chunk is None:
                    ui.write(_(b'cannot edit patch for whole file'))
                    ui.write(b"\n")
                    continue
                if chunk.header.binary():
                    ui.write(_(b'cannot edit patch for binary file'))
                    ui.write(b"\n")
                    continue
                # Patch comment based on the Git one (based on comment at end of
                # https://mercurial-scm.org/wiki/RecordExtension)
                phelp = b'---' + _(
                    b"""
To remove '-' lines, make them ' ' lines (context).
To remove '+' lines, delete them.
Lines starting with # will be removed from the patch.

If the patch applies cleanly, the edited hunk will immediately be
added to the record list. If it does not apply cleanly, a rejects
file will be generated: you can use that when you try again. If
all lines of the hunk are removed, then the edit is aborted and
the hunk is left unchanged.
"""
                )
                (patchfd, patchfn) = pycompat.mkstemp(
                    prefix=b"hg-editor-", suffix=b".diff"
                )
                ncpatchfp = None
                try:
                    # Write the initial patch
                    f = util.nativeeolwriter(os.fdopen(patchfd, 'wb'))
                    chunk.header.write(f)
                    chunk.write(f)
                    f.write(
                        b''.join(
                            [b'# ' + i + b'\n' for i in phelp.splitlines()]
                        )
                    )
                    f.close()
                    # Start the editor and wait for it to complete
                    editor = ui.geteditor()
                    ret = ui.system(
                        b"%s \"%s\"" % (editor, patchfn),
                        environ={b'HGUSER': ui.username()},
                        blockedtag=b'filterpatch',
                    )
                    if ret != 0:
                        ui.warn(_(b"editor exited with exit code %d\n") % ret)
                        continue
                    # Remove comment lines
                    patchfp = open(patchfn, 'rb')
                    ncpatchfp = stringio()
                    for line in util.iterfile(patchfp):
                        line = util.fromnativeeol(line)
                        if not line.startswith(b'#'):
                            ncpatchfp.write(line)
                    patchfp.close()
                    ncpatchfp.seek(0)
                    newpatches = parsepatch(ncpatchfp)
                finally:
                    os.unlink(patchfn)
                    del ncpatchfp
                # Signal that the chunk shouldn't be applied as-is, but
                # provide the new patch to be used instead.
                ret = False
            elif r == 3:  # Skip
                ret = skipfile = False
            elif r == 4:  # file (Record remaining)
                ret = skipfile = True
            elif r == 5:  # done, skip remaining
                ret = skipall = False
            elif r == 6:  # all
                ret = skipall = True
            elif r == 7:  # quit
                raise error.CanceledError(_(b'user quit'))
            return ret, skipfile, skipall, newpatches

    seen = set()
    applied = {}  # 'filename' -> [] of chunks
    skipfile, skipall = None, None
    pos, total = 1, sum(len(h.hunks) for h in headers)
    for h in headers:
        pos += len(h.hunks)
        skipfile = None
        fixoffset = 0
        hdr = b''.join(h.header)
        if hdr in seen:
            continue
        seen.add(hdr)
        if skipall is None:
            h.pretty(ui)
        files = h.files()
        msg = _(b'examine changes to %s?') % _(b' and ').join(
            b"'%s'" % f for f in files
        )
        if all(match.exact(f) for f in files):
            r, skipall, np = True, None, None
        else:
            r, skipfile, skipall, np = prompt(skipfile, skipall, msg, None)
        if not r:
            continue
        applied[h.filename()] = [h]
        if h.allhunks():
            applied[h.filename()] += h.hunks
            continue
        for i, chunk in enumerate(h.hunks):
            if skipfile is None and skipall is None:
                chunk.pretty(ui)
            if total == 1:
                msg = messages[b'single'][operation] % chunk.filename()
            else:
                idx = pos - len(h.hunks) + i
                msg = messages[b'multiple'][operation] % (
                    idx,
                    total,
                    chunk.filename(),
                )
            r, skipfile, skipall, newpatches = prompt(
                skipfile, skipall, msg, chunk
            )
            if r:
                if fixoffset:
                    chunk = copy.copy(chunk)
                    chunk.toline += fixoffset
                applied[chunk.filename()].append(chunk)
            elif newpatches is not None:
                for newpatch in newpatches:
                    for newhunk in newpatch.hunks:
                        if fixoffset:
                            newhunk.toline += fixoffset
                        applied[newhunk.filename()].append(newhunk)
            else:
                fixoffset += chunk.removed - chunk.added
    return (
        sum(
            [
                h
                for h in pycompat.itervalues(applied)
                if h[0].special() or len(h) > 1
            ],
            [],
        ),
        {},
    )


class hunk(object):
    def __init__(self, desc, num, lr, context):
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

    def getnormalized(self):
        """Return a copy with line endings normalized to LF."""

        def normalize(lines):
            nlines = []
            for line in lines:
                if line.endswith(b'\r\n'):
                    line = line[:-2] + b'\n'
                nlines.append(line)
            return nlines

        # Dummy object, it is rebuilt manually
        nh = hunk(self.desc, self.number, None, None)
        nh.number = self.number
        nh.desc = self.desc
        nh.hunk = self.hunk
        nh.a = normalize(self.a)
        nh.b = normalize(self.b)
        nh.starta = self.starta
        nh.startb = self.startb
        nh.lena = self.lena
        nh.lenb = self.lenb
        return nh

    def read_unified_hunk(self, lr):
        m = unidesc.match(self.desc)
        if not m:
            raise PatchError(_(b"bad hunk #%d") % self.number)
        self.starta, self.lena, self.startb, self.lenb = m.groups()
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
        try:
            diffhelper.addlines(
                lr, self.hunk, self.lena, self.lenb, self.a, self.b
            )
        except error.ParseError as e:
            raise PatchError(_(b"bad hunk #%d: %s") % (self.number, e))
        # if we hit eof before finishing out the hunk, the last line will
        # be zero length.  Lets try to fix it up.
        while len(self.hunk[-1]) == 0:
            del self.hunk[-1]
            del self.a[-1]
            del self.b[-1]
            self.lena -= 1
            self.lenb -= 1
        self._fixnewline(lr)

    def read_context_hunk(self, lr):
        self.desc = lr.readline()
        m = contextdesc.match(self.desc)
        if not m:
            raise PatchError(_(b"bad hunk #%d") % self.number)
        self.starta, aend = m.groups()
        self.starta = int(self.starta)
        if aend is None:
            aend = self.starta
        self.lena = int(aend) - self.starta
        if self.starta:
            self.lena += 1
        for x in pycompat.xrange(self.lena):
            l = lr.readline()
            if l.startswith(b'---'):
                # lines addition, old block is empty
                lr.push(l)
                break
            s = l[2:]
            if l.startswith(b'- ') or l.startswith(b'! '):
                u = b'-' + s
            elif l.startswith(b'  '):
                u = b' ' + s
            else:
                raise PatchError(
                    _(b"bad hunk #%d old text line %d") % (self.number, x)
                )
            self.a.append(u)
            self.hunk.append(u)

        l = lr.readline()
        if l.startswith(br'\ '):
            s = self.a[-1][:-1]
            self.a[-1] = s
            self.hunk[-1] = s
            l = lr.readline()
        m = contextdesc.match(l)
        if not m:
            raise PatchError(_(b"bad hunk #%d") % self.number)
        self.startb, bend = m.groups()
        self.startb = int(self.startb)
        if bend is None:
            bend = self.startb
        self.lenb = int(bend) - self.startb
        if self.startb:
            self.lenb += 1
        hunki = 1
        for x in pycompat.xrange(self.lenb):
            l = lr.readline()
            if l.startswith(br'\ '):
                # XXX: the only way to hit this is with an invalid line range.
                # The no-eol marker is not counted in the line range, but I
                # guess there are diff(1) out there which behave differently.
                s = self.b[-1][:-1]
                self.b[-1] = s
                self.hunk[hunki - 1] = s
                continue
            if not l:
                # line deletions, new block is empty and we hit EOF
                lr.push(l)
                break
            s = l[2:]
            if l.startswith(b'+ ') or l.startswith(b'! '):
                u = b'+' + s
            elif l.startswith(b'  '):
                u = b' ' + s
            elif len(self.b) == 0:
                # line deletions, new block is empty
                lr.push(l)
                break
            else:
                raise PatchError(
                    _(b"bad hunk #%d old text line %d") % (self.number, x)
                )
            self.b.append(s)
            while True:
                if hunki >= len(self.hunk):
                    h = b""
                else:
                    h = self.hunk[hunki]
                hunki += 1
                if h == u:
                    break
                elif h.startswith(b'-'):
                    continue
                else:
                    self.hunk.insert(hunki - 1, u)
                    break

        if not self.a:
            # this happens when lines were only added to the hunk
            for x in self.hunk:
                if x.startswith(b'-') or x.startswith(b' '):
                    self.a.append(x)
        if not self.b:
            # this happens when lines were only deleted from the hunk
            for x in self.hunk:
                if x.startswith(b'+') or x.startswith(b' '):
                    self.b.append(x[1:])
        # @@ -start,len +start,len @@
        self.desc = b"@@ -%d,%d +%d,%d @@\n" % (
            self.starta,
            self.lena,
            self.startb,
            self.lenb,
        )
        self.hunk[0] = self.desc
        self._fixnewline(lr)

    def _fixnewline(self, lr):
        l = lr.readline()
        if l.startswith(br'\ '):
            diffhelper.fixnewline(self.hunk, self.a, self.b)
        else:
            lr.push(l)

    def complete(self):
        return len(self.a) == self.lena and len(self.b) == self.lenb

    def _fuzzit(self, old, new, fuzz, toponly):
        # this removes context lines from the top and bottom of list 'l'.  It
        # checks the hunk to make sure only context lines are removed, and then
        # returns a new shortened list of lines.
        fuzz = min(fuzz, len(old))
        if fuzz:
            top = 0
            bot = 0
            hlen = len(self.hunk)
            for x in pycompat.xrange(hlen - 1):
                # the hunk starts with the @@ line, so use x+1
                if self.hunk[x + 1].startswith(b' '):
                    top += 1
                else:
                    break
            if not toponly:
                for x in pycompat.xrange(hlen - 1):
                    if self.hunk[hlen - bot - 1].startswith(b' '):
                        bot += 1
                    else:
                        break

            bot = min(fuzz, bot)
            top = min(fuzz, top)
            return old[top : len(old) - bot], new[top : len(new) - bot], top
        return old, new, 0

    def fuzzit(self, fuzz, toponly):
        old, new, top = self._fuzzit(self.a, self.b, fuzz, toponly)
        oldstart = self.starta + top
        newstart = self.startb + top
        # zero length hunk ranges already have their start decremented
        if self.lena and oldstart > 0:
            oldstart -= 1
        if self.lenb and newstart > 0:
            newstart -= 1
        return old, oldstart, new, newstart


class binhunk(object):
    """A binary patch file."""

    def __init__(self, lr, fname):
        self.text = None
        self.delta = False
        self.hunk = [b'GIT binary patch\n']
        self._fname = fname
        self._read(lr)

    def complete(self):
        return self.text is not None

    def new(self, lines):
        if self.delta:
            return [applybindelta(self.text, b''.join(lines))]
        return [self.text]

    def _read(self, lr):
        def getline(lr, hunk):
            l = lr.readline()
            hunk.append(l)
            return l.rstrip(b'\r\n')

        while True:
            line = getline(lr, self.hunk)
            if not line:
                raise PatchError(
                    _(b'could not extract "%s" binary data') % self._fname
                )
            if line.startswith(b'literal '):
                size = int(line[8:].rstrip())
                break
            if line.startswith(b'delta '):
                size = int(line[6:].rstrip())
                self.delta = True
                break
        dec = []
        line = getline(lr, self.hunk)
        while len(line) > 1:
            l = line[0:1]
            if l <= b'Z' and l >= b'A':
                l = ord(l) - ord(b'A') + 1
            else:
                l = ord(l) - ord(b'a') + 27
            try:
                dec.append(util.b85decode(line[1:])[:l])
            except ValueError as e:
                raise PatchError(
                    _(b'could not decode "%s" binary patch: %s')
                    % (self._fname, stringutil.forcebytestr(e))
                )
            line = getline(lr, self.hunk)
        text = zlib.decompress(b''.join(dec))
        if len(text) != size:
            raise PatchError(
                _(b'"%s" length is %d bytes, should be %d')
                % (self._fname, len(text), size)
            )
        self.text = text


def parsefilename(str):
    # --- filename \t|space stuff
    s = str[4:].rstrip(b'\r\n')
    i = s.find(b'\t')
    if i < 0:
        i = s.find(b' ')
        if i < 0:
            return s
    return s[:i]


def reversehunks(hunks):
    '''reverse the signs in the hunks given as argument

    This function operates on hunks coming out of patch.filterpatch, that is
    a list of the form: [header1, hunk1, hunk2, header2...]. Example usage:

    >>> rawpatch = b"""diff --git a/folder1/g b/folder1/g
    ... --- a/folder1/g
    ... +++ b/folder1/g
    ... @@ -1,7 +1,7 @@
    ... +firstline
    ...  c
    ...  1
    ...  2
    ... + 3
    ... -4
    ...  5
    ...  d
    ... +lastline"""
    >>> hunks = parsepatch([rawpatch])
    >>> hunkscomingfromfilterpatch = []
    >>> for h in hunks:
    ...     hunkscomingfromfilterpatch.append(h)
    ...     hunkscomingfromfilterpatch.extend(h.hunks)

    >>> reversedhunks = reversehunks(hunkscomingfromfilterpatch)
    >>> from . import util
    >>> fp = util.stringio()
    >>> for c in reversedhunks:
    ...      c.write(fp)
    >>> fp.seek(0) or None
    >>> reversedpatch = fp.read()
    >>> print(pycompat.sysstr(reversedpatch))
    diff --git a/folder1/g b/folder1/g
    --- a/folder1/g
    +++ b/folder1/g
    @@ -1,4 +1,3 @@
    -firstline
     c
     1
     2
    @@ -2,6 +1,6 @@
     c
     1
     2
    - 3
    +4
     5
     d
    @@ -6,3 +5,2 @@
     5
     d
    -lastline

    '''

    newhunks = []
    for c in hunks:
        if util.safehasattr(c, b'reversehunk'):
            c = c.reversehunk()
        newhunks.append(c)
    return newhunks


def parsepatch(originalchunks, maxcontext=None):
    """patch -> [] of headers -> [] of hunks

    If maxcontext is not None, trim context lines if necessary.

    >>> rawpatch = b'''diff --git a/folder1/g b/folder1/g
    ... --- a/folder1/g
    ... +++ b/folder1/g
    ... @@ -1,8 +1,10 @@
    ...  1
    ...  2
    ... -3
    ...  4
    ...  5
    ...  6
    ... +6.1
    ... +6.2
    ...  7
    ...  8
    ... +9'''
    >>> out = util.stringio()
    >>> headers = parsepatch([rawpatch], maxcontext=1)
    >>> for header in headers:
    ...     header.write(out)
    ...     for hunk in header.hunks:
    ...         hunk.write(out)
    >>> print(pycompat.sysstr(out.getvalue()))
    diff --git a/folder1/g b/folder1/g
    --- a/folder1/g
    +++ b/folder1/g
    @@ -2,3 +2,2 @@
     2
    -3
     4
    @@ -6,2 +5,4 @@
     6
    +6.1
    +6.2
     7
    @@ -8,1 +9,2 @@
     8
    +9
    """

    class parser(object):
        """patch parsing state machine"""

        def __init__(self):
            self.fromline = 0
            self.toline = 0
            self.proc = b''
            self.header = None
            self.context = []
            self.before = []
            self.hunk = []
            self.headers = []

        def addrange(self, limits):
            self.addcontext([])
            fromstart, fromend, tostart, toend, proc = limits
            self.fromline = int(fromstart)
            self.toline = int(tostart)
            self.proc = proc

        def addcontext(self, context):
            if self.hunk:
                h = recordhunk(
                    self.header,
                    self.fromline,
                    self.toline,
                    self.proc,
                    self.before,
                    self.hunk,
                    context,
                    maxcontext,
                )
                self.header.hunks.append(h)
                self.fromline += len(self.before) + h.removed
                self.toline += len(self.before) + h.added
                self.before = []
                self.hunk = []
            self.context = context

        def addhunk(self, hunk):
            if self.context:
                self.before = self.context
                self.context = []
            if self.hunk:
                self.addcontext([])
            self.hunk = hunk

        def newfile(self, hdr):
            self.addcontext([])
            h = header(hdr)
            self.headers.append(h)
            self.header = h

        def addother(self, line):
            pass  # 'other' lines are ignored

        def finished(self):
            self.addcontext([])
            return self.headers

        transitions = {
            b'file': {
                b'context': addcontext,
                b'file': newfile,
                b'hunk': addhunk,
                b'range': addrange,
            },
            b'context': {
                b'file': newfile,
                b'hunk': addhunk,
                b'range': addrange,
                b'other': addother,
            },
            b'hunk': {
                b'context': addcontext,
                b'file': newfile,
                b'range': addrange,
            },
            b'range': {b'context': addcontext, b'hunk': addhunk},
            b'other': {b'other': addother},
        }

    p = parser()
    fp = stringio()
    fp.write(b''.join(originalchunks))
    fp.seek(0)

    state = b'context'
    for newstate, data in scanpatch(fp):
        try:
            p.transitions[state][newstate](p, data)
        except KeyError:
            raise PatchError(
                b'unhandled transition: %s -> %s' % (state, newstate)
            )
        state = newstate
    del fp
    return p.finished()


def pathtransform(path, strip, prefix):
    """turn a path from a patch into a path suitable for the repository

    prefix, if not empty, is expected to be normalized with a / at the end.

    Returns (stripped components, path in repository).

    >>> pathtransform(b'a/b/c', 0, b'')
    ('', 'a/b/c')
    >>> pathtransform(b'   a/b/c   ', 0, b'')
    ('', '   a/b/c')
    >>> pathtransform(b'   a/b/c   ', 2, b'')
    ('a/b/', 'c')
    >>> pathtransform(b'a/b/c', 0, b'd/e/')
    ('', 'd/e/a/b/c')
    >>> pathtransform(b'   a//b/c   ', 2, b'd/e/')
    ('a//b/', 'd/e/c')
    >>> pathtransform(b'a/b/c', 3, b'')
    Traceback (most recent call last):
    PatchError: unable to strip away 1 of 3 dirs from a/b/c
    """
    pathlen = len(path)
    i = 0
    if strip == 0:
        return b'', prefix + path.rstrip()
    count = strip
    while count > 0:
        i = path.find(b'/', i)
        if i == -1:
            raise PatchError(
                _(b"unable to strip away %d of %d dirs from %s")
                % (count, strip, path)
            )
        i += 1
        # consume '//' in the path
        while i < pathlen - 1 and path[i : i + 1] == b'/':
            i += 1
        count -= 1
    return path[:i].lstrip(), prefix + path[i:].rstrip()


def makepatchmeta(backend, afile_orig, bfile_orig, hunk, strip, prefix):
    nulla = afile_orig == b"/dev/null"
    nullb = bfile_orig == b"/dev/null"
    create = nulla and hunk.starta == 0 and hunk.lena == 0
    remove = nullb and hunk.startb == 0 and hunk.lenb == 0
    abase, afile = pathtransform(afile_orig, strip, prefix)
    gooda = not nulla and backend.exists(afile)
    bbase, bfile = pathtransform(bfile_orig, strip, prefix)
    if afile == bfile:
        goodb = gooda
    else:
        goodb = not nullb and backend.exists(bfile)
    missing = not goodb and not gooda and not create

    # some diff programs apparently produce patches where the afile is
    # not /dev/null, but afile starts with bfile
    abasedir = afile[: afile.rfind(b'/') + 1]
    bbasedir = bfile[: bfile.rfind(b'/') + 1]
    if (
        missing
        and abasedir == bbasedir
        and afile.startswith(bfile)
        and hunk.starta == 0
        and hunk.lena == 0
    ):
        create = True
        missing = False

    # If afile is "a/b/foo" and bfile is "a/b/foo.orig" we assume the
    # diff is between a file and its backup. In this case, the original
    # file should be patched (see original mpatch code).
    isbackup = abase == bbase and bfile.startswith(afile)
    fname = None
    if not missing:
        if gooda and goodb:
            if isbackup:
                fname = afile
            else:
                fname = bfile
        elif gooda:
            fname = afile

    if not fname:
        if not nullb:
            if isbackup:
                fname = afile
            else:
                fname = bfile
        elif not nulla:
            fname = afile
        else:
            raise PatchError(_(b"undefined source and destination files"))

    gp = patchmeta(fname)
    if create:
        gp.op = b'ADD'
    elif remove:
        gp.op = b'DELETE'
    return gp


def scanpatch(fp):
    """like patch.iterhunks, but yield different events

    - ('file',    [header_lines + fromfile + tofile])
    - ('context', [context_lines])
    - ('hunk',    [hunk_lines])
    - ('range',   (-start,len, +start,len, proc))
    """
    lines_re = re.compile(br'@@ -(\d+),(\d+) \+(\d+),(\d+) @@\s*(.*)')
    lr = linereader(fp)

    def scanwhile(first, p):
        """scan lr while predicate holds"""
        lines = [first]
        for line in iter(lr.readline, b''):
            if p(line):
                lines.append(line)
            else:
                lr.push(line)
                break
        return lines

    for line in iter(lr.readline, b''):
        if line.startswith(b'diff --git a/') or line.startswith(b'diff -r '):

            def notheader(line):
                s = line.split(None, 1)
                return not s or s[0] not in (b'---', b'diff')

            header = scanwhile(line, notheader)
            fromfile = lr.readline()
            if fromfile.startswith(b'---'):
                tofile = lr.readline()
                header += [fromfile, tofile]
            else:
                lr.push(fromfile)
            yield b'file', header
        elif line.startswith(b' '):
            cs = (b' ', b'\\')
            yield b'context', scanwhile(line, lambda l: l.startswith(cs))
        elif line.startswith((b'-', b'+')):
            cs = (b'-', b'+', b'\\')
            yield b'hunk', scanwhile(line, lambda l: l.startswith(cs))
        else:
            m = lines_re.match(line)
            if m:
                yield b'range', m.groups()
            else:
                yield b'other', line


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
        fp = stringio(lr.fp.read())
    gitlr = linereader(fp)
    gitlr.push(firstline)
    gitpatches = readgitpatch(gitlr)
    fp.seek(pos)
    return gitpatches


def iterhunks(fp):
    """Read a patch and yield the following events:
    - ("file", afile, bfile, firsthunk): select a new target file.
    - ("hunk", hunk): a new hunk is ready to be applied, follows a
    "file" event.
    - ("git", gitchanges): current diff is in git format, gitchanges
    maps filenames to gitpatch records. Unique event.
    """
    afile = b""
    bfile = b""
    state = None
    hunknum = 0
    emitfile = newfile = False
    gitpatches = None

    # our states
    BFILE = 1
    context = None
    lr = linereader(fp)

    for x in iter(lr.readline, b''):
        if state == BFILE and (
            (not context and x.startswith(b'@'))
            or (context is not False and x.startswith(b'***************'))
            or x.startswith(b'GIT binary patch')
        ):
            gp = None
            if gitpatches and gitpatches[-1].ispatching(afile, bfile):
                gp = gitpatches.pop()
            if x.startswith(b'GIT binary patch'):
                h = binhunk(lr, gp.path)
            else:
                if context is None and x.startswith(b'***************'):
                    context = True
                h = hunk(x, hunknum + 1, lr, context)
            hunknum += 1
            if emitfile:
                emitfile = False
                yield b'file', (afile, bfile, h, gp and gp.copy() or None)
            yield b'hunk', h
        elif x.startswith(b'diff --git a/'):
            m = gitre.match(x.rstrip(b'\r\n'))
            if not m:
                continue
            if gitpatches is None:
                # scan whole input for git metadata
                gitpatches = scangitpatch(lr, x)
                yield b'git', [
                    g.copy() for g in gitpatches if g.op in (b'COPY', b'RENAME')
                ]
                gitpatches.reverse()
            afile = b'a/' + m.group(1)
            bfile = b'b/' + m.group(2)
            while gitpatches and not gitpatches[-1].ispatching(afile, bfile):
                gp = gitpatches.pop()
                yield b'file', (
                    b'a/' + gp.path,
                    b'b/' + gp.path,
                    None,
                    gp.copy(),
                )
            if not gitpatches:
                raise PatchError(
                    _(b'failed to synchronize metadata for "%s"') % afile[2:]
                )
            newfile = True
        elif x.startswith(b'---'):
            # check for a unified diff
            l2 = lr.readline()
            if not l2.startswith(b'+++'):
                lr.push(l2)
                continue
            newfile = True
            context = False
            afile = parsefilename(x)
            bfile = parsefilename(l2)
        elif x.startswith(b'***'):
            # check for a context diff
            l2 = lr.readline()
            if not l2.startswith(b'---'):
                lr.push(l2)
                continue
            l3 = lr.readline()
            lr.push(l3)
            if not l3.startswith(b"***************"):
                lr.push(l2)
                continue
            newfile = True
            context = True
            afile = parsefilename(x)
            bfile = parsefilename(l2)

        if newfile:
            newfile = False
            emitfile = True
            state = BFILE
            hunknum = 0

    while gitpatches:
        gp = gitpatches.pop()
        yield b'file', (b'a/' + gp.path, b'b/' + gp.path, None, gp.copy())


def applybindelta(binchunk, data):
    """Apply a binary delta hunk
    The algorithm used is the algorithm from git's patch-delta.c
    """

    def deltahead(binchunk):
        i = 0
        for c in pycompat.bytestr(binchunk):
            i += 1
            if not (ord(c) & 0x80):
                return i
        return i

    out = b""
    s = deltahead(binchunk)
    binchunk = binchunk[s:]
    s = deltahead(binchunk)
    binchunk = binchunk[s:]
    i = 0
    while i < len(binchunk):
        cmd = ord(binchunk[i : i + 1])
        i += 1
        if cmd & 0x80:
            offset = 0
            size = 0
            if cmd & 0x01:
                offset = ord(binchunk[i : i + 1])
                i += 1
            if cmd & 0x02:
                offset |= ord(binchunk[i : i + 1]) << 8
                i += 1
            if cmd & 0x04:
                offset |= ord(binchunk[i : i + 1]) << 16
                i += 1
            if cmd & 0x08:
                offset |= ord(binchunk[i : i + 1]) << 24
                i += 1
            if cmd & 0x10:
                size = ord(binchunk[i : i + 1])
                i += 1
            if cmd & 0x20:
                size |= ord(binchunk[i : i + 1]) << 8
                i += 1
            if cmd & 0x40:
                size |= ord(binchunk[i : i + 1]) << 16
                i += 1
            if size == 0:
                size = 0x10000
            offset_end = offset + size
            out += data[offset:offset_end]
        elif cmd != 0:
            offset_end = i + cmd
            out += binchunk[i:offset_end]
            i += cmd
        else:
            raise PatchError(_(b'unexpected delta opcode 0'))
    return out


def applydiff(ui, fp, backend, store, strip=1, prefix=b'', eolmode=b'strict'):
    """Reads a patch from fp and tries to apply it.

    Returns 0 for a clean patch, -1 if any rejects were found and 1 if
    there was any fuzz.

    If 'eolmode' is 'strict', the patch content and patched file are
    read in binary mode. Otherwise, line endings are ignored when
    patching then normalized according to 'eolmode'.
    """
    return _applydiff(
        ui,
        fp,
        patchfile,
        backend,
        store,
        strip=strip,
        prefix=prefix,
        eolmode=eolmode,
    )


def _canonprefix(repo, prefix):
    if prefix:
        prefix = pathutil.canonpath(repo.root, repo.getcwd(), prefix)
        if prefix != b'':
            prefix += b'/'
    return prefix


def _applydiff(
    ui, fp, patcher, backend, store, strip=1, prefix=b'', eolmode=b'strict'
):
    prefix = _canonprefix(backend.repo, prefix)

    def pstrip(p):
        return pathtransform(p, strip - 1, prefix)[1]

    rejects = 0
    err = 0
    current_file = None

    for state, values in iterhunks(fp):
        if state == b'hunk':
            if not current_file:
                continue
            ret = current_file.apply(values)
            if ret > 0:
                err = 1
        elif state == b'file':
            if current_file:
                rejects += current_file.close()
                current_file = None
            afile, bfile, first_hunk, gp = values
            if gp:
                gp.path = pstrip(gp.path)
                if gp.oldpath:
                    gp.oldpath = pstrip(gp.oldpath)
            else:
                gp = makepatchmeta(
                    backend, afile, bfile, first_hunk, strip, prefix
                )
            if gp.op == b'RENAME':
                backend.unlink(gp.oldpath)
            if not first_hunk:
                if gp.op == b'DELETE':
                    backend.unlink(gp.path)
                    continue
                data, mode = None, None
                if gp.op in (b'RENAME', b'COPY'):
                    data, mode = store.getfile(gp.oldpath)[:2]
                    if data is None:
                        # This means that the old path does not exist
                        raise PatchError(
                            _(b"source file '%s' does not exist") % gp.oldpath
                        )
                if gp.mode:
                    mode = gp.mode
                    if gp.op == b'ADD':
                        # Added files without content have no hunk and
                        # must be created
                        data = b''
                if data or mode:
                    if gp.op in (b'ADD', b'RENAME', b'COPY') and backend.exists(
                        gp.path
                    ):
                        raise PatchError(
                            _(
                                b"cannot create %s: destination "
                                b"already exists"
                            )
                            % gp.path
                        )
                    backend.setfile(gp.path, data, mode, gp.oldpath)
                continue
            try:
                current_file = patcher(ui, gp, backend, store, eolmode=eolmode)
            except PatchError as inst:
                ui.warn(stringutil.forcebytestr(inst) + b'\n')
                current_file = None
                rejects += 1
                continue
        elif state == b'git':
            for gp in values:
                path = pstrip(gp.oldpath)
                data, mode = backend.getfile(path)
                if data is None:
                    # The error ignored here will trigger a getfile()
                    # error in a place more appropriate for error
                    # handling, and will not interrupt the patching
                    # process.
                    pass
                else:
                    store.setfile(path, data, mode)
        else:
            raise error.Abort(_(b'unsupported parser state: %s') % state)

    if current_file:
        rejects += current_file.close()

    if rejects:
        return -1
    return err


def _externalpatch(ui, repo, patcher, patchname, strip, files, similarity):
    """use <patcher> to apply <patchname> to the working directory.
    returns whether patch was applied with fuzz factor."""

    fuzz = False
    args = []
    cwd = repo.root
    if cwd:
        args.append(b'-d %s' % procutil.shellquote(cwd))
    cmd = b'%s %s -p%d < %s' % (
        patcher,
        b' '.join(args),
        strip,
        procutil.shellquote(patchname),
    )
    ui.debug(b'Using external patch tool: %s\n' % cmd)
    fp = procutil.popen(cmd, b'rb')
    try:
        for line in util.iterfile(fp):
            line = line.rstrip()
            ui.note(line + b'\n')
            if line.startswith(b'patching file '):
                pf = util.parsepatchoutput(line)
                printed_file = False
                files.add(pf)
            elif line.find(b'with fuzz') >= 0:
                fuzz = True
                if not printed_file:
                    ui.warn(pf + b'\n')
                    printed_file = True
                ui.warn(line + b'\n')
            elif line.find(b'saving rejects to file') >= 0:
                ui.warn(line + b'\n')
            elif line.find(b'FAILED') >= 0:
                if not printed_file:
                    ui.warn(pf + b'\n')
                    printed_file = True
                ui.warn(line + b'\n')
    finally:
        if files:
            scmutil.marktouched(repo, files, similarity)
    code = fp.close()
    if code:
        raise PatchError(
            _(b"patch command failed: %s") % procutil.explainexit(code)
        )
    return fuzz


def patchbackend(
    ui, backend, patchobj, strip, prefix, files=None, eolmode=b'strict'
):
    if files is None:
        files = set()
    if eolmode is None:
        eolmode = ui.config(b'patch', b'eol')
    if eolmode.lower() not in eolmodes:
        raise error.Abort(_(b'unsupported line endings type: %s') % eolmode)
    eolmode = eolmode.lower()

    store = filestore()
    try:
        fp = open(patchobj, b'rb')
    except TypeError:
        fp = patchobj
    try:
        ret = applydiff(
            ui, fp, backend, store, strip=strip, prefix=prefix, eolmode=eolmode
        )
    finally:
        if fp != patchobj:
            fp.close()
        files.update(backend.close())
        store.close()
    if ret < 0:
        raise PatchError(_(b'patch failed to apply'))
    return ret > 0


def internalpatch(
    ui,
    repo,
    patchobj,
    strip,
    prefix=b'',
    files=None,
    eolmode=b'strict',
    similarity=0,
):
    """use builtin patch to apply <patchobj> to the working directory.
    returns whether patch was applied with fuzz factor."""
    backend = workingbackend(ui, repo, similarity)
    return patchbackend(ui, backend, patchobj, strip, prefix, files, eolmode)


def patchrepo(
    ui, repo, ctx, store, patchobj, strip, prefix, files=None, eolmode=b'strict'
):
    backend = repobackend(ui, repo, ctx, store)
    return patchbackend(ui, backend, patchobj, strip, prefix, files, eolmode)


def patch(
    ui,
    repo,
    patchname,
    strip=1,
    prefix=b'',
    files=None,
    eolmode=b'strict',
    similarity=0,
):
    """Apply <patchname> to the working directory.

    'eolmode' specifies how end of lines should be handled. It can be:
    - 'strict': inputs are read in binary mode, EOLs are preserved
    - 'crlf': EOLs are ignored when patching and reset to CRLF
    - 'lf': EOLs are ignored when patching and reset to LF
    - None: get it from user settings, default to 'strict'
    'eolmode' is ignored when using an external patcher program.

    Returns whether patch was applied with fuzz factor.
    """
    patcher = ui.config(b'ui', b'patch')
    if files is None:
        files = set()
    if patcher:
        return _externalpatch(
            ui, repo, patcher, patchname, strip, files, similarity
        )
    return internalpatch(
        ui, repo, patchname, strip, prefix, files, eolmode, similarity
    )


def changedfiles(ui, repo, patchpath, strip=1, prefix=b''):
    backend = fsbackend(ui, repo.root)
    prefix = _canonprefix(repo, prefix)
    with open(patchpath, b'rb') as fp:
        changed = set()
        for state, values in iterhunks(fp):
            if state == b'file':
                afile, bfile, first_hunk, gp = values
                if gp:
                    gp.path = pathtransform(gp.path, strip - 1, prefix)[1]
                    if gp.oldpath:
                        gp.oldpath = pathtransform(
                            gp.oldpath, strip - 1, prefix
                        )[1]
                else:
                    gp = makepatchmeta(
                        backend, afile, bfile, first_hunk, strip, prefix
                    )
                changed.add(gp.path)
                if gp.op == b'RENAME':
                    changed.add(gp.oldpath)
            elif state not in (b'hunk', b'git'):
                raise error.Abort(_(b'unsupported parser state: %s') % state)
        return changed


class GitDiffRequired(Exception):
    pass


diffopts = diffutil.diffallopts
diffallopts = diffutil.diffallopts
difffeatureopts = diffutil.difffeatureopts


def diff(
    repo,
    node1=None,
    node2=None,
    match=None,
    changes=None,
    opts=None,
    losedatafn=None,
    pathfn=None,
    copy=None,
    copysourcematch=None,
    hunksfilterfn=None,
):
    """yields diff of changes to files between two nodes, or node and
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

    prefix is a filename prefix that is prepended to all filenames on
    display (used for subrepos).

    relroot, if not empty, must be normalized with a trailing /. Any match
    patterns that fall outside it will be ignored.

    copy, if not empty, should contain mappings {dst@y: src@x} of copy
    information.

    if copysourcematch is not None, then copy sources will be filtered by this
    matcher

    hunksfilterfn, if not None, should be a function taking a filectx and
    hunks generator that may yield filtered hunks.
    """
    if not node1 and not node2:
        node1 = repo.dirstate.p1()

    ctx1 = repo[node1]
    ctx2 = repo[node2]

    for fctx1, fctx2, hdr, hunks in diffhunks(
        repo,
        ctx1=ctx1,
        ctx2=ctx2,
        match=match,
        changes=changes,
        opts=opts,
        losedatafn=losedatafn,
        pathfn=pathfn,
        copy=copy,
        copysourcematch=copysourcematch,
    ):
        if hunksfilterfn is not None:
            # If the file has been removed, fctx2 is None; but this should
            # not occur here since we catch removed files early in
            # logcmdutil.getlinerangerevs() for 'hg log -L'.
            assert (
                fctx2 is not None
            ), b'fctx2 unexpectly None in diff hunks filtering'
            hunks = hunksfilterfn(fctx2, hunks)
        text = b''.join(b''.join(hlines) for hrange, hlines in hunks)
        if hdr and (text or len(hdr) > 1):
            yield b'\n'.join(hdr) + b'\n'
        if text:
            yield text


def diffhunks(
    repo,
    ctx1,
    ctx2,
    match=None,
    changes=None,
    opts=None,
    losedatafn=None,
    pathfn=None,
    copy=None,
    copysourcematch=None,
):
    """Yield diff of changes to files in the form of (`header`, `hunks`) tuples
    where `header` is a list of diff headers and `hunks` is an iterable of
    (`hunkrange`, `hunklines`) tuples.

    See diff() for the meaning of parameters.
    """

    if opts is None:
        opts = mdiff.defaultopts

    def lrugetfilectx():
        cache = {}
        order = collections.deque()

        def getfilectx(f, ctx):
            fctx = ctx.filectx(f, filelog=cache.get(f))
            if f not in cache:
                if len(cache) > 20:
                    del cache[order.popleft()]
                cache[f] = fctx.filelog()
            else:
                order.remove(f)
            order.append(f)
            return fctx

        return getfilectx

    getfilectx = lrugetfilectx()

    if not changes:
        changes = ctx1.status(ctx2, match=match)
    if isinstance(changes, list):
        modified, added, removed = changes[:3]
    else:
        modified, added, removed = (
            changes.modified,
            changes.added,
            changes.removed,
        )

    if not modified and not added and not removed:
        return []

    if repo.ui.debugflag:
        hexfunc = hex
    else:
        hexfunc = short
    revs = [hexfunc(node) for node in [ctx1.node(), ctx2.node()] if node]

    if copy is None:
        copy = {}
        if opts.git or opts.upgrade:
            copy = copies.pathcopies(ctx1, ctx2, match=match)

    if copysourcematch:
        # filter out copies where source side isn't inside the matcher
        # (copies.pathcopies() already filtered out the destination)
        copy = {
            dst: src
            for dst, src in pycompat.iteritems(copy)
            if copysourcematch(src)
        }

    modifiedset = set(modified)
    addedset = set(added)
    removedset = set(removed)
    for f in modified:
        if f not in ctx1:
            # Fix up added, since merged-in additions appear as
            # modifications during merges
            modifiedset.remove(f)
            addedset.add(f)
    for f in removed:
        if f not in ctx1:
            # Merged-in additions that are then removed are reported as removed.
            # They are not in ctx1, so We don't want to show them in the diff.
            removedset.remove(f)
    modified = sorted(modifiedset)
    added = sorted(addedset)
    removed = sorted(removedset)
    for dst, src in list(copy.items()):
        if src not in ctx1:
            # Files merged in during a merge and then copied/renamed are
            # reported as copies. We want to show them in the diff as additions.
            del copy[dst]

    prefetchmatch = scmutil.matchfiles(
        repo, list(modifiedset | addedset | removedset)
    )
    revmatches = [
        (ctx1.rev(), prefetchmatch),
        (ctx2.rev(), prefetchmatch),
    ]
    scmutil.prefetchfiles(repo, revmatches)

    def difffn(opts, losedata):
        return trydiff(
            repo,
            revs,
            ctx1,
            ctx2,
            modified,
            added,
            removed,
            copy,
            getfilectx,
            opts,
            losedata,
            pathfn,
        )

    if opts.upgrade and not opts.git:
        try:

            def losedata(fn):
                if not losedatafn or not losedatafn(fn=fn):
                    raise GitDiffRequired

            # Buffer the whole output until we are sure it can be generated
            return list(difffn(opts.copy(git=False), losedata))
        except GitDiffRequired:
            return difffn(opts.copy(git=True), None)
    else:
        return difffn(opts, None)


def diffsinglehunk(hunklines):
    """yield tokens for a list of lines in a single hunk"""
    for line in hunklines:
        # chomp
        chompline = line.rstrip(b'\r\n')
        # highlight tabs and trailing whitespace
        stripline = chompline.rstrip()
        if line.startswith(b'-'):
            label = b'diff.deleted'
        elif line.startswith(b'+'):
            label = b'diff.inserted'
        else:
            raise error.ProgrammingError(b'unexpected hunk line: %s' % line)
        for token in tabsplitter.findall(stripline):
            if token.startswith(b'\t'):
                yield (token, b'diff.tab')
            else:
                yield (token, label)

        if chompline != stripline:
            yield (chompline[len(stripline) :], b'diff.trailingwhitespace')
        if chompline != line:
            yield (line[len(chompline) :], b'')


def diffsinglehunkinline(hunklines):
    """yield tokens for a list of lines in a single hunk, with inline colors"""
    # prepare deleted, and inserted content
    a = bytearray()
    b = bytearray()
    for line in hunklines:
        if line[0:1] == b'-':
            a += line[1:]
        elif line[0:1] == b'+':
            b += line[1:]
        else:
            raise error.ProgrammingError(b'unexpected hunk line: %s' % line)
    # fast path: if either side is empty, use diffsinglehunk
    if not a or not b:
        for t in diffsinglehunk(hunklines):
            yield t
        return
    # re-split the content into words
    al = wordsplitter.findall(bytes(a))
    bl = wordsplitter.findall(bytes(b))
    # re-arrange the words to lines since the diff algorithm is line-based
    aln = [s if s == b'\n' else s + b'\n' for s in al]
    bln = [s if s == b'\n' else s + b'\n' for s in bl]
    an = b''.join(aln)
    bn = b''.join(bln)
    # run the diff algorithm, prepare atokens and btokens
    atokens = []
    btokens = []
    blocks = mdiff.allblocks(an, bn, lines1=aln, lines2=bln)
    for (a1, a2, b1, b2), btype in blocks:
        changed = btype == b'!'
        for token in mdiff.splitnewlines(b''.join(al[a1:a2])):
            atokens.append((changed, token))
        for token in mdiff.splitnewlines(b''.join(bl[b1:b2])):
            btokens.append((changed, token))

    # yield deleted tokens, then inserted ones
    for prefix, label, tokens in [
        (b'-', b'diff.deleted', atokens),
        (b'+', b'diff.inserted', btokens),
    ]:
        nextisnewline = True
        for changed, token in tokens:
            if nextisnewline:
                yield (prefix, label)
                nextisnewline = False
            # special handling line end
            isendofline = token.endswith(b'\n')
            if isendofline:
                chomp = token[:-1]  # chomp
                if chomp.endswith(b'\r'):
                    chomp = chomp[:-1]
                endofline = token[len(chomp) :]
                token = chomp.rstrip()  # detect spaces at the end
                endspaces = chomp[len(token) :]
            # scan tabs
            for maybetab in tabsplitter.findall(token):
                if b'\t' == maybetab[0:1]:
                    currentlabel = b'diff.tab'
                else:
                    if changed:
                        currentlabel = label + b'.changed'
                    else:
                        currentlabel = label + b'.unchanged'
                yield (maybetab, currentlabel)
            if isendofline:
                if endspaces:
                    yield (endspaces, b'diff.trailingwhitespace')
                yield (endofline, b'')
                nextisnewline = True


def difflabel(func, *args, **kw):
    '''yields 2-tuples of (output, label) based on the output of func()'''
    if kw.get('opts') and kw['opts'].worddiff:
        dodiffhunk = diffsinglehunkinline
    else:
        dodiffhunk = diffsinglehunk
    headprefixes = [
        (b'diff', b'diff.diffline'),
        (b'copy', b'diff.extended'),
        (b'rename', b'diff.extended'),
        (b'old', b'diff.extended'),
        (b'new', b'diff.extended'),
        (b'deleted', b'diff.extended'),
        (b'index', b'diff.extended'),
        (b'similarity', b'diff.extended'),
        (b'---', b'diff.file_a'),
        (b'+++', b'diff.file_b'),
    ]
    textprefixes = [
        (b'@', b'diff.hunk'),
        # - and + are handled by diffsinglehunk
    ]
    head = False

    # buffers a hunk, i.e. adjacent "-", "+" lines without other changes.
    hunkbuffer = []

    def consumehunkbuffer():
        if hunkbuffer:
            for token in dodiffhunk(hunkbuffer):
                yield token
            hunkbuffer[:] = []

    for chunk in func(*args, **kw):
        lines = chunk.split(b'\n')
        linecount = len(lines)
        for i, line in enumerate(lines):
            if head:
                if line.startswith(b'@'):
                    head = False
            else:
                if line and not line.startswith(
                    (b' ', b'+', b'-', b'@', b'\\')
                ):
                    head = True
            diffline = False
            if not head and line and line.startswith((b'+', b'-')):
                diffline = True

            prefixes = textprefixes
            if head:
                prefixes = headprefixes
            if diffline:
                # buffered
                bufferedline = line
                if i + 1 < linecount:
                    bufferedline += b"\n"
                hunkbuffer.append(bufferedline)
            else:
                # unbuffered
                for token in consumehunkbuffer():
                    yield token
                stripline = line.rstrip()
                for prefix, label in prefixes:
                    if stripline.startswith(prefix):
                        yield (stripline, label)
                        if line != stripline:
                            yield (
                                line[len(stripline) :],
                                b'diff.trailingwhitespace',
                            )
                        break
                else:
                    yield (line, b'')
                if i + 1 < linecount:
                    yield (b'\n', b'')
        for token in consumehunkbuffer():
            yield token


def diffui(*args, **kw):
    '''like diff(), but yields 2-tuples of (output, label) for ui.write()'''
    return difflabel(diff, *args, **kw)


def _filepairs(modified, added, removed, copy, opts):
    """generates tuples (f1, f2, copyop), where f1 is the name of the file
    before and f2 is the the name after. For added files, f1 will be None,
    and for removed files, f2 will be None. copyop may be set to None, 'copy'
    or 'rename' (the latter two only if opts.git is set)."""
    gone = set()

    copyto = {v: k for k, v in copy.items()}

    addedset, removedset = set(added), set(removed)

    for f in sorted(modified + added + removed):
        copyop = None
        f1, f2 = f, f
        if f in addedset:
            f1 = None
            if f in copy:
                if opts.git:
                    f1 = copy[f]
                    if f1 in removedset and f1 not in gone:
                        copyop = b'rename'
                        gone.add(f1)
                    else:
                        copyop = b'copy'
        elif f in removedset:
            f2 = None
            if opts.git:
                # have we already reported a copy above?
                if (
                    f in copyto
                    and copyto[f] in addedset
                    and copy[copyto[f]] == f
                ):
                    continue
        yield f1, f2, copyop


def _gitindex(text):
    if not text:
        text = b""
    l = len(text)
    s = hashutil.sha1(b'blob %d\0' % l)
    s.update(text)
    return hex(s.digest())


_gitmode = {b'l': b'120000', b'x': b'100755', b'': b'100644'}


def trydiff(
    repo,
    revs,
    ctx1,
    ctx2,
    modified,
    added,
    removed,
    copy,
    getfilectx,
    opts,
    losedatafn,
    pathfn,
):
    """given input data, generate a diff and yield it in blocks

    If generating a diff would lose data like flags or binary data and
    losedatafn is not None, it will be called.

    pathfn is applied to every path in the diff output.
    """

    if opts.noprefix:
        aprefix = bprefix = b''
    else:
        aprefix = b'a/'
        bprefix = b'b/'

    def diffline(f, revs):
        revinfo = b' '.join([b"-r %s" % rev for rev in revs])
        return b'diff %s %s' % (revinfo, f)

    def isempty(fctx):
        return fctx is None or fctx.size() == 0

    date1 = dateutil.datestr(ctx1.date())
    date2 = dateutil.datestr(ctx2.date())

    if not pathfn:
        pathfn = lambda f: f

    for f1, f2, copyop in _filepairs(modified, added, removed, copy, opts):
        content1 = None
        content2 = None
        fctx1 = None
        fctx2 = None
        flag1 = None
        flag2 = None
        if f1:
            fctx1 = getfilectx(f1, ctx1)
            if opts.git or losedatafn:
                flag1 = ctx1.flags(f1)
        if f2:
            fctx2 = getfilectx(f2, ctx2)
            if opts.git or losedatafn:
                flag2 = ctx2.flags(f2)
        # if binary is True, output "summary" or "base85", but not "text diff"
        if opts.text:
            binary = False
        else:
            binary = any(f.isbinary() for f in [fctx1, fctx2] if f is not None)

        if losedatafn and not opts.git:
            if (
                binary
                or
                # copy/rename
                f2 in copy
                or
                # empty file creation
                (not f1 and isempty(fctx2))
                or
                # empty file deletion
                (isempty(fctx1) and not f2)
                or
                # create with flags
                (not f1 and flag2)
                or
                # change flags
                (f1 and f2 and flag1 != flag2)
            ):
                losedatafn(f2 or f1)

        path1 = pathfn(f1 or f2)
        path2 = pathfn(f2 or f1)
        header = []
        if opts.git:
            header.append(
                b'diff --git %s%s %s%s' % (aprefix, path1, bprefix, path2)
            )
            if not f1:  # added
                header.append(b'new file mode %s' % _gitmode[flag2])
            elif not f2:  # removed
                header.append(b'deleted file mode %s' % _gitmode[flag1])
            else:  # modified/copied/renamed
                mode1, mode2 = _gitmode[flag1], _gitmode[flag2]
                if mode1 != mode2:
                    header.append(b'old mode %s' % mode1)
                    header.append(b'new mode %s' % mode2)
                if copyop is not None:
                    if opts.showsimilarity:
                        sim = similar.score(ctx1[path1], ctx2[path2]) * 100
                        header.append(b'similarity index %d%%' % sim)
                    header.append(b'%s from %s' % (copyop, path1))
                    header.append(b'%s to %s' % (copyop, path2))
        elif revs:
            header.append(diffline(path1, revs))

        #  fctx.is  | diffopts                | what to   | is fctx.data()
        #  binary() | text nobinary git index | output?   | outputted?
        # ------------------------------------|----------------------------
        #  yes      | no   no       no  *     | summary   | no
        #  yes      | no   no       yes *     | base85    | yes
        #  yes      | no   yes      no  *     | summary   | no
        #  yes      | no   yes      yes 0     | summary   | no
        #  yes      | no   yes      yes >0    | summary   | semi [1]
        #  yes      | yes  *        *   *     | text diff | yes
        #  no       | *    *        *   *     | text diff | yes
        # [1]: hash(fctx.data()) is outputted. so fctx.data() cannot be faked
        if binary and (
            not opts.git or (opts.git and opts.nobinary and not opts.index)
        ):
            # fast path: no binary content will be displayed, content1 and
            # content2 are only used for equivalent test. cmp() could have a
            # fast path.
            if fctx1 is not None:
                content1 = b'\0'
            if fctx2 is not None:
                if fctx1 is not None and not fctx1.cmp(fctx2):
                    content2 = b'\0'  # not different
                else:
                    content2 = b'\0\0'
        else:
            # normal path: load contents
            if fctx1 is not None:
                content1 = fctx1.data()
            if fctx2 is not None:
                content2 = fctx2.data()

        data1 = (ctx1, fctx1, path1, flag1, content1, date1)
        data2 = (ctx2, fctx2, path2, flag2, content2, date2)
        yield diffcontent(data1, data2, header, binary, opts)


def diffcontent(data1, data2, header, binary, opts):
    """diffs two versions of a file.

    data1 and data2 are tuples containg:

        * ctx: changeset for the file
        * fctx: file context for that file
        * path1: name of the file
        * flag: flags of the file
        * content: full content of the file (can be null in case of binary)
        * date: date of the changeset

    header: the patch header
    binary: whether the any of the version of file is binary or not
    opts:   user passed options

    It exists as a separate function so that extensions like extdiff can wrap
    it and use the file content directly.
    """

    ctx1, fctx1, path1, flag1, content1, date1 = data1
    ctx2, fctx2, path2, flag2, content2, date2 = data2
    index1 = _gitindex(content1) if path1 in ctx1 else sha1nodeconstants.nullhex
    index2 = _gitindex(content2) if path2 in ctx2 else sha1nodeconstants.nullhex
    if binary and opts.git and not opts.nobinary:
        text = mdiff.b85diff(content1, content2)
        if text:
            header.append(b'index %s..%s' % (index1, index2))
        hunks = ((None, [text]),)
    else:
        if opts.git and opts.index > 0:
            flag = flag1
            if flag is None:
                flag = flag2
            header.append(
                b'index %s..%s %s'
                % (
                    index1[0 : opts.index],
                    index2[0 : opts.index],
                    _gitmode[flag],
                )
            )

        uheaders, hunks = mdiff.unidiff(
            content1,
            date1,
            content2,
            date2,
            path1,
            path2,
            binary=binary,
            opts=opts,
        )
        header.extend(uheaders)
    return fctx1, fctx2, header, hunks


def diffstatsum(stats):
    maxfile, maxtotal, addtotal, removetotal, binary = 0, 0, 0, 0, False
    for f, a, r, b in stats:
        maxfile = max(maxfile, encoding.colwidth(f))
        maxtotal = max(maxtotal, a + r)
        addtotal += a
        removetotal += r
        binary = binary or b

    return maxfile, maxtotal, addtotal, removetotal, binary


def diffstatdata(lines):
    diffre = re.compile(br'^diff .*-r [a-z0-9]+\s(.*)$')

    results = []
    filename, adds, removes, isbinary = None, 0, 0, False

    def addresult():
        if filename:
            results.append((filename, adds, removes, isbinary))

    # inheader is used to track if a line is in the
    # header portion of the diff.  This helps properly account
    # for lines that start with '--' or '++'
    inheader = False

    for line in lines:
        if line.startswith(b'diff'):
            addresult()
            # starting a new file diff
            # set numbers to 0 and reset inheader
            inheader = True
            adds, removes, isbinary = 0, 0, False
            if line.startswith(b'diff --git a/'):
                filename = gitre.search(line).group(2)
            elif line.startswith(b'diff -r'):
                # format: "diff -r ... -r ... filename"
                filename = diffre.search(line).group(1)
        elif line.startswith(b'@@'):
            inheader = False
        elif line.startswith(b'+') and not inheader:
            adds += 1
        elif line.startswith(b'-') and not inheader:
            removes += 1
        elif line.startswith(b'GIT binary patch') or line.startswith(
            b'Binary file'
        ):
            isbinary = True
        elif line.startswith(b'rename from'):
            filename = line[12:]
        elif line.startswith(b'rename to'):
            filename += b' => %s' % line[10:]
    addresult()
    return results


def diffstat(lines, width=80):
    output = []
    stats = diffstatdata(lines)
    maxname, maxtotal, totaladds, totalremoves, hasbinary = diffstatsum(stats)

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
        if isbinary:
            count = b'Bin'
        else:
            count = b'%d' % (adds + removes)
        pluses = b'+' * scale(adds)
        minuses = b'-' * scale(removes)
        output.append(
            b' %s%s |  %*s %s%s\n'
            % (
                filename,
                b' ' * (maxname - encoding.colwidth(filename)),
                countwidth,
                count,
                pluses,
                minuses,
            )
        )

    if stats:
        output.append(
            _(b' %d files changed, %d insertions(+), %d deletions(-)\n')
            % (len(stats), totaladds, totalremoves)
        )

    return b''.join(output)


def diffstatui(*args, **kw):
    """like diffstat(), but yields 2-tuples of (output, label) for
    ui.write()
    """

    for line in diffstat(*args, **kw).splitlines():
        if line and line[-1] in b'+-':
            name, graph = line.rsplit(b' ', 1)
            yield (name + b' ', b'')
            m = re.search(br'\++', graph)
            if m:
                yield (m.group(0), b'diffstat.inserted')
            m = re.search(br'-+', graph)
            if m:
                yield (m.group(0), b'diffstat.deleted')
        else:
            yield (line, b'')
        yield (b'\n', b'')
