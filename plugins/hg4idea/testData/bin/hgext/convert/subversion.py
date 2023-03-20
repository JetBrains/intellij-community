# Subversion 1.4/1.5 Python API backend
#
# Copyright(C) 2007 Daniel Holth et al
from __future__ import absolute_import

import codecs
import locale
import os
import re
import xml.dom.minidom

from mercurial.i18n import _
from mercurial.pycompat import open
from mercurial import (
    encoding,
    error,
    pycompat,
    util,
    vfs as vfsmod,
)
from mercurial.utils import (
    dateutil,
    procutil,
    stringutil,
)

from . import common

pickle = util.pickle
stringio = util.stringio
propertycache = util.propertycache
urlerr = util.urlerr
urlreq = util.urlreq

commandline = common.commandline
commit = common.commit
converter_sink = common.converter_sink
converter_source = common.converter_source
decodeargs = common.decodeargs
encodeargs = common.encodeargs
makedatetimestamp = common.makedatetimestamp
mapfile = common.mapfile
MissingTool = common.MissingTool
NoRepo = common.NoRepo

# Subversion stuff. Works best with very recent Python SVN bindings
# e.g. SVN 1.5 or backports. Thanks to the bzr folks for enhancing
# these bindings.

try:
    import svn
    import svn.client
    import svn.core
    import svn.ra
    import svn.delta
    from . import transport
    import warnings

    warnings.filterwarnings(
        'ignore', module='svn.core', category=DeprecationWarning
    )
    svn.core.SubversionException  # trigger import to catch error

except ImportError:
    svn = None


# In Subversion, paths and URLs are Unicode (encoded as UTF-8), which
# Subversion converts from / to native strings when interfacing with the OS.
# When passing paths and URLs to Subversion, we have to recode them such that
# it roundstrips with what Subversion is doing.

fsencoding = None


def init_fsencoding():
    global fsencoding, fsencoding_is_utf8
    if fsencoding is not None:
        return
    if pycompat.iswindows:
        # On Windows, filenames are Unicode, but we store them using the MBCS
        # encoding.
        fsencoding = 'mbcs'
    else:
        # This is the encoding used to convert UTF-8 back to natively-encoded
        # strings in Subversion 1.14.0 or earlier with APR 1.7.0 or earlier.
        with util.with_lc_ctype():
            fsencoding = locale.nl_langinfo(locale.CODESET) or 'ISO-8859-1'
    fsencoding = codecs.lookup(fsencoding).name
    fsencoding_is_utf8 = fsencoding == codecs.lookup('utf-8').name


def fs2svn(s):
    if fsencoding_is_utf8:
        return s
    else:
        return s.decode(fsencoding).encode('utf-8')


def formatsvndate(date):
    return dateutil.datestr(date, b'%Y-%m-%dT%H:%M:%S.000000Z')


def parsesvndate(s):
    # Example SVN datetime. Includes microseconds.
    # ISO-8601 conformant
    # '2007-01-04T17:35:00.902377Z'
    return dateutil.parsedate(s[:19] + b' UTC', [b'%Y-%m-%dT%H:%M:%S'])


class SvnPathNotFound(Exception):
    pass


def revsplit(rev):
    """Parse a revision string and return (uuid, path, revnum).
    >>> revsplit(b'svn:a2147622-4a9f-4db4-a8d3-13562ff547b2'
    ...          b'/proj%20B/mytrunk/mytrunk@1')
    ('a2147622-4a9f-4db4-a8d3-13562ff547b2', '/proj%20B/mytrunk/mytrunk', 1)
    >>> revsplit(b'svn:8af66a51-67f5-4354-b62c-98d67cc7be1d@1')
    ('', '', 1)
    >>> revsplit(b'@7')
    ('', '', 7)
    >>> revsplit(b'7')
    ('', '', 0)
    >>> revsplit(b'bad')
    ('', '', 0)
    """
    parts = rev.rsplit(b'@', 1)
    revnum = 0
    if len(parts) > 1:
        revnum = int(parts[1])
    parts = parts[0].split(b'/', 1)
    uuid = b''
    mod = b''
    if len(parts) > 1 and parts[0].startswith(b'svn:'):
        uuid = parts[0][4:]
        mod = b'/' + parts[1]
    return uuid, mod, revnum


def quote(s):
    # As of svn 1.7, many svn calls expect "canonical" paths. In
    # theory, we should call svn.core.*canonicalize() on all paths
    # before passing them to the API.  Instead, we assume the base url
    # is canonical and copy the behaviour of svn URL encoding function
    # so we can extend it safely with new components. The "safe"
    # characters were taken from the "svn_uri__char_validity" table in
    # libsvn_subr/path.c.
    return urlreq.quote(s, b"!$&'()*+,-./:=@_~")


def geturl(path):
    """Convert path or URL to a SVN URL, encoded in UTF-8.

    This can raise UnicodeDecodeError if the path or URL can't be converted to
    unicode using `fsencoding`.
    """
    try:
        return svn.client.url_from_path(
            svn.core.svn_path_canonicalize(fs2svn(path))
        )
    except svn.core.SubversionException:
        # svn.client.url_from_path() fails with local repositories
        pass
    if os.path.isdir(path):
        path = os.path.normpath(util.abspath(path))
        if pycompat.iswindows:
            path = b'/' + util.normpath(path)
        # Module URL is later compared with the repository URL returned
        # by svn API, which is UTF-8.
        path = fs2svn(path)
        path = b'file://%s' % quote(path)
    return svn.core.svn_path_canonicalize(path)


def optrev(number):
    optrev = svn.core.svn_opt_revision_t()
    optrev.kind = svn.core.svn_opt_revision_number
    optrev.value.number = number
    return optrev


class changedpath(object):
    def __init__(self, p):
        self.copyfrom_path = p.copyfrom_path
        self.copyfrom_rev = p.copyfrom_rev
        self.action = p.action


def get_log_child(
    fp,
    url,
    paths,
    start,
    end,
    limit=0,
    discover_changed_paths=True,
    strict_node_history=False,
):
    protocol = -1

    def receiver(orig_paths, revnum, author, date, message, pool):
        paths = {}
        if orig_paths is not None:
            for k, v in pycompat.iteritems(orig_paths):
                paths[k] = changedpath(v)
        pickle.dump((paths, revnum, author, date, message), fp, protocol)

    try:
        # Use an ra of our own so that our parent can consume
        # our results without confusing the server.
        t = transport.SvnRaTransport(url=url)
        svn.ra.get_log(
            t.ra,
            paths,
            start,
            end,
            limit,
            discover_changed_paths,
            strict_node_history,
            receiver,
        )
    except IOError:
        # Caller may interrupt the iteration
        pickle.dump(None, fp, protocol)
    except Exception as inst:
        pickle.dump(stringutil.forcebytestr(inst), fp, protocol)
    else:
        pickle.dump(None, fp, protocol)
    fp.flush()
    # With large history, cleanup process goes crazy and suddenly
    # consumes *huge* amount of memory. The output file being closed,
    # there is no need for clean termination.
    os._exit(0)


