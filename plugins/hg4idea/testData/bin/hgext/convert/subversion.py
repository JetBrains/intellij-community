# Subversion 1.4/1.5 Python API backend
#
# Copyright(C) 2007 Daniel Holth et al

import os, re, sys, tempfile, urllib, urllib2, xml.dom.minidom
import cPickle as pickle

from mercurial import strutil, scmutil, util, encoding
from mercurial.i18n import _

propertycache = util.propertycache

# Subversion stuff. Works best with very recent Python SVN bindings
# e.g. SVN 1.5 or backports. Thanks to the bzr folks for enhancing
# these bindings.

from cStringIO import StringIO

from common import NoRepo, MissingTool, commit, encodeargs, decodeargs
from common import commandline, converter_source, converter_sink, mapfile
from common import makedatetimestamp

try:
    from svn.core import SubversionException, Pool
    import svn
    import svn.client
    import svn.core
    import svn.ra
    import svn.delta
    import transport
    import warnings
    warnings.filterwarnings('ignore',
            module='svn.core',
            category=DeprecationWarning)

except ImportError:
    svn = None

class SvnPathNotFound(Exception):
    pass

def revsplit(rev):
    """Parse a revision string and return (uuid, path, revnum)."""
    url, revnum = rev.rsplit('@', 1)
    parts = url.split('/', 1)
    mod = ''
    if len(parts) > 1:
        mod = '/' + parts[1]
    return parts[0][4:], mod, int(revnum)

def quote(s):
    # As of svn 1.7, many svn calls expect "canonical" paths. In
    # theory, we should call svn.core.*canonicalize() on all paths
    # before passing them to the API.  Instead, we assume the base url
    # is canonical and copy the behaviour of svn URL encoding function
    # so we can extend it safely with new components. The "safe"
    # characters were taken from the "svn_uri__char_validity" table in
    # libsvn_subr/path.c.
    return urllib.quote(s, "!$&'()*+,-./:=@_~")

def geturl(path):
    try:
        return svn.client.url_from_path(svn.core.svn_path_canonicalize(path))
    except SubversionException:
        # svn.client.url_from_path() fails with local repositories
        pass
    if os.path.isdir(path):
        path = os.path.normpath(os.path.abspath(path))
        if os.name == 'nt':
            path = '/' + util.normpath(path)
        # Module URL is later compared with the repository URL returned
        # by svn API, which is UTF-8.
        path = encoding.tolocal(path)
        path = 'file://%s' % quote(path)
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

def get_log_child(fp, url, paths, start, end, limit=0,
                  discover_changed_paths=True, strict_node_history=False):
    protocol = -1
    def receiver(orig_paths, revnum, author, date, message, pool):
        if orig_paths is not None:
            for k, v in orig_paths.iteritems():
                orig_paths[k] = changedpath(v)
        pickle.dump((orig_paths, revnum, author, date, message),
                    fp, protocol)

    try:
        # Use an ra of our own so that our parent can consume
        # our results without confusing the server.
        t = transport.SvnRaTransport(url=url)
        svn.ra.get_log(t.ra, paths, start, end, limit,
                       discover_changed_paths,
                       strict_node_history,
                       receiver)
    except IOError:
        # Caller may interrupt the iteration
        pickle.dump(None, fp, protocol)
    except Exception, inst:
        pickle.dump(str(inst), fp, protocol)
    else:
        pickle.dump(None, fp, protocol)
    fp.close()
    # With large history, cleanup process goes crazy and suddenly
    # consumes *huge* amount of memory. The output file being closed,
    # there is no need for clean termination.
    os._exit(0)

def debugsvnlog(ui, **opts):
    """Fetch SVN log in a subprocess and channel them back to parent to
    avoid memory collection issues.
    """
    if svn is None:
        raise util.Abort(_('debugsvnlog could not load Subversion python '
                           'bindings'))

    util.setbinary(sys.stdin)
    util.setbinary(sys.stdout)
    args = decodeargs(sys.stdin.read())
    get_log_child(sys.stdout, *args)

