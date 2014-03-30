# changelog.py - changelog class for mercurial
#
# Copyright 2005-2007 Matt Mackall <mpm@selenic.com>
#
# This software may be used and distributed according to the terms of the
# GNU General Public License version 2 or any later version.

from node import bin, hex, nullid
from i18n import _
import util, error, revlog, encoding

_defaultextra = {'branch': 'default'}

def _string_escape(text):
    """
    >>> d = {'nl': chr(10), 'bs': chr(92), 'cr': chr(13), 'nul': chr(0)}
    >>> s = "ab%(nl)scd%(bs)s%(bs)sn%(nul)sab%(cr)scd%(bs)s%(nl)s" % d
    >>> s
    'ab\\ncd\\\\\\\\n\\x00ab\\rcd\\\\\\n'
    >>> res = _string_escape(s)
    >>> s == res.decode('string_escape')
    True
    """
    # subset of the string_escape codec
    text = text.replace('\\', '\\\\').replace('\n', '\\n').replace('\r', '\\r')
    return text.replace('\0', '\\0')

def decodeextra(text):
    """
    >>> sorted(decodeextra(encodeextra({'foo': 'bar', 'baz': chr(0) + '2'})
    ...                    ).iteritems())
    [('baz', '\\x002'), ('branch', 'default'), ('foo', 'bar')]
    >>> sorted(decodeextra(encodeextra({'foo': 'bar',
    ...                                 'baz': chr(92) + chr(0) + '2'})
    ...                    ).iteritems())
    [('baz', '\\\\\\x002'), ('branch', 'default'), ('foo', 'bar')]
    """
    extra = _defaultextra.copy()
    for l in text.split('\0'):
        if l:
            if '\\0' in l:
                # fix up \0 without getting into trouble with \\0
                l = l.replace('\\\\', '\\\\\n')
                l = l.replace('\\0', '\0')
                l = l.replace('\n', '')
            k, v = l.decode('string_escape').split(':', 1)
            extra[k] = v
    return extra

def encodeextra(d):
    # keys must be sorted to produce a deterministic changelog entry
    items = [_string_escape('%s:%s' % (k, d[k])) for k in sorted(d)]
    return "\0".join(items)

def stripdesc(desc):
    """strip trailing whitespace and leading and trailing empty lines"""
    return '\n'.join([l.rstrip() for l in desc.splitlines()]).strip('\n')

class appender(object):
    '''the changelog index must be updated last on disk, so we use this class
    to delay writes to it'''
    def __init__(self, fp, buf):
        self.data = buf
        self.fp = fp
        self.offset = fp.tell()
        self.size = util.fstat(fp).st_size

    def end(self):
        return self.size + len("".join(self.data))
    def tell(self):
        return self.offset
    def flush(self):
        pass
    def close(self):
        self.fp.close()

    def seek(self, offset, whence=0):
        '''virtual file offset spans real file and data'''
        if whence == 0:
            self.offset = offset
        elif whence == 1:
            self.offset += offset
        elif whence == 2:
            self.offset = self.end() + offset
        if self.offset < self.size:
            self.fp.seek(self.offset)

    def read(self, count=-1):
        '''only trick here is reads that span real file and data'''
        ret = ""
        if self.offset < self.size:
            s = self.fp.read(count)
            ret = s
            self.offset += len(s)
            if count > 0:
                count -= len(s)
        if count != 0:
            doff = self.offset - self.size
            self.data.insert(0, "".join(self.data))
            del self.data[1:]
            s = self.data[0][doff:doff + count]
            self.offset += len(s)
            ret += s
        return ret

    def write(self, s):
        self.data.append(str(s))
        self.offset += len(s)

def delayopener(opener, target, divert, buf):
    def o(name, mode='r'):
        if name != target:
            return opener(name, mode)
        if divert:
            return opener(name + ".a", mode.replace('a', 'w'))
        # otherwise, divert to memory
        return appender(opener(name, mode), buf)
    return o