def debugsvnlog(ui, **opts):
    """Fetch SVN log in a subprocess and channel them back to parent to
    avoid memory collection issues.
    """
    with util.with_lc_ctype():
        if svn is None:
            raise error.Abort(
                _(b'debugsvnlog could not load Subversion python bindings')
            )

        args = decodeargs(ui.fin.read())
        get_log_child(ui.fout, *args)


class logstream(object):
    """Interruptible revision log iterator."""

    def __init__(self, stdout):
        self._stdout = stdout

    def __iter__(self):
        while True:
            try:
                entry = pickle.load(self._stdout)
            except EOFError:
                raise error.Abort(
                    _(
                        b'Mercurial failed to run itself, check'
                        b' hg executable is in PATH'
                    )
                )
            try:
                orig_paths, revnum, author, date, message = entry
            except (TypeError, ValueError):
                if entry is None:
                    break
                raise error.Abort(_(b"log stream exception '%s'") % entry)
            yield entry

    def close(self):
        if self._stdout:
            self._stdout.close()
            self._stdout = None


class directlogstream(list):
    """Direct revision log iterator.
    This can be used for debugging and development but it will probably leak
    memory and is not suitable for real conversions."""

    def __init__(
        self,
        url,
        paths,
        start,
        end,
        limit=0,
        discover_changed_paths=True,
        strict_node_history=False,
    ):
        def receiver(orig_paths, revnum, author, date, message, pool):
            paths = {}
            if orig_paths is not None:
                for k, v in pycompat.iteritems(orig_paths):
                    paths[k] = changedpath(v)
            self.append((paths, revnum, author, date, message))

        # Use an ra of our own so that our parent can consume
        # our results without confusing the server.
        t = transport.SvnRaTransport(url=url)
        svn.ra.get_log(
            t.ra,
            paths,
            start,
            end,
            limit,
            discover_changed_paths,
            strict_node_history,
            receiver,
        )

    def close(self):
        pass


# Check to see if the given path is a local Subversion repo. Verify this by
# looking for several svn-specific files and directories in the given
# directory.
def filecheck(ui, path, proto):
    for x in (b'locks', b'hooks', b'format', b'db'):
        if not os.path.exists(os.path.join(path, x)):
            return False
    return True


# Check to see if a given path is the root of an svn repo over http. We verify
# this by requesting a version-controlled URL we know can't exist and looking
# for the svn-specific "not found" XML.
def httpcheck(ui, path, proto):
    try:
        opener = urlreq.buildopener()
        rsp = opener.open(
            pycompat.strurl(b'%s://%s/!svn/ver/0/.svn' % (proto, path)), b'rb'
        )
        data = rsp.read()
    except urlerr.httperror as inst:
        if inst.code != 404:
            # Except for 404 we cannot know for sure this is not an svn repo
            ui.warn(
                _(
                    b'svn: cannot probe remote repository, assume it could '
                    b'be a subversion repository. Use --source-type if you '
                    b'know better.\n'
                )
            )
            return True
        data = inst.fp.read()
    except Exception:
        # Could be urlerr.urlerror if the URL is invalid or anything else.
        return False
    return b'<m:human-readable errcode="160013">' in data


protomap = {
    b'http': httpcheck,
    b'https': httpcheck,
    b'file': filecheck,
}


class NonUtf8PercentEncodedBytes(Exception):
    pass


# Subversion paths are Unicode. Since the percent-decoding is done on
# UTF-8-encoded strings, percent-encoded bytes are interpreted as UTF-8.
def url2pathname_like_subversion(unicodepath):
    if pycompat.ispy3:
        # On Python 3, we have to pass unicode to urlreq.url2pathname().
        # Percent-decoded bytes get decoded using UTF-8 and the 'replace' error
        # handler.
        unicodepath = urlreq.url2pathname(unicodepath)
        if u'\N{REPLACEMENT CHARACTER}' in unicodepath:
            raise NonUtf8PercentEncodedBytes
        else:
            return unicodepath
    else:
        # If we passed unicode on Python 2, it would be converted using the
        # latin-1 encoding. Therefore, we pass UTF-8-encoded bytes.
        unicodepath = urlreq.url2pathname(unicodepath.encode('utf-8'))
        try:
            return unicodepath.decode('utf-8')
        except UnicodeDecodeError:
            raise NonUtf8PercentEncodedBytes


def issvnurl(ui, url):
    try:
        proto, path = url.split(b'://', 1)
        if proto == b'file':
            if (
                pycompat.iswindows
                and path[:1] == b'/'
                and path[1:2].isalpha()
                and path[2:6].lower() == b'%3a/'
            ):
                path = path[:2] + b':/' + path[6:]
            try:
                unicodepath = path.decode(fsencoding)
            except UnicodeDecodeError:
                ui.warn(
                    _(
                        b'Subversion requires that file URLs can be converted '
                        b'to Unicode using the current locale encoding (%s)\n'
                    )
                    % pycompat.sysbytes(fsencoding)
                )
                return False
            try:
                unicodepath = url2pathname_like_subversion(unicodepath)
            except NonUtf8PercentEncodedBytes:
                ui.warn(
                    _(
                        b'Subversion does not support non-UTF-8 '
                        b'percent-encoded bytes in file URLs\n'
                    )
                )
                return False
            # Below, we approximate how Subversion checks the path. On Unix, we
            # should therefore convert the path to bytes using `fsencoding`
            # (like Subversion does). On Windows, the right thing would
            # actually be to leave the path as unicode. For now, we restrict
            # the path to MBCS.
            path = unicodepath.encode(fsencoding)
    except ValueError:
        proto = b'file'
        path = util.abspath(url)
        try:
            path.decode(fsencoding)
        except UnicodeDecodeError:
            ui.warn(
                _(
                    b'Subversion requires that paths can be converted to '
                    b'Unicode using the current locale encoding (%s)\n'
                )
                % pycompat.sysbytes(fsencoding)
            )
            return False
    if proto == b'file':
        path = util.pconvert(path)
    elif proto in (b'http', 'https'):
        if not encoding.isasciistr(path):
            ui.warn(
                _(
                    b"Subversion sources don't support non-ASCII characters in "
                    b"HTTP(S) URLs. Please percent-encode them.\n"
                )
            )
            return False
    check = protomap.get(proto, lambda *args: False)
    while b'/' in path:
        if check(ui, path, proto):
            return True
        path = path.rsplit(b'/', 1)[0]
    return False