class logstream(object):
    """Interruptible revision log iterator."""
    def __init__(self, stdout):
        self._stdout = stdout

    def __iter__(self):
        while True:
            try:
                entry = pickle.load(self._stdout)
            except EOFError:
                raise util.Abort(_('Mercurial failed to run itself, check'
                                   ' hg executable is in PATH'))
            try:
                orig_paths, revnum, author, date, message = entry
            except (TypeError, ValueError):
                if entry is None:
                    break
                raise util.Abort(_("log stream exception '%s'") % entry)
            yield entry

    def close(self):
        if self._stdout:
            self._stdout.close()
            self._stdout = None


# Check to see if the given path is a local Subversion repo. Verify this by
# looking for several svn-specific files and directories in the given
# directory.
def filecheck(ui, path, proto):
    for x in ('locks', 'hooks', 'format', 'db'):
        if not os.path.exists(os.path.join(path, x)):
            return False
    return True

# Check to see if a given path is the root of an svn repo over http. We verify
# this by requesting a version-controlled URL we know can't exist and looking
# for the svn-specific "not found" XML.
def httpcheck(ui, path, proto):
    try:
        opener = urllib2.build_opener()
        rsp = opener.open('%s://%s/!svn/ver/0/.svn' % (proto, path))
        data = rsp.read()
    except urllib2.HTTPError, inst:
        if inst.code != 404:
            # Except for 404 we cannot know for sure this is not an svn repo
            ui.warn(_('svn: cannot probe remote repository, assume it could '
                      'be a subversion repository. Use --source-type if you '
                      'know better.\n'))
            return True
        data = inst.fp.read()
    except Exception:
        # Could be urllib2.URLError if the URL is invalid or anything else.
        return False
    return '<m:human-readable errcode="160013">' in data

protomap = {'http': httpcheck,
            'https': httpcheck,
            'file': filecheck,
            }
