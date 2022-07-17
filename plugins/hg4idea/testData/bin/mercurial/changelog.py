# changelog.py - changelog class for mercurial
#
# Copyright 2005-2007 Olivia Mackall <olivia@selenic.com>
#
# This software may be used and distributed according to the terms of the
# GNU General Public License version 2 or any later version.

from __future__ import absolute_import

from .i18n import _
from .node import (
    bin,
    hex,
)
from .thirdparty import attr

from . import (
    encoding,
    error,
    metadata,
    pycompat,
    revlog,
)
from .utils import (
    dateutil,
    stringutil,
)
from .revlogutils import (
    constants as revlog_constants,
    flagutil,
)

_defaultextra = {b'branch': b'default'}


def _string_escape(text):
    """
    >>> from .pycompat import bytechr as chr
    >>> d = {b'nl': chr(10), b'bs': chr(92), b'cr': chr(13), b'nul': chr(0)}
    >>> s = b"ab%(nl)scd%(bs)s%(bs)sn%(nul)s12ab%(cr)scd%(bs)s%(nl)s" % d
    >>> s
    'ab\\ncd\\\\\\\\n\\x0012ab\\rcd\\\\\\n'
    >>> res = _string_escape(s)
    >>> s == _string_unescape(res)
    True
    """
    # subset of the string_escape codec
    text = (
        text.replace(b'\\', b'\\\\')
        .replace(b'\n', b'\\n')
        .replace(b'\r', b'\\r')
    )
    return text.replace(b'\0', b'\\0')


def _string_unescape(text):
    if b'\\0' in text:
        # fix up \0 without getting into trouble with \\0
        text = text.replace(b'\\\\', b'\\\\\n')
        text = text.replace(b'\\0', b'\0')
        text = text.replace(b'\n', b'')
    return stringutil.unescapestr(text)


def decodeextra(text):
    """
    >>> from .pycompat import bytechr as chr
    >>> sorted(decodeextra(encodeextra({b'foo': b'bar', b'baz': chr(0) + b'2'})
    ...                    ).items())
    [('baz', '\\x002'), ('branch', 'default'), ('foo', 'bar')]
    >>> sorted(decodeextra(encodeextra({b'foo': b'bar',
    ...                                 b'baz': chr(92) + chr(0) + b'2'})
    ...                    ).items())
    [('baz', '\\\\\\x002'), ('branch', 'default'), ('foo', 'bar')]
    """
    extra = _defaultextra.copy()
    for l in text.split(b'\0'):
        if l:
            k, v = _string_unescape(l).split(b':', 1)
            extra[k] = v
    return extra


def encodeextra(d):
    # keys must be sorted to produce a deterministic changelog entry
    items = [_string_escape(b'%s:%s' % (k, d[k])) for k in sorted(d)]
    return b"\0".join(items)


def stripdesc(desc):
    """strip trailing whitespace and leading and trailing empty lines"""
    return b'\n'.join([l.rstrip() for l in desc.splitlines()]).strip(b'\n')


class appender(object):
    """the changelog index must be updated last on disk, so we use this class
    to delay writes to it"""

    def __init__(self, vfs, name, mode, buf):
        self.data = buf
        fp = vfs(name, mode)
        self.fp = fp
        self.offset = fp.tell()
        self.size = vfs.fstat(fp).st_size
        self._end = self.size

    def end(self):
        return self._end

    def tell(self):
        return self.offset

    def flush(self):
        pass

    @property
    def closed(self):
        return self.fp.closed

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
        ret = b""
        if self.offset < self.size:
            s = self.fp.read(count)
            ret = s
            self.offset += len(s)
            if count > 0:
                count -= len(s)
        if count != 0:
            doff = self.offset - self.size
            self.data.insert(0, b"".join(self.data))
            del self.data[1:]
            s = self.data[0][doff : doff + count]
            self.offset += len(s)
            ret += s
        return ret

    def write(self, s):
        self.data.append(bytes(s))
        self.offset += len(s)
        self._end += len(s)

    def __enter__(self):
        self.fp.__enter__()
        return self

    def __exit__(self, *args):
        return self.fp.__exit__(*args)


class _divertopener(object):
    def __init__(self, opener, target):
        self._opener = opener
        self._target = target

    def __call__(self, name, mode=b'r', checkambig=False, **kwargs):
        if name != self._target:
            return self._opener(name, mode, **kwargs)
        return self._opener(name + b".a", mode, **kwargs)

    def __getattr__(self, attr):
        return getattr(self._opener, attr)