# SVN conversion code stolen from bzr-svn and tailor
#
# Subversion looks like a versioned filesystem, branches structures
# are defined by conventions and not enforced by the tool. First,
# we define the potential branches (modules) as "trunk" and "branches"
# children directories. Revisions are then identified by their
# module and revision number (and a repository identifier).
#
# The revision graph is really a tree (or a forest). By default, a
# revision parent is the previous revision in the same module. If the
# module directory is copied/moved from another module then the
# revision is the module root and its parent the source revision in
# the parent module. A revision has at most one parent.
#
class svn_source(converter_source):
    def __init__(self, ui, repotype, url, revs=None):
        super(svn_source, self).__init__(ui, repotype, url, revs=revs)

        init_fsencoding()
        if not (
            url.startswith(b'svn://')
            or url.startswith(b'svn+ssh://')
            or (
                os.path.exists(url)
                and os.path.exists(os.path.join(url, b'.svn'))
            )
            or issvnurl(ui, url)
        ):
            raise NoRepo(
                _(b"%s does not look like a Subversion repository") % url
            )
        if svn is None:
            raise MissingTool(_(b'could not load Subversion python bindings'))

        try:
            version = svn.core.SVN_VER_MAJOR, svn.core.SVN_VER_MINOR
            if version < (1, 4):
                raise MissingTool(
                    _(
                        b'Subversion python bindings %d.%d found, '
                        b'1.4 or later required'
                    )
                    % version
                )
        except AttributeError:
            raise MissingTool(
                _(
                    b'Subversion python bindings are too old, 1.4 '
                    b'or later required'
                )
            )

        self.lastrevs = {}

        latest = None
        try:
            # Support file://path@rev syntax. Useful e.g. to convert
            # deleted branches.
            at = url.rfind(b'@')
            if at >= 0:
                latest = int(url[at + 1 :])
                url = url[:at]
        except ValueError:
            pass
        self.url = geturl(url)
        self.encoding = b'UTF-8'  # Subversion is always nominal UTF-8
        try:
            with util.with_lc_ctype():
                self.transport = transport.SvnRaTransport(url=self.url)
                self.ra = self.transport.ra
                self.ctx = self.transport.client
                self.baseurl = svn.ra.get_repos_root(self.ra)
                # Module is either empty or a repository path starting with
                # a slash and not ending with a slash.
                self.module = urlreq.unquote(self.url[len(self.baseurl) :])
                self.prevmodule = None
                self.rootmodule = self.module
                self.commits = {}
                self.paths = {}
                self.uuid = svn.ra.get_uuid(self.ra)
        except svn.core.SubversionException:
            ui.traceback()
            svnversion = b'%d.%d.%d' % (
                svn.core.SVN_VER_MAJOR,
                svn.core.SVN_VER_MINOR,
                svn.core.SVN_VER_MICRO,
            )
            raise NoRepo(
                _(
                    b"%s does not look like a Subversion repository "
                    b"to libsvn version %s"
                )
                % (self.url, svnversion)
            )

        if revs:
            if len(revs) > 1:
                raise error.Abort(
                    _(
                        b'subversion source does not support '
                        b'specifying multiple revisions'
                    )
                )
            try:
                latest = int(revs[0])
            except ValueError:
                raise error.Abort(
                    _(b'svn: revision %s is not an integer') % revs[0]
                )

        trunkcfg = self.ui.config(b'convert', b'svn.trunk')
        if trunkcfg is None:
            trunkcfg = b'trunk'
        self.trunkname = trunkcfg.strip(b'/')
        self.startrev = self.ui.config(b'convert', b'svn.startrev')
        try:
            self.startrev = int(self.startrev)
            if self.startrev < 0:
                self.startrev = 0
        except ValueError:
            raise error.Abort(
                _(b'svn: start revision %s is not an integer') % self.startrev
            )

        try:
            with util.with_lc_ctype():
                self.head = self.latest(self.module, latest)
        except SvnPathNotFound:
            self.head = None
        if not self.head:
            raise error.Abort(
                _(b'no revision found in module %s') % self.module
            )
        self.last_changed = self.revnum(self.head)

        self._changescache = (None, None)

        if os.path.exists(os.path.join(url, b'.svn/entries')):
            self.wc = url
        else:
            self.wc = None
        self.convertfp = None

    def before(self):
        self.with_lc_ctype = util.with_lc_ctype()
        self.with_lc_ctype.__enter__()

    def after(self):
        self.with_lc_ctype.__exit__(None, None, None)

    def setrevmap(self, revmap):
        lastrevs = {}
        for revid in revmap:
            uuid, module, revnum = revsplit(revid)
            lastrevnum = lastrevs.setdefault(module, revnum)
            if revnum > lastrevnum:
                lastrevs[module] = revnum
        self.lastrevs = lastrevs

    def exists(self, path, optrev):
        try:
            svn.client.ls(
                self.url.rstrip(b'/') + b'/' + quote(path),
                optrev,
                False,
                self.ctx,
            )
            return True
        except svn.core.SubversionException:
            return False

    def getheads(self):
        def isdir(path, revnum):
            kind = self._checkpath(path, revnum)
            return kind == svn.core.svn_node_dir

        def getcfgpath(name, rev):
            cfgpath = self.ui.config(b'convert', b'svn.' + name)
            if cfgpath is not None and cfgpath.strip() == b'':
                return None
            path = (cfgpath or name).strip(b'/')
            if not self.exists(path, rev):
                if self.module.endswith(path) and name == b'trunk':
                    # we are converting from inside this directory
                    return None
                if cfgpath:
                    raise error.Abort(
                        _(b'expected %s to be at %r, but not found')
                        % (name, path)
                    )
                return None
            self.ui.note(
                _(b'found %s at %r\n') % (name, pycompat.bytestr(path))
            )
            return path

        rev = optrev(self.last_changed)
        oldmodule = b''
        trunk = getcfgpath(b'trunk', rev)
        self.tags = getcfgpath(b'tags', rev)
        branches = getcfgpath(b'branches', rev)

        # If the project has a trunk or branches, we will extract heads
        # from them. We keep the project root otherwise.
        if trunk:
            oldmodule = self.module or b''
            self.module += b'/' + trunk
            self.head = self.latest(self.module, self.last_changed)
            if not self.head:
                raise error.Abort(
                    _(b'no revision found in module %s') % self.module
                )

        # First head in the list is the module's head
        self.heads = [self.head]
        if self.tags is not None:
            self.tags = b'%s/%s' % (oldmodule, (self.tags or b'tags'))

        # Check if branches bring a few more heads to the list
        if branches:
            rpath = self.url.strip(b'/')
            branchnames = svn.client.ls(
                rpath + b'/' + quote(branches), rev, False, self.ctx
            )
            for branch in sorted(branchnames):
                module = b'%s/%s/%s' % (oldmodule, branches, branch)
                if not isdir(module, self.last_changed):
                    continue
                brevid = self.latest(module, self.last_changed)
                if not brevid:
                    self.ui.note(_(b'ignoring empty branch %s\n') % branch)
                    continue
                self.ui.note(
                    _(b'found branch %s at %d\n')
                    % (branch, self.revnum(brevid))
                )
                self.heads.append(brevid)

        if self.startrev and self.heads:
            if len(self.heads) > 1:
                raise error.Abort(
                    _(
                        b'svn: start revision is not supported '
                        b'with more than one branch'
                    )
                )
            revnum = self.revnum(self.heads[0])
            if revnum < self.startrev:
                raise error.Abort(
                    _(b'svn: no revision found after start revision %d')
                    % self.startrev
                )

        return self.heads

    def _getchanges(self, rev, full):
        (paths, parents) = self.paths[rev]
        copies = {}
        if parents:
            files, self.removed, copies = self.expandpaths(rev, paths, parents)
        if full or not parents:
            # Perform a full checkout on roots
            uuid, module, revnum = revsplit(rev)
            entries = svn.client.ls(
                self.baseurl + quote(module), optrev(revnum), True, self.ctx
            )
            files = [
                n
                for n, e in pycompat.iteritems(entries)
                if e.kind == svn.core.svn_node_file
            ]
            self.removed = set()

        files.sort()
        files = pycompat.ziplist(files, [rev] * len(files))
        return (files, copies)

    def getchanges(self, rev, full):
        # reuse cache from getchangedfiles
        if self._changescache[0] == rev and not full:
            (files, copies) = self._changescache[1]
        else:
            (files, copies) = self._getchanges(rev, full)
            # caller caches the result, so free it here to release memory
            del self.paths[rev]
        return (files, copies, set())

    def getchangedfiles(self, rev, i):
        # called from filemap - cache computed values for reuse in getchanges
        (files, copies) = self._getchanges(rev, False)
        self._changescache = (rev, (files, copies))
        return [f[0] for f in files]

    def getcommit(self, rev):
        if rev not in self.commits:
            uuid, module, revnum = revsplit(rev)
            self.module = module
            self.reparent(module)
            # We assume that:
            # - requests for revisions after "stop" come from the
            # revision graph backward traversal. Cache all of them
            # down to stop, they will be used eventually.
            # - requests for revisions before "stop" come to get
            # isolated branches parents. Just fetch what is needed.
            stop = self.lastrevs.get(module, 0)
            if revnum < stop:
                stop = revnum + 1
            self._fetch_revisions(revnum, stop)
            if rev not in self.commits:
                raise error.Abort(_(b'svn: revision %s not found') % revnum)
        revcommit = self.commits[rev]
        # caller caches the result, so free it here to release memory
        del self.commits[rev]
        return revcommit

    def checkrevformat(self, revstr, mapname=b'splicemap'):
        """fails if revision format does not match the correct format"""
        if not re.match(
            br'svn:[0-9a-f]{8,8}-[0-9a-f]{4,4}-'
            br'[0-9a-f]{4,4}-[0-9a-f]{4,4}-[0-9a-f]'
            br'{12,12}(.*)@[0-9]+$',
            revstr,
        ):
            raise error.Abort(
                _(b'%s entry %s is not a valid revision identifier')
                % (mapname, revstr)
            )

    def numcommits(self):
        return int(self.head.rsplit(b'@', 1)[1]) - self.startrev

    def gettags(self):
        tags = {}
        if self.tags is None:
            return tags

        # svn tags are just a convention, project branches left in a
        # 'tags' directory. There is no other relationship than
        # ancestry, which is expensive to discover and makes them hard
        # to update incrementally.  Worse, past revisions may be
        # referenced by tags far away in the future, requiring a deep
        # history traversal on every calculation.  Current code
        # performs a single backward traversal, tracking moves within
        # the tags directory (tag renaming) and recording a new tag
        # everytime a project is copied from outside the tags
        # directory. It also lists deleted tags, this behaviour may
        # change in the future.
        pendings = []
        tagspath = self.tags
        start = svn.ra.get_latest_revnum(self.ra)
        stream = self._getlog([self.tags], start, self.startrev)
        try:
            for entry in stream:
                origpaths, revnum, author, date, message = entry
                if not origpaths:
                    origpaths = []
                copies = [
                    (e.copyfrom_path, e.copyfrom_rev, p)
                    for p, e in pycompat.iteritems(origpaths)
                    if e.copyfrom_path
                ]
                # Apply moves/copies from more specific to general
                copies.sort(reverse=True)

                srctagspath = tagspath
                if copies and copies[-1][2] == tagspath:
                    # Track tags directory moves
                    srctagspath = copies.pop()[0]

                for source, sourcerev, dest in copies:
                    if not dest.startswith(tagspath + b'/'):
                        continue
                    for tag in pendings:
                        if tag[0].startswith(dest):
                            tagpath = source + tag[0][len(dest) :]
                            tag[:2] = [tagpath, sourcerev]
                            break
                    else:
                        pendings.append([source, sourcerev, dest])

                # Filter out tags with children coming from different
                # parts of the repository like:
                # /tags/tag.1 (from /trunk:10)
                # /tags/tag.1/foo (from /branches/foo:12)
                # Here/tags/tag.1 discarded as well as its children.
                # It happens with tools like cvs2svn. Such tags cannot
                # be represented in mercurial.
                addeds = {
                    p: e.copyfrom_path
                    for p, e in pycompat.iteritems(origpaths)
                    if e.action == b'A' and e.copyfrom_path
                }
                badroots = set()
                for destroot in addeds:
                    for source, sourcerev, dest in pendings:
                        if not dest.startswith(
                            destroot + b'/'
                        ) or source.startswith(addeds[destroot] + b'/'):
                            continue
                        badroots.add(destroot)
                        break

                for badroot in badroots:
                    pendings = [
                        p
                        for p in pendings
                        if p[2] != badroot
                        and not p[2].startswith(badroot + b'/')
                    ]

                # Tell tag renamings from tag creations
                renamings = []
                for source, sourcerev, dest in pendings:
                    tagname = dest.split(b'/')[-1]
                    if source.startswith(srctagspath):
                        renamings.append([source, sourcerev, tagname])
                        continue
                    if tagname in tags:
                        # Keep the latest tag value
                        continue
                    # From revision may be fake, get one with changes
                    try:
                        tagid = self.latest(source, sourcerev)
                        if tagid and tagname not in tags:
                            tags[tagname] = tagid
                    except SvnPathNotFound:
                        # It happens when we are following directories
                        # we assumed were copied with their parents
                        # but were really created in the tag
                        # directory.
                        pass
                pendings = renamings
                tagspath = srctagspath
        finally:
            stream.close()
        return tags

    def converted(self, rev, destrev):
        if not self.wc:
            return
        if self.convertfp is None:
            self.convertfp = open(
                os.path.join(self.wc, b'.svn', b'hg-shamap'), b'ab'
            )
        self.convertfp.write(
            util.tonativeeol(b'%s %d\n' % (destrev, self.revnum(rev)))
        )
        self.convertfp.flush()

    def revid(self, revnum, module=None):
        return b'svn:%s%s@%d' % (self.uuid, module or self.module, revnum)

    def revnum(self, rev):
        return int(rev.split(b'@')[-1])

    def latest(self, path, stop=None):
        """Find the latest revid affecting path, up to stop revision
        number. If stop is None, default to repository latest
        revision. It may return a revision in a different module,
        since a branch may be moved without a change being
        reported. Return None if computed module does not belong to
        rootmodule subtree.
        """

        def findchanges(path, start, stop=None):
            stream = self._getlog([path], start, stop or 1)
            try:
                for entry in stream:
                    paths, revnum, author, date, message = entry
                    if stop is None and paths:
                        # We do not know the latest changed revision,
                        # keep the first one with changed paths.
                        break
                    if stop is not None and revnum <= stop:
                        break

                    for p in paths:
                        if not path.startswith(p) or not paths[p].copyfrom_path:
                            continue
                        newpath = paths[p].copyfrom_path + path[len(p) :]
                        self.ui.debug(
                            b"branch renamed from %s to %s at %d\n"
                            % (path, newpath, revnum)
                        )
                        path = newpath
                        break
                if not paths:
                    revnum = None
                return revnum, path
            finally:
                stream.close()

        if not path.startswith(self.rootmodule):
            # Requests on foreign branches may be forbidden at server level
            self.ui.debug(b'ignoring foreign branch %r\n' % path)
            return None

        if stop is None:
            stop = svn.ra.get_latest_revnum(self.ra)
        try:
            prevmodule = self.reparent(b'')
            dirent = svn.ra.stat(self.ra, path.strip(b'/'), stop)
            self.reparent(prevmodule)
        except svn.core.SubversionException:
            dirent = None
        if not dirent:
            raise SvnPathNotFound(
                _(b'%s not found up to revision %d') % (path, stop)
            )

        # stat() gives us the previous revision on this line of
        # development, but it might be in *another module*. Fetch the
        # log and detect renames down to the latest revision.
        revnum, realpath = findchanges(path, stop, dirent.created_rev)
        if revnum is None:
            # Tools like svnsync can create empty revision, when
            # synchronizing only a subtree for instance. These empty
            # revisions created_rev still have their original values
            # despite all changes having disappeared and can be
            # returned by ra.stat(), at least when stating the root
            # module. In that case, do not trust created_rev and scan
            # the whole history.
            revnum, realpath = findchanges(path, stop)
            if revnum is None:
                self.ui.debug(b'ignoring empty branch %r\n' % realpath)
                return None

        if not realpath.startswith(self.rootmodule):
            self.ui.debug(b'ignoring foreign branch %r\n' % realpath)
            return None
        return self.revid(revnum, realpath)

    def reparent(self, module):
        """Reparent the svn transport and return the previous parent."""
        if self.prevmodule == module:
            return module
        svnurl = self.baseurl + quote(module)
        prevmodule = self.prevmodule
        if prevmodule is None:
            prevmodule = b''
        self.ui.debug(b"reparent to %s\n" % svnurl)
        svn.ra.reparent(self.ra, svnurl)
        self.prevmodule = module
        return prevmodule

    def expandpaths(self, rev, paths, parents):
        changed, removed = set(), set()
        copies = {}

        new_module, revnum = revsplit(rev)[1:]
        if new_module != self.module:
            self.module = new_module
            self.reparent(self.module)

        progress = self.ui.makeprogress(
            _(b'scanning paths'), unit=_(b'paths'), total=len(paths)
        )
        for i, (path, ent) in enumerate(paths):
            progress.update(i, item=path)
            entrypath = self.getrelpath(path)

            kind = self._checkpath(entrypath, revnum)
            if kind == svn.core.svn_node_file:
                changed.add(self.recode(entrypath))
                if not ent.copyfrom_path or not parents:
                    continue
                # Copy sources not in parent revisions cannot be
                # represented, ignore their origin for now
                pmodule, prevnum = revsplit(parents[0])[1:]
                if ent.copyfrom_rev < prevnum:
                    continue
                copyfrom_path = self.getrelpath(ent.copyfrom_path, pmodule)
                if not copyfrom_path:
                    continue
                self.ui.debug(
                    b"copied to %s from %s@%d\n"
                    % (entrypath, copyfrom_path, ent.copyfrom_rev)
                )
                copies[self.recode(entrypath)] = self.recode(copyfrom_path)
            elif kind == 0:  # gone, but had better be a deleted *file*
                self.ui.debug(b"gone from %d\n" % ent.copyfrom_rev)
                pmodule, prevnum = revsplit(parents[0])[1:]
                parentpath = pmodule + b"/" + entrypath
                fromkind = self._checkpath(entrypath, prevnum, pmodule)

                if fromkind == svn.core.svn_node_file:
                    removed.add(self.recode(entrypath))
                elif fromkind == svn.core.svn_node_dir:
                    oroot = parentpath.strip(b'/')
                    nroot = path.strip(b'/')
                    children = self._iterfiles(oroot, prevnum)
                    for childpath in children:
                        childpath = childpath.replace(oroot, nroot)
                        childpath = self.getrelpath(b"/" + childpath, pmodule)
                        if childpath:
                            removed.add(self.recode(childpath))
                else:
                    self.ui.debug(
                        b'unknown path in revision %d: %s\n' % (revnum, path)
                    )
            elif kind == svn.core.svn_node_dir:
                if ent.action == b'M':
                    # If the directory just had a prop change,
                    # then we shouldn't need to look for its children.
                    continue
                if ent.action == b'R' and parents:
                    # If a directory is replacing a file, mark the previous
                    # file as deleted
                    pmodule, prevnum = revsplit(parents[0])[1:]
                    pkind = self._checkpath(entrypath, prevnum, pmodule)
                    if pkind == svn.core.svn_node_file:
                        removed.add(self.recode(entrypath))
                    elif pkind == svn.core.svn_node_dir:
                        # We do not know what files were kept or removed,
                        # mark them all as changed.
                        for childpath in self._iterfiles(pmodule, prevnum):
                            childpath = self.getrelpath(b"/" + childpath)
                            if childpath:
                                changed.add(self.recode(childpath))

                for childpath in self._iterfiles(path, revnum):
                    childpath = self.getrelpath(b"/" + childpath)
                    if childpath:
                        changed.add(self.recode(childpath))

                # Handle directory copies
                if not ent.copyfrom_path or not parents:
                    continue
                # Copy sources not in parent revisions cannot be
                # represented, ignore their origin for now
                pmodule, prevnum = revsplit(parents[0])[1:]
                if ent.copyfrom_rev < prevnum:
                    continue
                copyfrompath = self.getrelpath(ent.copyfrom_path, pmodule)
                if not copyfrompath:
                    continue
                self.ui.debug(
                    b"mark %s came from %s:%d\n"
                    % (path, copyfrompath, ent.copyfrom_rev)
                )
                children = self._iterfiles(ent.copyfrom_path, ent.copyfrom_rev)
                for childpath in children:
                    childpath = self.getrelpath(b"/" + childpath, pmodule)
                    if not childpath:
                        continue
                    copytopath = path + childpath[len(copyfrompath) :]
                    copytopath = self.getrelpath(copytopath)
                    copies[self.recode(copytopath)] = self.recode(childpath)

        progress.complete()
        changed.update(removed)
        return (list(changed), removed, copies)

    def _fetch_revisions(self, from_revnum, to_revnum):
        if from_revnum < to_revnum:
            from_revnum, to_revnum = to_revnum, from_revnum

        self.child_cset = None

        def parselogentry(orig_paths, revnum, author, date, message):
            """Return the parsed commit object or None, and True if
            the revision is a branch root.
            """
            self.ui.debug(
                b"parsing revision %d (%d changes)\n"
                % (revnum, len(orig_paths))
            )

            branched = False
            rev = self.revid(revnum)
            # branch log might return entries for a parent we already have

            if rev in self.commits or revnum < to_revnum:
                return None, branched

            parents = []
            # check whether this revision is the start of a branch or part
            # of a branch renaming
            orig_paths = sorted(pycompat.iteritems(orig_paths))
            root_paths = [
                (p, e) for p, e in orig_paths if self.module.startswith(p)
            ]
            if root_paths:
                path, ent = root_paths[-1]
                if ent.copyfrom_path:
                    branched = True
                    newpath = ent.copyfrom_path + self.module[len(path) :]
                    # ent.copyfrom_rev may not be the actual last revision
                    previd = self.latest(newpath, ent.copyfrom_rev)
                    if previd is not None:
                        prevmodule, prevnum = revsplit(previd)[1:]
                        if prevnum >= self.startrev:
                            parents = [previd]
                            self.ui.note(
                                _(b'found parent of branch %s at %d: %s\n')
                                % (self.module, prevnum, prevmodule)
                            )
                else:
                    self.ui.debug(b"no copyfrom path, don't know what to do.\n")

            paths = []
            # filter out unrelated paths
            for path, ent in orig_paths:
                if self.getrelpath(path) is None:
                    continue
                paths.append((path, ent))

            date = parsesvndate(date)
            if self.ui.configbool(b'convert', b'localtimezone'):
                date = makedatetimestamp(date[0])

            if message:
                log = self.recode(message)
            else:
                log = b''

            if author:
                author = self.recode(author)
            else:
                author = b''

            try:
                branch = self.module.split(b"/")[-1]
                if branch == self.trunkname:
                    branch = None
            except IndexError:
                branch = None

            cset = commit(
                author=author,
                date=dateutil.datestr(date, b'%Y-%m-%d %H:%M:%S %1%2'),
                desc=log,
                parents=parents,
                branch=branch,
                rev=rev,
            )

            self.commits[rev] = cset
            # The parents list is *shared* among self.paths and the
            # commit object. Both will be updated below.
            self.paths[rev] = (paths, cset.parents)
            if self.child_cset and not self.child_cset.parents:
                self.child_cset.parents[:] = [rev]
            self.child_cset = cset
            return cset, branched

        self.ui.note(
            _(b'fetching revision log for "%s" from %d to %d\n')
            % (self.module, from_revnum, to_revnum)
        )

        try:
            firstcset = None
            lastonbranch = False
            stream = self._getlog([self.module], from_revnum, to_revnum)
            try:
                for entry in stream:
                    paths, revnum, author, date, message = entry
                    if revnum < self.startrev:
                        lastonbranch = True
                        break
                    if not paths:
                        self.ui.debug(b'revision %d has no entries\n' % revnum)
                        # If we ever leave the loop on an empty
                        # revision, do not try to get a parent branch
                        lastonbranch = lastonbranch or revnum == 0
                        continue
                    cset, lastonbranch = parselogentry(
                        paths, revnum, author, date, message
                    )
                    if cset:
                        firstcset = cset
                    if lastonbranch:
                        break
            finally:
                stream.close()

            if not lastonbranch and firstcset and not firstcset.parents:
                # The first revision of the sequence (the last fetched one)
                # has invalid parents if not a branch root. Find the parent
                # revision now, if any.
                try:
                    firstrevnum = self.revnum(firstcset.rev)
                    if firstrevnum > 1:
                        latest = self.latest(self.module, firstrevnum - 1)
                        if latest:
                            firstcset.parents.append(latest)
                except SvnPathNotFound:
                    pass
        except svn.core.SubversionException as xxx_todo_changeme:
            (inst, num) = xxx_todo_changeme.args
            if num == svn.core.SVN_ERR_FS_NO_SUCH_REVISION:
                raise error.Abort(
                    _(b'svn: branch has no revision %s') % to_revnum
                )
            raise

    def getfile(self, file, rev):
        # TODO: ra.get_file transmits the whole file instead of diffs.
        if file in self.removed:
            return None, None
        try:
            new_module, revnum = revsplit(rev)[1:]
            if self.module != new_module:
                self.module = new_module
                self.reparent(self.module)
            io = stringio()
            info = svn.ra.get_file(self.ra, file, revnum, io)
            data = io.getvalue()
            # ra.get_file() seems to keep a reference on the input buffer
            # preventing collection. Release it explicitly.
            io.close()
            if isinstance(info, list):
                info = info[-1]
            mode = (b"svn:executable" in info) and b'x' or b''
            mode = (b"svn:special" in info) and b'l' or mode
        except svn.core.SubversionException as e:
            notfound = (
                svn.core.SVN_ERR_FS_NOT_FOUND,
                svn.core.SVN_ERR_RA_DAV_PATH_NOT_FOUND,
            )
            if e.apr_err in notfound:  # File not found
                return None, None
            raise
        if mode == b'l':
            link_prefix = b"link "
            if data.startswith(link_prefix):
                data = data[len(link_prefix) :]
        return data, mode

    def _iterfiles(self, path, revnum):
        """Enumerate all files in path at revnum, recursively."""
        path = path.strip(b'/')
        pool = svn.core.Pool()
        rpath = b'/'.join([self.baseurl, quote(path)]).strip(b'/')
        entries = svn.client.ls(rpath, optrev(revnum), True, self.ctx, pool)
        if path:
            path += b'/'
        return (
            (path + p)
            for p, e in pycompat.iteritems(entries)
            if e.kind == svn.core.svn_node_file
        )

    def getrelpath(self, path, module=None):
        if module is None:
            module = self.module
        # Given the repository url of this wc, say
        #   "http://server/plone/CMFPlone/branches/Plone-2_0-branch"
        # extract the "entry" portion (a relative path) from what
        # svn log --xml says, i.e.
        #   "/CMFPlone/branches/Plone-2_0-branch/tests/PloneTestCase.py"
        # that is to say "tests/PloneTestCase.py"
        if path.startswith(module):
            relative = path.rstrip(b'/')[len(module) :]
            if relative.startswith(b'/'):
                return relative[1:]
            elif relative == b'':
                return relative

        # The path is outside our tracked tree...
        self.ui.debug(
            b'%r is not under %r, ignoring\n'
            % (pycompat.bytestr(path), pycompat.bytestr(module))
        )
        return None

    def _checkpath(self, path, revnum, module=None):
        if module is not None:
            prevmodule = self.reparent(b'')
            path = module + b'/' + path
        try:
            # ra.check_path does not like leading slashes very much, it leads
            # to PROPFIND subversion errors
            return svn.ra.check_path(self.ra, path.strip(b'/'), revnum)
        finally:
            if module is not None:
                self.reparent(prevmodule)

    def _getlog(
        self,
        paths,
        start,
        end,
        limit=0,
        discover_changed_paths=True,
        strict_node_history=False,
    ):
        # Normalize path names, svn >= 1.5 only wants paths relative to
        # supplied URL
        relpaths = []
        for p in paths:
            if not p.startswith(b'/'):
                p = self.module + b'/' + p
            relpaths.append(p.strip(b'/'))
        args = [
            self.baseurl,
            relpaths,
            start,
            end,
            limit,
            discover_changed_paths,
            strict_node_history,
        ]
        # developer config: convert.svn.debugsvnlog
        if not self.ui.configbool(b'convert', b'svn.debugsvnlog'):
            return directlogstream(*args)
        arg = encodeargs(args)
        hgexe = procutil.hgexecutable()
        cmd = b'%s debugsvnlog' % procutil.shellquote(hgexe)
        stdin, stdout = procutil.popen2(cmd)
        stdin.write(arg)
        try:
            stdin.close()
        except IOError:
            raise error.Abort(
                _(
                    b'Mercurial failed to run itself, check'
                    b' hg executable is in PATH'
                )
            )
        return logstream(stdout)