def issvnurl(ui, url):
    try:
        proto, path = url.split('://', 1)
        if proto == 'file':
            if (os.name == 'nt' and path[:1] == '/' and path[1:2].isalpha()
                and path[2:6].lower() == '%3a/'):
                path = path[:2] + ':/' + path[6:]
            path = urllib.url2pathname(path)
    except ValueError:
        proto = 'file'
        path = os.path.abspath(url)
    if proto == 'file':
        path = util.pconvert(path)
    check = protomap.get(proto, lambda *args: False)
    while '/' in path:
        if check(ui, path, proto):
            return True
        path = path.rsplit('/', 1)[0]
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
    def __init__(self, ui, url, rev=None):
        super(svn_source, self).__init__(ui, url, rev=rev)

        if not (url.startswith('svn://') or url.startswith('svn+ssh://') or
                (os.path.exists(url) and
                 os.path.exists(os.path.join(url, '.svn'))) or
                issvnurl(ui, url)):
            raise NoRepo(_("%s does not look like a Subversion repository")
                         % url)
        if svn is None:
            raise MissingTool(_('could not load Subversion python bindings'))

        try:
            version = svn.core.SVN_VER_MAJOR, svn.core.SVN_VER_MINOR
            if version < (1, 4):
                raise MissingTool(_('Subversion python bindings %d.%d found, '
                                    '1.4 or later required') % version)
        except AttributeError:
            raise MissingTool(_('Subversion python bindings are too old, 1.4 '
                                'or later required'))

        self.lastrevs = {}

        latest = None
        try:
            # Support file://path@rev syntax. Useful e.g. to convert
            # deleted branches.
            at = url.rfind('@')
            if at >= 0:
                latest = int(url[at + 1:])
                url = url[:at]
        except ValueError:
            pass
        self.url = geturl(url)
        self.encoding = 'UTF-8' # Subversion is always nominal UTF-8
        try:
            self.transport = transport.SvnRaTransport(url=self.url)
            self.ra = self.transport.ra
            self.ctx = self.transport.client
            self.baseurl = svn.ra.get_repos_root(self.ra)
            # Module is either empty or a repository path starting with
            # a slash and not ending with a slash.
            self.module = urllib.unquote(self.url[len(self.baseurl):])
            self.prevmodule = None
            self.rootmodule = self.module
            self.commits = {}
            self.paths = {}
            self.uuid = svn.ra.get_uuid(self.ra)
        except SubversionException:
            ui.traceback()
            raise NoRepo(_("%s does not look like a Subversion repository")
                         % self.url)

        if rev:
            try:
                latest = int(rev)
            except ValueError:
                raise util.Abort(_('svn: revision %s is not an integer') % rev)

        self.trunkname = self.ui.config('convert', 'svn.trunk',
                                        'trunk').strip('/')
        self.startrev = self.ui.config('convert', 'svn.startrev', default=0)
        try:
            self.startrev = int(self.startrev)
            if self.startrev < 0:
                self.startrev = 0
        except ValueError:
            raise util.Abort(_('svn: start revision %s is not an integer')
                             % self.startrev)

        try:
            self.head = self.latest(self.module, latest)
        except SvnPathNotFound:
            self.head = None
        if not self.head:
            raise util.Abort(_('no revision found in module %s')
                             % self.module)
        self.last_changed = self.revnum(self.head)

        self._changescache = None

        if os.path.exists(os.path.join(url, '.svn/entries')):
            self.wc = url
        else:
            self.wc = None
        self.convertfp = None

    def setrevmap(self, revmap):
        lastrevs = {}
        for revid in revmap.iterkeys():
            uuid, module, revnum = revsplit(revid)
            lastrevnum = lastrevs.setdefault(module, revnum)
            if revnum > lastrevnum:
                lastrevs[module] = revnum
        self.lastrevs = lastrevs

    def exists(self, path, optrev):
        try:
            svn.client.ls(self.url.rstrip('/') + '/' + quote(path),
                                 optrev, False, self.ctx)
            return True
        except SubversionException:
            return False

    def getheads(self):

        def isdir(path, revnum):
            kind = self._checkpath(path, revnum)
            return kind == svn.core.svn_node_dir

        def getcfgpath(name, rev):
            cfgpath = self.ui.config('convert', 'svn.' + name)
            if cfgpath is not None and cfgpath.strip() == '':
                return None
            path = (cfgpath or name).strip('/')
            if not self.exists(path, rev):
                if self.module.endswith(path) and name == 'trunk':
                    # we are converting from inside this directory
                    return None
                if cfgpath:
                    raise util.Abort(_('expected %s to be at %r, but not found')
                                 % (name, path))
                return None
            self.ui.note(_('found %s at %r\n') % (name, path))
            return path

        rev = optrev(self.last_changed)
        oldmodule = ''
        trunk = getcfgpath('trunk', rev)
        self.tags = getcfgpath('tags', rev)
        branches = getcfgpath('branches', rev)

        # If the project has a trunk or branches, we will extract heads
        # from them. We keep the project root otherwise.
        if trunk:
            oldmodule = self.module or ''
            self.module += '/' + trunk
            self.head = self.latest(self.module, self.last_changed)
            if not self.head:
                raise util.Abort(_('no revision found in module %s')
                                 % self.module)

        # First head in the list is the module's head
        self.heads = [self.head]
        if self.tags is not None:
            self.tags = '%s/%s' % (oldmodule , (self.tags or 'tags'))

        # Check if branches bring a few more heads to the list
        if branches:
            rpath = self.url.strip('/')
            branchnames = svn.client.ls(rpath + '/' + quote(branches),
                                        rev, False, self.ctx)
            for branch in sorted(branchnames):
                module = '%s/%s/%s' % (oldmodule, branches, branch)
                if not isdir(module, self.last_changed):
                    continue
                brevid = self.latest(module, self.last_changed)
                if not brevid:
                    self.ui.note(_('ignoring empty branch %s\n') % branch)
                    continue
                self.ui.note(_('found branch %s at %d\n') %
                             (branch, self.revnum(brevid)))
                self.heads.append(brevid)

        if self.startrev and self.heads:
            if len(self.heads) > 1:
                raise util.Abort(_('svn: start revision is not supported '
                                   'with more than one branch'))
            revnum = self.revnum(self.heads[0])
            if revnum < self.startrev:
                raise util.Abort(
                    _('svn: no revision found after start revision %d')
                                 % self.startrev)

        return self.heads

    def getchanges(self, rev):
        if self._changescache and self._changescache[0] == rev:
            return self._changescache[1]
        self._changescache = None
        (paths, parents) = self.paths[rev]
        if parents:
            files, self.removed, copies = self.expandpaths(rev, paths, parents)
        else:
            # Perform a full checkout on roots
            uuid, module, revnum = revsplit(rev)
            entries = svn.client.ls(self.baseurl + quote(module),
                                    optrev(revnum), True, self.ctx)
            files = [n for n, e in entries.iteritems()
                     if e.kind == svn.core.svn_node_file]
            copies = {}
            self.removed = set()

        files.sort()
        files = zip(files, [rev] * len(files))

        # caller caches the result, so free it here to release memory
        del self.paths[rev]
        return (files, copies)

    def getchangedfiles(self, rev, i):
        changes = self.getchanges(rev)
        self._changescache = (rev, changes)
        return [f[0] for f in changes[0]]

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
                raise util.Abort(_('svn: revision %s not found') % revnum)
        commit = self.commits[rev]
        # caller caches the result, so free it here to release memory
        del self.commits[rev]
        return commit

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
                copies = [(e.copyfrom_path, e.copyfrom_rev, p) for p, e
                          in origpaths.iteritems() if e.copyfrom_path]
                # Apply moves/copies from more specific to general
                copies.sort(reverse=True)

                srctagspath = tagspath
                if copies and copies[-1][2] == tagspath:
                    # Track tags directory moves
                    srctagspath = copies.pop()[0]

                for source, sourcerev, dest in copies:
                    if not dest.startswith(tagspath + '/'):
                        continue
                    for tag in pendings:
                        if tag[0].startswith(dest):
                            tagpath = source + tag[0][len(dest):]
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
                addeds = dict((p, e.copyfrom_path) for p, e
                              in origpaths.iteritems()
                              if e.action == 'A' and e.copyfrom_path)
                badroots = set()
                for destroot in addeds:
                    for source, sourcerev, dest in pendings:
                        if (not dest.startswith(destroot + '/')
                            or source.startswith(addeds[destroot] + '/')):
                            continue
                        badroots.add(destroot)
                        break

                for badroot in badroots:
                    pendings = [p for p in pendings if p[2] != badroot
                                and not p[2].startswith(badroot + '/')]

                # Tell tag renamings from tag creations
                renamings = []
                for source, sourcerev, dest in pendings:
                    tagname = dest.split('/')[-1]
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
            self.convertfp = open(os.path.join(self.wc, '.svn', 'hg-shamap'),
                                  'a')
        self.convertfp.write('%s %d\n' % (destrev, self.revnum(rev)))
        self.convertfp.flush()

    def revid(self, revnum, module=None):
        return 'svn:%s%s@%s' % (self.uuid, module or self.module, revnum)

    def revnum(self, rev):
        return int(rev.split('@')[-1])

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
                    if revnum <= stop:
                        break

                    for p in paths:
                        if (not path.startswith(p) or
                            not paths[p].copyfrom_path):
                            continue
                        newpath = paths[p].copyfrom_path + path[len(p):]
                        self.ui.debug("branch renamed from %s to %s at %d\n" %
                                      (path, newpath, revnum))
                        path = newpath
                        break
                if not paths:
                    revnum = None
                return revnum, path
            finally:
                stream.close()

        if not path.startswith(self.rootmodule):
            # Requests on foreign branches may be forbidden at server level
            self.ui.debug('ignoring foreign branch %r\n' % path)
            return None

        if stop is None:
            stop = svn.ra.get_latest_revnum(self.ra)
        try:
            prevmodule = self.reparent('')
            dirent = svn.ra.stat(self.ra, path.strip('/'), stop)
            self.reparent(prevmodule)
        except SubversionException:
            dirent = None
        if not dirent:
            raise SvnPathNotFound(_('%s not found up to revision %d')
                                  % (path, stop))

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
                self.ui.debug('ignoring empty branch %r\n' % realpath)
                return None

        if not realpath.startswith(self.rootmodule):
            self.ui.debug('ignoring foreign branch %r\n' % realpath)
            return None
        return self.revid(revnum, realpath)

    def reparent(self, module):
        """Reparent the svn transport and return the previous parent."""
        if self.prevmodule == module:
            return module
        svnurl = self.baseurl + quote(module)
        prevmodule = self.prevmodule
        if prevmodule is None:
            prevmodule = ''
        self.ui.debug("reparent to %s\n" % svnurl)
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

        for i, (path, ent) in enumerate(paths):
            self.ui.progress(_('scanning paths'), i, item=path,
                             total=len(paths))
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
                self.ui.debug("copied to %s from %s@%s\n" %
                              (entrypath, copyfrom_path, ent.copyfrom_rev))
                copies[self.recode(entrypath)] = self.recode(copyfrom_path)
            elif kind == 0: # gone, but had better be a deleted *file*
                self.ui.debug("gone from %s\n" % ent.copyfrom_rev)
                pmodule, prevnum = revsplit(parents[0])[1:]
                parentpath = pmodule + "/" + entrypath
                fromkind = self._checkpath(entrypath, prevnum, pmodule)

                if fromkind == svn.core.svn_node_file:
                    removed.add(self.recode(entrypath))
                elif fromkind == svn.core.svn_node_dir:
                    oroot = parentpath.strip('/')
                    nroot = path.strip('/')
                    children = self._iterfiles(oroot, prevnum)
                    for childpath in children:
                        childpath = childpath.replace(oroot, nroot)
                        childpath = self.getrelpath("/" + childpath, pmodule)
                        if childpath:
                            removed.add(self.recode(childpath))
                else:
                    self.ui.debug('unknown path in revision %d: %s\n' % \
                                  (revnum, path))
            elif kind == svn.core.svn_node_dir:
                if ent.action == 'M':
                    # If the directory just had a prop change,
                    # then we shouldn't need to look for its children.
                    continue
                if ent.action == 'R' and parents:
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
                            childpath = self.getrelpath("/" + childpath)
                            if childpath:
                                changed.add(self.recode(childpath))

                for childpath in self._iterfiles(path, revnum):
                    childpath = self.getrelpath("/" + childpath)
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
                self.ui.debug("mark %s came from %s:%d\n"
                              % (path, copyfrompath, ent.copyfrom_rev))
                children = self._iterfiles(ent.copyfrom_path, ent.copyfrom_rev)
                for childpath in children:
                    childpath = self.getrelpath("/" + childpath, pmodule)
                    if not childpath:
                        continue
                    copytopath = path + childpath[len(copyfrompath):]
                    copytopath = self.getrelpath(copytopath)
                    copies[self.recode(copytopath)] = self.recode(childpath)

        self.ui.progress(_('scanning paths'), None)
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
            self.ui.debug("parsing revision %d (%d changes)\n" %
                          (revnum, len(orig_paths)))

            branched = False
            rev = self.revid(revnum)
            # branch log might return entries for a parent we already have

            if rev in self.commits or revnum < to_revnum:
                return None, branched

            parents = []
            # check whether this revision is the start of a branch or part
            # of a branch renaming
            orig_paths = sorted(orig_paths.iteritems())
            root_paths = [(p, e) for p, e in orig_paths
                          if self.module.startswith(p)]
            if root_paths:
                path, ent = root_paths[-1]
                if ent.copyfrom_path:
                    branched = True
                    newpath = ent.copyfrom_path + self.module[len(path):]
                    # ent.copyfrom_rev may not be the actual last revision
                    previd = self.latest(newpath, ent.copyfrom_rev)
                    if previd is not None:
                        prevmodule, prevnum = revsplit(previd)[1:]
                        if prevnum >= self.startrev:
                            parents = [previd]
                            self.ui.note(
                                _('found parent of branch %s at %d: %s\n') %
                                (self.module, prevnum, prevmodule))
                else:
                    self.ui.debug("no copyfrom path, don't know what to do.\n")

            paths = []
            # filter out unrelated paths
            for path, ent in orig_paths:
                if self.getrelpath(path) is None:
                    continue
                paths.append((path, ent))

            # Example SVN datetime. Includes microseconds.
            # ISO-8601 conformant
            # '2007-01-04T17:35:00.902377Z'
            date = util.parsedate(date[:19] + " UTC", ["%Y-%m-%dT%H:%M:%S"])
            if self.ui.configbool('convert', 'localtimezone'):
                date = makedatetimestamp(date[0])

            log = message and self.recode(message) or ''
            author = author and self.recode(author) or ''
            try:
                branch = self.module.split("/")[-1]
                if branch == self.trunkname:
                    branch = None
            except IndexError:
                branch = None

            cset = commit(author=author,
                          date=util.datestr(date, '%Y-%m-%d %H:%M:%S %1%2'),
                          desc=log,
                          parents=parents,
                          branch=branch,
                          rev=rev)

            self.commits[rev] = cset
            # The parents list is *shared* among self.paths and the
            # commit object. Both will be updated below.
            self.paths[rev] = (paths, cset.parents)
            if self.child_cset and not self.child_cset.parents:
                self.child_cset.parents[:] = [rev]
            self.child_cset = cset
            return cset, branched

        self.ui.note(_('fetching revision log for "%s" from %d to %d\n') %
                     (self.module, from_revnum, to_revnum))

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
                        self.ui.debug('revision %d has no entries\n' % revnum)
                        # If we ever leave the loop on an empty
                        # revision, do not try to get a parent branch
                        lastonbranch = lastonbranch or revnum == 0
                        continue
                    cset, lastonbranch = parselogentry(paths, revnum, author,
                                                       date, message)
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
        except SubversionException, (inst, num):
            if num == svn.core.SVN_ERR_FS_NO_SUCH_REVISION:
                raise util.Abort(_('svn: branch has no revision %s')
                                 % to_revnum)
            raise

    def getfile(self, file, rev):
        # TODO: ra.get_file transmits the whole file instead of diffs.
        if file in self.removed:
            raise IOError
        mode = ''
        try:
            new_module, revnum = revsplit(rev)[1:]
            if self.module != new_module:
                self.module = new_module
                self.reparent(self.module)
            io = StringIO()
            info = svn.ra.get_file(self.ra, file, revnum, io)
            data = io.getvalue()
            # ra.get_file() seems to keep a reference on the input buffer
            # preventing collection. Release it explicitly.
            io.close()
            if isinstance(info, list):
                info = info[-1]
            mode = ("svn:executable" in info) and 'x' or ''
            mode = ("svn:special" in info) and 'l' or mode
        except SubversionException, e:
            notfound = (svn.core.SVN_ERR_FS_NOT_FOUND,
                svn.core.SVN_ERR_RA_DAV_PATH_NOT_FOUND)
            if e.apr_err in notfound: # File not found
                raise IOError
            raise
        if mode == 'l':
            link_prefix = "link "
            if data.startswith(link_prefix):
                data = data[len(link_prefix):]
        return data, mode

    def _iterfiles(self, path, revnum):
        """Enumerate all files in path at revnum, recursively."""
        path = path.strip('/')
        pool = Pool()
        rpath = '/'.join([self.baseurl, quote(path)]).strip('/')
        entries = svn.client.ls(rpath, optrev(revnum), True, self.ctx, pool)
        if path:
            path += '/'
        return ((path + p) for p, e in entries.iteritems()
                if e.kind == svn.core.svn_node_file)

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
            relative = path.rstrip('/')[len(module):]
            if relative.startswith('/'):
                return relative[1:]
            elif relative == '':
                return relative

        # The path is outside our tracked tree...
        self.ui.debug('%r is not under %r, ignoring\n' % (path, module))
        return None

    def _checkpath(self, path, revnum, module=None):
        if module is not None:
            prevmodule = self.reparent('')
            path = module + '/' + path
        try:
            # ra.check_path does not like leading slashes very much, it leads
            # to PROPFIND subversion errors
            return svn.ra.check_path(self.ra, path.strip('/'), revnum)
        finally:
            if module is not None:
                self.reparent(prevmodule)

    def _getlog(self, paths, start, end, limit=0, discover_changed_paths=True,
                strict_node_history=False):
        # Normalize path names, svn >= 1.5 only wants paths relative to
        # supplied URL
        relpaths = []
        for p in paths:
            if not p.startswith('/'):
                p = self.module + '/' + p
            relpaths.append(p.strip('/'))
        args = [self.baseurl, relpaths, start, end, limit,
                discover_changed_paths, strict_node_history]
        arg = encodeargs(args)
        hgexe = util.hgexecutable()
        cmd = '%s debugsvnlog' % util.shellquote(hgexe)
        stdin, stdout = util.popen2(util.quotecommand(cmd))
        stdin.write(arg)
        try:
            stdin.close()
        except IOError:
            raise util.Abort(_('Mercurial failed to run itself, check'
                               ' hg executable is in PATH'))
        return logstream(stdout)