def _delayopener(opener, target, buf):
    """build an opener that stores chunks in 'buf' instead of 'target'"""

    def _delay(name, mode=b'r', checkambig=False, **kwargs):
        if name != target:
            return opener(name, mode, **kwargs)
        assert not kwargs
        return appender(opener, name, mode, buf)

    return _delay


@attr.s
class _changelogrevision(object):
    # Extensions might modify _defaultextra, so let the constructor below pass
    # it in
    extra = attr.ib()
    manifest = attr.ib()
    user = attr.ib(default=b'')
    date = attr.ib(default=(0, 0))
    files = attr.ib(default=attr.Factory(list))
    filesadded = attr.ib(default=None)
    filesremoved = attr.ib(default=None)
    p1copies = attr.ib(default=None)
    p2copies = attr.ib(default=None)
    description = attr.ib(default=b'')
    branchinfo = attr.ib(default=(_defaultextra[b'branch'], False))


class changelogrevision(object):
    """Holds results of a parsed changelog revision.

    Changelog revisions consist of multiple pieces of data, including
    the manifest node, user, and date. This object exposes a view into
    the parsed object.
    """

    __slots__ = (
        '_offsets',
        '_text',
        '_sidedata',
        '_cpsd',
        '_changes',
    )

    def __new__(cls, cl, text, sidedata, cpsd):
        if not text:
            return _changelogrevision(extra=_defaultextra, manifest=cl.nullid)

        self = super(changelogrevision, cls).__new__(cls)
        # We could return here and implement the following as an __init__.
        # But doing it here is equivalent and saves an extra function call.

        # format used:
        # nodeid\n        : manifest node in ascii
        # user\n          : user, no \n or \r allowed
        # time tz extra\n : date (time is int or float, timezone is int)
        #                 : extra is metadata, encoded and separated by '\0'
        #                 : older versions ignore it
        # files\n\n       : files modified by the cset, no \n or \r allowed
        # (.*)            : comment (free text, ideally utf-8)
        #
        # changelog v0 doesn't use extra

        nl1 = text.index(b'\n')
        nl2 = text.index(b'\n', nl1 + 1)
        nl3 = text.index(b'\n', nl2 + 1)

        # The list of files may be empty. Which means nl3 is the first of the
        # double newline that precedes the description.
        if text[nl3 + 1 : nl3 + 2] == b'\n':
            doublenl = nl3
        else:
            doublenl = text.index(b'\n\n', nl3 + 1)

        self._offsets = (nl1, nl2, nl3, doublenl)
        self._text = text
        self._sidedata = sidedata
        self._cpsd = cpsd
        self._changes = None

        return self

    @property
    def manifest(self):
        return bin(self._text[0 : self._offsets[0]])

    @property
    def user(self):
        off = self._offsets
        return encoding.tolocal(self._text[off[0] + 1 : off[1]])

    @property
    def _rawdate(self):
        off = self._offsets
        dateextra = self._text[off[1] + 1 : off[2]]
        return dateextra.split(b' ', 2)[0:2]

    @property
    def _rawextra(self):
        off = self._offsets
        dateextra = self._text[off[1] + 1 : off[2]]
        fields = dateextra.split(b' ', 2)
        if len(fields) != 3:
            return None

        return fields[2]

    @property
    def date(self):
        raw = self._rawdate
        time = float(raw[0])
        # Various tools did silly things with the timezone.
        try:
            timezone = int(raw[1])
        except ValueError:
            timezone = 0

        return time, timezone

    @property
    def extra(self):
        raw = self._rawextra
        if raw is None:
            return _defaultextra

        return decodeextra(raw)

    @property
    def changes(self):
        if self._changes is not None:
            return self._changes
        if self._cpsd:
            changes = metadata.decode_files_sidedata(self._sidedata)
        else:
            changes = metadata.ChangingFiles(
                touched=self.files or (),
                added=self.filesadded or (),
                removed=self.filesremoved or (),
                p1_copies=self.p1copies or {},
                p2_copies=self.p2copies or {},
            )
        self._changes = changes
        return changes

    @property
    def files(self):
        if self._cpsd:
            return sorted(self.changes.touched)
        off = self._offsets
        if off[2] == off[3]:
            return []

        return self._text[off[2] + 1 : off[3]].split(b'\n')

    @property
    def filesadded(self):
        if self._cpsd:
            return self.changes.added
        else:
            rawindices = self.extra.get(b'filesadded')
        if rawindices is None:
            return None
        return metadata.decodefileindices(self.files, rawindices)

    @property
    def filesremoved(self):
        if self._cpsd:
            return self.changes.removed
        else:
            rawindices = self.extra.get(b'filesremoved')
        if rawindices is None:
            return None
        return metadata.decodefileindices(self.files, rawindices)

    @property
    def p1copies(self):
        if self._cpsd:
            return self.changes.copied_from_p1
        else:
            rawcopies = self.extra.get(b'p1copies')
        if rawcopies is None:
            return None
        return metadata.decodecopies(self.files, rawcopies)

    @property
    def p2copies(self):
        if self._cpsd:
            return self.changes.copied_from_p2
        else:
            rawcopies = self.extra.get(b'p2copies')
        if rawcopies is None:
            return None
        return metadata.decodecopies(self.files, rawcopies)

    @property
    def description(self):
        return encoding.tolocal(self._text[self._offsets[3] + 2 :])

    @property
    def branchinfo(self):
        extra = self.extra
        return encoding.tolocal(extra.get(b"branch")), b'close' in extra