class changelog(revlog.revlog):
    def __init__(self, opener):
        revlog.revlog.__init__(self, opener, "00changelog.i")
        if self._initempty:
            # changelogs don't benefit from generaldelta
            self.version &= ~revlog.REVLOGGENERALDELTA
            self._generaldelta = False
        self._realopener = opener
        self._delayed = False
        self._divert = False
        self.filteredrevs = frozenset()

    def tip(self):
        """filtered version of revlog.tip"""
        for i in xrange(len(self) -1, -2, -1):
            if i not in self.filteredrevs:
                return self.node(i)

    def __iter__(self):
        """filtered version of revlog.__iter__"""
        if len(self.filteredrevs) == 0:
            return revlog.revlog.__iter__(self)

        def filterediter():
            for i in xrange(len(self)):
                if i not in self.filteredrevs:
                    yield i

        return filterediter()

    def revs(self, start=0, stop=None):
        """filtered version of revlog.revs"""
        for i in super(changelog, self).revs(start, stop):
            if i not in self.filteredrevs:
                yield i

    @util.propertycache
    def nodemap(self):
        # XXX need filtering too
        self.rev(self.node(0))
        return self._nodecache

    def hasnode(self, node):
        """filtered version of revlog.hasnode"""
        try:
            i = self.rev(node)
            return i not in self.filteredrevs
        except KeyError:
            return False

    def headrevs(self):
        if self.filteredrevs:
            # XXX we should fix and use the C version
            return self._headrevs()
        return super(changelog, self).headrevs()

    def strip(self, *args, **kwargs):
        # XXX make something better than assert
        # We can't expect proper strip behavior if we are filtered.
        assert not self.filteredrevs
        super(changelog, self).strip(*args, **kwargs)

    def rev(self, node):
        """filtered version of revlog.rev"""
        r = super(changelog, self).rev(node)
        if r in self.filteredrevs:
            raise error.LookupError(hex(node), self.indexfile, _('no node'))
        return r

    def node(self, rev):
        """filtered version of revlog.node"""
        if rev in self.filteredrevs:
            raise IndexError(rev)
        return super(changelog, self).node(rev)

    def linkrev(self, rev):
        """filtered version of revlog.linkrev"""
        if rev in self.filteredrevs:
            raise IndexError(rev)
        return super(changelog, self).linkrev(rev)

    def parentrevs(self, rev):
        """filtered version of revlog.parentrevs"""
        if rev in self.filteredrevs:
            raise IndexError(rev)
        return super(changelog, self).parentrevs(rev)

    def flags(self, rev):
        """filtered version of revlog.flags"""
        if rev in self.filteredrevs:
            raise IndexError(rev)
        return super(changelog, self).flags(rev)

    def delayupdate(self):
        "delay visibility of index updates to other readers"
        self._delayed = True
        self._divert = (len(self) == 0)
        self._delaybuf = []
        self.opener = delayopener(self._realopener, self.indexfile,
                                  self._divert, self._delaybuf)

    def finalize(self, tr):
        "finalize index updates"
        self._delayed = False
        self.opener = self._realopener
        # move redirected index data back into place
        if self._divert:
            nfile = self.opener(self.indexfile + ".a")
            n = nfile.name
            nfile.close()
            util.rename(n, n[:-2])
        elif self._delaybuf:
            fp = self.opener(self.indexfile, 'a')
            fp.write("".join(self._delaybuf))
            fp.close()
            self._delaybuf = []
        # split when we're done
        self.checkinlinesize(tr)

    def readpending(self, file):
        r = revlog.revlog(self.opener, file)
        self.index = r.index
        self.nodemap = r.nodemap
        self._nodecache = r._nodecache
        self._chunkcache = r._chunkcache

    def writepending(self):
        "create a file containing the unfinalized state for pretxnchangegroup"
        if self._delaybuf:
            # make a temporary copy of the index
            fp1 = self._realopener(self.indexfile)
            fp2 = self._realopener(self.indexfile + ".a", "w")
            fp2.write(fp1.read())
            # add pending data
            fp2.write("".join(self._delaybuf))
            fp2.close()
            # switch modes so finalize can simply rename
            self._delaybuf = []
            self._divert = True

        if self._divert:
            return True

        return False

    def checkinlinesize(self, tr, fp=None):
        if not self._delayed:
            revlog.revlog.checkinlinesize(self, tr, fp)

    def read(self, node):
        """
        format used:
        nodeid\n        : manifest node in ascii
        user\n          : user, no \n or \r allowed
        time tz extra\n : date (time is int or float, timezone is int)
                        : extra is metadata, encoded and separated by '\0'
                        : older versions ignore it
        files\n\n       : files modified by the cset, no \n or \r allowed
        (.*)            : comment (free text, ideally utf-8)

        changelog v0 doesn't use extra
        """
        text = self.revision(node)
        if not text:
            return (nullid, "", (0, 0), [], "", _defaultextra)
        last = text.index("\n\n")
        desc = encoding.tolocal(text[last + 2:])
        l = text[:last].split('\n')
        manifest = bin(l[0])
        user = encoding.tolocal(l[1])

        tdata = l[2].split(' ', 2)
        if len(tdata) != 3:
            time = float(tdata[0])
            try:
                # various tools did silly things with the time zone field.
                timezone = int(tdata[1])
            except ValueError:
                timezone = 0
            extra = _defaultextra
        else:
            time, timezone = float(tdata[0]), int(tdata[1])
            extra = decodeextra(tdata[2])

        files = l[3:]
        return (manifest, user, (time, timezone), files, desc, extra)

    def add(self, manifest, files, desc, transaction, p1, p2,
                  user, date=None, extra=None):
        # Convert to UTF-8 encoded bytestrings as the very first
        # thing: calling any method on a localstr object will turn it
        # into a str object and the cached UTF-8 string is thus lost.
        user, desc = encoding.fromlocal(user), encoding.fromlocal(desc)

        user = user.strip()
        # An empty username or a username with a "\n" will make the
        # revision text contain two "\n\n" sequences -> corrupt
        # repository since read cannot unpack the revision.
        if not user:
            raise error.RevlogError(_("empty username"))
        if "\n" in user:
            raise error.RevlogError(_("username %s contains a newline")
                                    % repr(user))

        desc = stripdesc(desc)

        if date:
            parseddate = "%d %d" % util.parsedate(date)
        else:
            parseddate = "%d %d" % util.makedate()
        if extra:
            branch = extra.get("branch")
            if branch in ("default", ""):
                del extra["branch"]
            elif branch in (".", "null", "tip"):
                raise error.RevlogError(_('the name \'%s\' is reserved')
                                        % branch)
        if extra:
            extra = encodeextra(extra)
            parseddate = "%s %s" % (parseddate, extra)
        l = [hex(manifest), user, parseddate] + sorted(files) + ["", desc]
        text = "\n".join(l)
        return self.addrevision(text, transaction, len(self), p1, p2)

    def branch(self, rev):
        """return the branch of a revision

        This function exists because creating a changectx object
        just to access this is costly."""
        return encoding.tolocal(self.read(rev)[5].get("branch"))