pre_revprop_change_template = b'''#!/bin/sh

REPOS="$1"
REV="$2"
USER="$3"
PROPNAME="$4"
ACTION="$5"

%(rules)s

echo "Changing prohibited revision property" >&2
exit 1
'''


def gen_pre_revprop_change_hook(prop_actions_allowed):
    rules = []
    for action, propname in prop_actions_allowed:
        rules.append(
            (
                b'if [ "$ACTION" = "%s" -a "$PROPNAME" = "%s" ]; '
                b'then exit 0; fi'
            )
            % (action, propname)
        )
    return pre_revprop_change_template % {b'rules': b'\n'.join(rules)}


class svn_sink(converter_sink, commandline):
    commit_re = re.compile(br'Committed revision (\d+).', re.M)
    uuid_re = re.compile(br'Repository UUID:\s*(\S+)', re.M)

    def prerun(self):
        if self.wc:
            os.chdir(self.wc)

    def postrun(self):
        if self.wc:
            os.chdir(self.cwd)

    def join(self, name):
        return os.path.join(self.wc, b'.svn', name)

    def revmapfile(self):
        return self.join(b'hg-shamap')

    def authorfile(self):
        return self.join(b'hg-authormap')

    def __init__(self, ui, repotype, path):

        converter_sink.__init__(self, ui, repotype, path)
        commandline.__init__(self, ui, b'svn')
        self.delete = []
        self.setexec = []
        self.delexec = []
        self.copies = []
        self.wc = None
        self.cwd = encoding.getcwd()

        created = False
        if os.path.isfile(os.path.join(path, b'.svn', b'entries')):
            self.wc = os.path.realpath(path)
            self.run0(b'update')
        else:
            if not re.search(br'^(file|http|https|svn|svn\+ssh)://', path):
                path = os.path.realpath(path)
                if os.path.isdir(os.path.dirname(path)):
                    if not os.path.exists(
                        os.path.join(path, b'db', b'fs-type')
                    ):
                        ui.status(
                            _(b"initializing svn repository '%s'\n")
                            % os.path.basename(path)
                        )
                        commandline(ui, b'svnadmin').run0(b'create', path)
                        created = path
                    path = util.normpath(path)
                    if not path.startswith(b'/'):
                        path = b'/' + path
                    path = b'file://' + path

            wcpath = os.path.join(
                encoding.getcwd(), os.path.basename(path) + b'-wc'
            )
            ui.status(
                _(b"initializing svn working copy '%s'\n")
                % os.path.basename(wcpath)
            )
            self.run0(b'checkout', path, wcpath)

            self.wc = wcpath
        self.opener = vfsmod.vfs(self.wc)
        self.wopener = vfsmod.vfs(self.wc)
        self.childmap = mapfile(ui, self.join(b'hg-childmap'))
        if util.checkexec(self.wc):
            self.is_exec = util.isexec
        else:
            self.is_exec = None

        if created:
            prop_actions_allowed = [
                (b'M', b'svn:log'),
                (b'A', b'hg:convert-branch'),
                (b'A', b'hg:convert-rev'),
            ]

            if self.ui.configbool(
                b'convert', b'svn.dangerous-set-commit-dates'
            ):
                prop_actions_allowed.append((b'M', b'svn:date'))

            hook = os.path.join(created, b'hooks', b'pre-revprop-change')
            fp = open(hook, b'wb')
            fp.write(gen_pre_revprop_change_hook(prop_actions_allowed))
            fp.close()
            util.setflags(hook, False, True)

        output = self.run0(b'info')
        self.uuid = self.uuid_re.search(output).group(1).strip()

    def wjoin(self, *names):
        return os.path.join(self.wc, *names)

    @propertycache
    def manifest(self):
        # As of svn 1.7, the "add" command fails when receiving
        # already tracked entries, so we have to track and filter them
        # ourselves.
        m = set()
        output = self.run0(b'ls', recursive=True, xml=True)
        doc = xml.dom.minidom.parseString(output)
        for e in doc.getElementsByTagName('entry'):
            for n in e.childNodes:
                if n.nodeType != n.ELEMENT_NODE or n.tagName != 'name':
                    continue
                name = ''.join(
                    c.data for c in n.childNodes if c.nodeType == c.TEXT_NODE
                )
                # Entries are compared with names coming from
                # mercurial, so bytes with undefined encoding. Our
                # best bet is to assume they are in local
                # encoding. They will be passed to command line calls
                # later anyway, so they better be.
                m.add(encoding.unitolocal(name))
                break
        return m

    def putfile(self, filename, flags, data):
        if b'l' in flags:
            self.wopener.symlink(data, filename)
        else:
            try:
                if os.path.islink(self.wjoin(filename)):
                    os.unlink(filename)
            except OSError:
                pass

            if self.is_exec:
                # We need to check executability of the file before the change,
                # because `vfs.write` is able to reset exec bit.
                wasexec = False
                if os.path.exists(self.wjoin(filename)):
                    wasexec = self.is_exec(self.wjoin(filename))

            self.wopener.write(filename, data)

            if self.is_exec:
                if wasexec:
                    if b'x' not in flags:
                        self.delexec.append(filename)
                else:
                    if b'x' in flags:
                        self.setexec.append(filename)
                util.setflags(self.wjoin(filename), False, b'x' in flags)

    def _copyfile(self, source, dest):
        # SVN's copy command pukes if the destination file exists, but
        # our copyfile method expects to record a copy that has
        # already occurred.  Cross the semantic gap.
        wdest = self.wjoin(dest)
        exists = os.path.lexists(wdest)
        if exists:
            fd, tempname = pycompat.mkstemp(
                prefix=b'hg-copy-', dir=os.path.dirname(wdest)
            )
            os.close(fd)
            os.unlink(tempname)
            os.rename(wdest, tempname)
        try:
            self.run0(b'copy', source, dest)
        finally:
            self.manifest.add(dest)
            if exists:
                try:
                    os.unlink(wdest)
                except OSError:
                    pass
                os.rename(tempname, wdest)

    def dirs_of(self, files):
        dirs = set()
        for f in files:
            if os.path.isdir(self.wjoin(f)):
                dirs.add(f)
            i = len(f)
            for i in iter(lambda: f.rfind(b'/', 0, i), -1):
                dirs.add(f[:i])
        return dirs

    def add_dirs(self, files):
        add_dirs = [
            d for d in sorted(self.dirs_of(files)) if d not in self.manifest
        ]
        if add_dirs:
            self.manifest.update(add_dirs)
            self.xargs(add_dirs, b'add', non_recursive=True, quiet=True)
        return add_dirs

    def add_files(self, files):
        files = [f for f in files if f not in self.manifest]
        if files:
            self.manifest.update(files)
            self.xargs(files, b'add', quiet=True)
        return files

    def addchild(self, parent, child):
        self.childmap[parent] = child

    def revid(self, rev):
        return b"svn:%s@%s" % (self.uuid, rev)

    def putcommit(
        self, files, copies, parents, commit, source, revmap, full, cleanp2
    ):
        for parent in parents:
            try:
                return self.revid(self.childmap[parent])
            except KeyError:
                pass

        # Apply changes to working copy
        for f, v in files:
            data, mode = source.getfile(f, v)
            if data is None:
                self.delete.append(f)
            else:
                self.putfile(f, mode, data)
                if f in copies:
                    self.copies.append([copies[f], f])
        if full:
            self.delete.extend(sorted(self.manifest.difference(files)))
        files = [f[0] for f in files]

        entries = set(self.delete)
        files = frozenset(files)
        entries.update(self.add_dirs(files.difference(entries)))
        if self.copies:
            for s, d in self.copies:
                self._copyfile(s, d)
            self.copies = []
        if self.delete:
            self.xargs(self.delete, b'delete')
            for f in self.delete:
                self.manifest.remove(f)
            self.delete = []
        entries.update(self.add_files(files.difference(entries)))
        if self.delexec:
            self.xargs(self.delexec, b'propdel', b'svn:executable')
            self.delexec = []
        if self.setexec:
            self.xargs(self.setexec, b'propset', b'svn:executable', b'*')
            self.setexec = []

        fd, messagefile = pycompat.mkstemp(prefix=b'hg-convert-')
        fp = os.fdopen(fd, 'wb')
        fp.write(util.tonativeeol(commit.desc))
        fp.close()
        try:
            output = self.run0(
                b'commit',
                username=stringutil.shortuser(commit.author),
                file=messagefile,
                encoding=b'utf-8',
            )
            try:
                rev = self.commit_re.search(output).group(1)
            except AttributeError:
                if not files:
                    return parents[0] if parents else b'None'
                self.ui.warn(_(b'unexpected svn output:\n'))
                self.ui.warn(output)
                raise error.Abort(_(b'unable to cope with svn output'))
            if commit.rev:
                self.run(
                    b'propset',
                    b'hg:convert-rev',
                    commit.rev,
                    revprop=True,
                    revision=rev,
                )
            if commit.branch and commit.branch != b'default':
                self.run(
                    b'propset',
                    b'hg:convert-branch',
                    commit.branch,
                    revprop=True,
                    revision=rev,
                )

            if self.ui.configbool(
                b'convert', b'svn.dangerous-set-commit-dates'
            ):
                # Subverson always uses UTC to represent date and time
                date = dateutil.parsedate(commit.date)
                date = (date[0], 0)

                # The only way to set date and time for svn commit is to use propset after commit is done
                self.run(
                    b'propset',
                    b'svn:date',
                    formatsvndate(date),
                    revprop=True,
                    revision=rev,
                )

            for parent in parents:
                self.addchild(parent, rev)
            return self.revid(rev)
        finally:
            os.unlink(messagefile)

    def puttags(self, tags):
        self.ui.warn(_(b'writing Subversion tags is not yet implemented\n'))
        return None, None

    def hascommitfrommap(self, rev):
        # We trust that revisions referenced in a map still is present
        # TODO: implement something better if necessary and feasible
        return True

    def hascommitforsplicemap(self, rev):
        # This is not correct as one can convert to an existing subversion
        # repository and childmap would not list all revisions. Too bad.
        if rev in self.childmap:
            return True
        raise error.Abort(
            _(
                b'splice map revision %s not found in subversion '
                b'child map (revision lookups are not implemented)'
            )
            % rev
        )