class changelog(revlog.revlog):
    def __init__(self, opener, trypending=False, concurrencychecker=None):
        """Load a changelog revlog using an opener.

        If ``trypending`` is true, we attempt to load the index from a
        ``00changelog.i.a`` file instead of the default ``00changelog.i``.
        The ``00changelog.i.a`` file contains index (and possibly inline
        revision) data for a transaction that hasn't been finalized yet.
        It exists in a separate file to facilitate readers (such as
        hooks processes) accessing data before a transaction is finalized.

        ``concurrencychecker`` will be passed to the revlog init function, see
        the documentation there.
        """
        revlog.revlog.__init__(
            self,
            opener,
            target=(revlog_constants.KIND_CHANGELOG, None),
            radix=b'00changelog',
            checkambig=True,
            mmaplargeindex=True,
            persistentnodemap=opener.options.get(b'persistent-nodemap', False),
            concurrencychecker=concurrencychecker,
            trypending=trypending,
        )

        if self._initempty and (self._format_version == revlog.REVLOGV1):
            # changelogs don't benefit from generaldelta.

            self._format_flags &= ~revlog.FLAG_GENERALDELTA
            self._generaldelta = False

        # Delta chains for changelogs tend to be very small because entries
        # tend to be small and don't delta well with each. So disable delta
        # chains.
        self._storedeltachains = False

        self._realopener = opener
        self._delayed = False
        self._delaybuf = None
        self._divert = False
        self._filteredrevs = frozenset()
        self._filteredrevs_hashcache = {}
        self._copiesstorage = opener.options.get(b'copies-storage')

    @property
    def filteredrevs(self):
        return self._filteredrevs

    @filteredrevs.setter
    def filteredrevs(self, val):
        # Ensure all updates go through this function
        assert isinstance(val, frozenset)
        self._filteredrevs = val
        self._filteredrevs_hashcache = {}

    def _write_docket(self, tr):
        if not self._delayed:
            super(changelog, self)._write_docket(tr)

    def delayupdate(self, tr):
        """delay visibility of index updates to other readers"""
        if self._docket is None and not self._delayed:
            if len(self) == 0:
                self._divert = True
                if self._realopener.exists(self._indexfile + b'.a'):
                    self._realopener.unlink(self._indexfile + b'.a')
                self.opener = _divertopener(self._realopener, self._indexfile)
            else:
                self._delaybuf = []
                self.opener = _delayopener(
                    self._realopener, self._indexfile, self._delaybuf
                )
            self._segmentfile.opener = self.opener
            self._segmentfile_sidedata.opener = self.opener
        self._delayed = True
        tr.addpending(b'cl-%i' % id(self), self._writepending)
        tr.addfinalize(b'cl-%i' % id(self), self._finalize)

    def _finalize(self, tr):
        """finalize index updates"""
        self._delayed = False
        self.opener = self._realopener
        self._segmentfile.opener = self.opener
        self._segmentfile_sidedata.opener = self.opener
        # move redirected index data back into place
        if self._docket is not None:
            self._write_docket(tr)
        elif self._divert:
            assert not self._delaybuf
            tmpname = self._indexfile + b".a"
            nfile = self.opener.open(tmpname)
            nfile.close()
            self.opener.rename(tmpname, self._indexfile, checkambig=True)
        elif self._delaybuf:
            fp = self.opener(self._indexfile, b'a', checkambig=True)
            fp.write(b"".join(self._delaybuf))
            fp.close()
            self._delaybuf = None
        self._divert = False
        # split when we're done
        self._enforceinlinesize(tr)

    def _writepending(self, tr):
        """create a file containing the unfinalized state for
        pretxnchangegroup"""
        if self._docket:
            return self._docket.write(tr, pending=True)
        if self._delaybuf:
            # make a temporary copy of the index
            fp1 = self._realopener(self._indexfile)
            pendingfilename = self._indexfile + b".a"
            # register as a temp file to ensure cleanup on failure
            tr.registertmp(pendingfilename)
            # write existing data
            fp2 = self._realopener(pendingfilename, b"w")
            fp2.write(fp1.read())
            # add pending data
            fp2.write(b"".join(self._delaybuf))
            fp2.close()
            # switch modes so finalize can simply rename
            self._delaybuf = None
            self._divert = True
            self.opener = _divertopener(self._realopener, self._indexfile)
            self._segmentfile.opener = self.opener
            self._segmentfile_sidedata.opener = self.opener

        if self._divert:
            return True

        return False

    def _enforceinlinesize(self, tr):
        if not self._delayed:
            revlog.revlog._enforceinlinesize(self, tr)

    def read(self, nodeorrev):
        """Obtain data from a parsed changelog revision.

        Returns a 6-tuple of:

           - manifest node in binary
           - author/user as a localstr
           - date as a 2-tuple of (time, timezone)
           - list of files
           - commit message as a localstr
           - dict of extra metadata

        Unless you need to access all fields, consider calling
        ``changelogrevision`` instead, as it is faster for partial object
        access.
        """
        d = self._revisiondata(nodeorrev)
        sidedata = self.sidedata(nodeorrev)
        copy_sd = self._copiesstorage == b'changeset-sidedata'
        c = changelogrevision(self, d, sidedata, copy_sd)
        return (c.manifest, c.user, c.date, c.files, c.description, c.extra)

    def changelogrevision(self, nodeorrev):
        """Obtain a ``changelogrevision`` for a node or revision."""
        text = self._revisiondata(nodeorrev)
        sidedata = self.sidedata(nodeorrev)
        return changelogrevision(
            self, text, sidedata, self._copiesstorage == b'changeset-sidedata'
        )

    def readfiles(self, nodeorrev):
        """
        short version of read that only returns the files modified by the cset
        """
        text = self.revision(nodeorrev)
        if not text:
            return []
        last = text.index(b"\n\n")
        l = text[:last].split(b'\n')
        return l[3:]

    def add(
        self,
        manifest,
        files,
        desc,
        transaction,
        p1,
        p2,
        user,
        date=None,
        extra=None,
    ):
        # Convert to UTF-8 encoded bytestrings as the very first
        # thing: calling any method on a localstr object will turn it
        # into a str object and the cached UTF-8 string is thus lost.
        user, desc = encoding.fromlocal(user), encoding.fromlocal(desc)

        user = user.strip()
        # An empty username or a username with a "\n" will make the
        # revision text contain two "\n\n" sequences -> corrupt
        # repository since read cannot unpack the revision.
        if not user:
            raise error.StorageError(_(b"empty username"))
        if b"\n" in user:
            raise error.StorageError(
                _(b"username %r contains a newline") % pycompat.bytestr(user)
            )

        desc = stripdesc(desc)

        if date:
            parseddate = b"%d %d" % dateutil.parsedate(date)
        else:
            parseddate = b"%d %d" % dateutil.makedate()
        if extra:
            branch = extra.get(b"branch")
            if branch in (b"default", b""):
                del extra[b"branch"]
            elif branch in (b".", b"null", b"tip"):
                raise error.StorageError(
                    _(b'the name \'%s\' is reserved') % branch
                )
        sortedfiles = sorted(files.touched)
        flags = 0
        sidedata = None
        if self._copiesstorage == b'changeset-sidedata':
            if files.has_copies_info:
                flags |= flagutil.REVIDX_HASCOPIESINFO
            sidedata = metadata.encode_files_sidedata(files)

        if extra:
            extra = encodeextra(extra)
            parseddate = b"%s %s" % (parseddate, extra)
        l = [hex(manifest), user, parseddate] + sortedfiles + [b"", desc]
        text = b"\n".join(l)
        rev = self.addrevision(
            text, transaction, len(self), p1, p2, sidedata=sidedata, flags=flags
        )
        return self.node(rev)

    def branchinfo(self, rev):
        """return the branch name and open/close state of a revision

        This function exists because creating a changectx object
        just to access this is costly."""
        return self.changelogrevision(rev).branchinfo

    def _nodeduplicatecallback(self, transaction, rev):
        # keep track of revisions that got "re-added", eg: unbunde of know rev.
        #
        # We track them in a list to preserve their order from the source bundle
        duplicates = transaction.changes.setdefault(b'revduplicates', [])
        duplicates.append(rev)