pre_revprop_change = '''#!/bin/sh

REPOS="$1"
REV="$2"
USER="$3"
PROPNAME="$4"
ACTION="$5"

if [ "$ACTION" = "M" -a "$PROPNAME" = "svn:log" ]; then exit 0; fi
if [ "$ACTION" = "A" -a "$PROPNAME" = "hg:convert-branch" ]; then exit 0; fi
if [ "$ACTION" = "A" -a "$PROPNAME" = "hg:convert-rev" ]; then exit 0; fi

echo "Changing prohibited revision property" >&2
exit 1
'''

class svn_sink(converter_sink, commandline):
    commit_re = re.compile(r'Committed revision (\d+).', re.M)
    uuid_re = re.compile(r'Repository UUID:\s*(\S+)', re.M)

    def prerun(self):
        if self.wc:
            os.chdir(self.wc)

    def postrun(self):
        if self.wc:
            os.chdir(self.cwd)

    def join(self, name):
        return os.path.join(self.wc, '.svn', name)

    def revmapfile(self):
        return self.join('hg-shamap')

    def authorfile(self):
        return self.join('hg-authormap')

    def __init__(self, ui, path):

        converter_sink.__init__(self, ui, path)
        commandline.__init__(self, ui, 'svn')
        self.delete = []
        self.setexec = []
        self.delexec = []
        self.copies = []
        self.wc = None
        self.cwd = os.getcwd()

        created = False
        if os.path.isfile(os.path.join(path, '.svn', 'entries')):
            self.wc = os.path.realpath(path)
            self.run0('update')
        else:
            if not re.search(r'^(file|http|https|svn|svn\+ssh)\://', path):
                path = os.path.realpath(path)
                if os.path.isdir(os.path.dirname(path)):
                    if not os.path.exists(os.path.join(path, 'db', 'fs-type')):
                        ui.status(_('initializing svn repository %r\n') %
                                  os.path.basename(path))
                        commandline(ui, 'svnadmin').run0('create', path)
                        created = path
                    path = util.normpath(path)
                    if not path.startswith('/'):
                        path = '/' + path
                    path = 'file://' + path

            wcpath = os.path.join(os.getcwd(), os.path.basename(path) + '-wc')
            ui.status(_('initializing svn working copy %r\n')
                      % os.path.basename(wcpath))
            self.run0('checkout', path, wcpath)

            self.wc = wcpath
        self.opener = scmutil.opener(self.wc)
        self.wopener = scmutil.opener(self.wc)
        self.childmap = mapfile(ui, self.join('hg-childmap'))
        self.is_exec = util.checkexec(self.wc) and util.isexec or None

        if created:
            hook = os.path.join(created, 'hooks', 'pre-revprop-change')
            fp = open(hook, 'w')
            fp.write(pre_revprop_change)
            fp.close()
            util.setflags(hook, False, True)

        output = self.run0('info')
        self.uuid = self.uuid_re.search(output).group(1).strip()

    def wjoin(self, *names):
        return os.path.join(self.wc, *names)

    @propertycache
    def manifest(self):
        # As of svn 1.7, the "add" command fails when receiving
        # already tracked entries, so we have to track and filter them
        # ourselves.
        m = set()
        output = self.run0('ls', recursive=True, xml=True)
        doc = xml.dom.minidom.parseString(output)
        for e in doc.getElementsByTagName('entry'):
            for n in e.childNodes:
                if n.nodeType != n.ELEMENT_NODE or n.tagName != 'name':
                    continue
                name = ''.join(c.data for c in n.childNodes
                               if c.nodeType == c.TEXT_NODE)
                # Entries are compared with names coming from
                # mercurial, so bytes with undefined encoding. Our
                # best bet is to assume they are in local
                # encoding. They will be passed to command line calls
                # later anyway, so they better be.
                m.add(encoding.tolocal(name.encode('utf-8')))
                break
        return m

    def putfile(self, filename, flags, data):
        if 'l' in flags:
            self.wopener.symlink(data, filename)
        else:
            try:
                if os.path.islink(self.wjoin(filename)):
                    os.unlink(filename)
            except OSError:
                pass
            self.wopener.write(filename, data)

            if self.is_exec:
                if self.is_exec(self.wjoin(filename)):
                    if 'x' not in flags:
                        self.delexec.append(filename)
                else:
                    if 'x' in flags:
                        self.setexec.append(filename)
                util.setflags(self.wjoin(filename), False, 'x' in flags)

    def _copyfile(self, source, dest):
        # SVN's copy command pukes if the destination file exists, but
        # our copyfile method expects to record a copy that has
        # already occurred.  Cross the semantic gap.
        wdest = self.wjoin(dest)
        exists = os.path.lexists(wdest)
        if exists:
            fd, tempname = tempfile.mkstemp(
                prefix='hg-copy-', dir=os.path.dirname(wdest))
            os.close(fd)
            os.unlink(tempname)
            os.rename(wdest, tempname)
        try:
            self.run0('copy', source, dest)
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
            for i in strutil.rfindall(f, '/'):
                dirs.add(f[:i])
        return dirs

    def add_dirs(self, files):
        add_dirs = [d for d in sorted(self.dirs_of(files))
                    if d not in self.manifest]
        if add_dirs:
            self.manifest.update(add_dirs)
            self.xargs(add_dirs, 'add', non_recursive=True, quiet=True)
        return add_dirs

    def add_files(self, files):
        files = [f for f in files if f not in self.manifest]
        if files:
            self.manifest.update(files)
            self.xargs(files, 'add', quiet=True)
        return files

    def tidy_dirs(self, names):
        deleted = []
        for d in sorted(self.dirs_of(names), reverse=True):
            wd = self.wjoin(d)
            if os.listdir(wd) == '.svn':
                self.run0('delete', d)
                self.manifest.remove(d)
                deleted.append(d)
        return deleted

    def addchild(self, parent, child):
        self.childmap[parent] = child

    def revid(self, rev):
        return u"svn:%s@%s" % (self.uuid, rev)

    def putcommit(self, files, copies, parents, commit, source, revmap):
        for parent in parents:
            try:
                return self.revid(self.childmap[parent])
            except KeyError:
                pass

        # Apply changes to working copy
        for f, v in files:
            try:
                data, mode = source.getfile(f, v)
            except IOError:
                self.delete.append(f)
            else:
                self.putfile(f, mode, data)
                if f in copies:
                    self.copies.append([copies[f], f])
        files = [f[0] for f in files]

        entries = set(self.delete)
        files = frozenset(files)
        entries.update(self.add_dirs(files.difference(entries)))
        if self.copies:
            for s, d in self.copies:
                self._copyfile(s, d)
            self.copies = []
        if self.delete:
            self.xargs(self.delete, 'delete')
            for f in self.delete:
                self.manifest.remove(f)
            self.delete = []
        entries.update(self.add_files(files.difference(entries)))
        entries.update(self.tidy_dirs(entries))
        if self.delexec:
            self.xargs(self.delexec, 'propdel', 'svn:executable')
            self.delexec = []
        if self.setexec:
            self.xargs(self.setexec, 'propset', 'svn:executable', '*')
            self.setexec = []

        fd, messagefile = tempfile.mkstemp(prefix='hg-convert-')
        fp = os.fdopen(fd, 'w')
        fp.write(commit.desc)
        fp.close()
        try:
            output = self.run0('commit',
                               username=util.shortuser(commit.author),
                               file=messagefile,
                               encoding='utf-8')
            try:
                rev = self.commit_re.search(output).group(1)
            except AttributeError:
                if not files:
                    return parents[0]
                self.ui.warn(_('unexpected svn output:\n'))
                self.ui.warn(output)
                raise util.Abort(_('unable to cope with svn output'))
            if commit.rev:
                self.run('propset', 'hg:convert-rev', commit.rev,
                         revprop=True, revision=rev)
            if commit.branch and commit.branch != 'default':
                self.run('propset', 'hg:convert-branch', commit.branch,
                         revprop=True, revision=rev)
            for parent in parents:
                self.addchild(parent, rev)
            return self.revid(rev)
        finally:
            os.unlink(messagefile)

    def puttags(self, tags):
        self.ui.warn(_('writing Subversion tags is not yet implemented\n'))
        return None, None

    def hascommit(self, rev):
        # This is not correct as one can convert to an existing subversion
        # repository and childmap would not list all revisions. Too bad.
        if rev in self.childmap:
            return True
        raise util.Abort(_('splice map revision %s not found in subversion '
                           'child map (revision lookups are not implemented)')
                         % rev)
