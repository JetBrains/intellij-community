# localrepo.py - read/write repository class for mercurial
#
# Copyright 2005-2007 Matt Mackall <mpm@selenic.com>
#
# This software may be used and distributed according to the terms of the
# GNU General Public License version 2 or any later version.
from node import hex, nullid, short
from i18n import _
import peer, changegroup, subrepo, discovery, pushkey, obsolete, repoview
import changelog, dirstate, filelog, manifest, context, bookmarks, phases
import lock, transaction, store, encoding
import scmutil, util, extensions, hook, error, revset
import match as matchmod
import merge as mergemod
import tags as tagsmod
from lock import release
import weakref, errno, os, time, inspect
import branchmap
propertycache = util.propertycache
filecache = scmutil.filecache

class repofilecache(filecache):
    """All filecache usage on repo are done for logic that should be unfiltered
    """

    def __get__(self, repo, type=None):
        return super(repofilecache, self).__get__(repo.unfiltered(), type)
    def __set__(self, repo, value):
        return super(repofilecache, self).__set__(repo.unfiltered(), value)
    def __delete__(self, repo):
        return super(repofilecache, self).__delete__(repo.unfiltered())

class storecache(repofilecache):
    """filecache for files in the store"""
    def join(self, obj, fname):
        return obj.sjoin(fname)

class unfilteredpropertycache(propertycache):
    """propertycache that apply to unfiltered repo only"""

    def __get__(self, repo, type=None):
        return super(unfilteredpropertycache, self).__get__(repo.unfiltered())

class filteredpropertycache(propertycache):
    """propertycache that must take filtering in account"""

    def cachevalue(self, obj, value):
        object.__setattr__(obj, self.name, value)


def hasunfilteredcache(repo, name):
    """check if a repo has an unfilteredpropertycache value for <name>"""
    return name in vars(repo.unfiltered())

def unfilteredmethod(orig):
    """decorate method that always need to be run on unfiltered version"""
    def wrapper(repo, *args, **kwargs):
        return orig(repo.unfiltered(), *args, **kwargs)
    return wrapper

MODERNCAPS = set(('lookup', 'branchmap', 'pushkey', 'known', 'getbundle'))
LEGACYCAPS = MODERNCAPS.union(set(['changegroupsubset']))

class localpeer(peer.peerrepository):
    '''peer for a local repo; reflects only the most recent API'''

    def __init__(self, repo, caps=MODERNCAPS):
        peer.peerrepository.__init__(self)
        self._repo = repo.filtered('served')
        self.ui = repo.ui
        self._caps = repo._restrictcapabilities(caps)
        self.requirements = repo.requirements
        self.supportedformats = repo.supportedformats

    def close(self):
        self._repo.close()

    def _capabilities(self):
        return self._caps

    def local(self):
        return self._repo

    def canpush(self):
        return True

    def url(self):
        return self._repo.url()

    def lookup(self, key):
        return self._repo.lookup(key)

    def branchmap(self):
        return self._repo.branchmap()

    def heads(self):
        return self._repo.heads()

    def known(self, nodes):
        return self._repo.known(nodes)

    def getbundle(self, source, heads=None, common=None):
        return self._repo.getbundle(source, heads=heads, common=common)

    # TODO We might want to move the next two calls into legacypeer and add
    # unbundle instead.

    def lock(self):
        return self._repo.lock()

    def addchangegroup(self, cg, source, url):
        return self._repo.addchangegroup(cg, source, url)

    def pushkey(self, namespace, key, old, new):
        return self._repo.pushkey(namespace, key, old, new)

    def listkeys(self, namespace):
        return self._repo.listkeys(namespace)

    def debugwireargs(self, one, two, three=None, four=None, five=None):
        '''used to test argument passing over the wire'''
        return "%s %s %s %s %s" % (one, two, three, four, five)

class locallegacypeer(localpeer):
    '''peer extension which implements legacy methods too; used for tests with
    restricted capabilities'''

    def __init__(self, repo):
        localpeer.__init__(self, repo, caps=LEGACYCAPS)

    def branches(self, nodes):
        return self._repo.branches(nodes)

    def between(self, pairs):
        return self._repo.between(pairs)

    def changegroup(self, basenodes, source):
        return self._repo.changegroup(basenodes, source)

    def changegroupsubset(self, bases, heads, source):
        return self._repo.changegroupsubset(bases, heads, source)

class localrepository(object):

    supportedformats = set(('revlogv1', 'generaldelta'))
    supported = supportedformats | set(('store', 'fncache', 'shared',
                                        'dotencode'))
    openerreqs = set(('revlogv1', 'generaldelta'))
    requirements = ['revlogv1']
    filtername = None

    def _baserequirements(self, create):
        return self.requirements[:]

    def __init__(self, baseui, path=None, create=False):
        self.wvfs = scmutil.vfs(path, expandpath=True, realpath=True)
        self.wopener = self.wvfs
        self.root = self.wvfs.base
        self.path = self.wvfs.join(".hg")
        self.origroot = path
        self.auditor = scmutil.pathauditor(self.root, self._checknested)
        self.vfs = scmutil.vfs(self.path)
        self.opener = self.vfs
        self.baseui = baseui
        self.ui = baseui.copy()
        # A list of callback to shape the phase if no data were found.
        # Callback are in the form: func(repo, roots) --> processed root.
        # This list it to be filled by extension during repo setup
        self._phasedefaults = []
        try:
            self.ui.readconfig(self.join("hgrc"), self.root)
            extensions.loadall(self.ui)
        except IOError:
            pass

        if not self.vfs.isdir():
            if create:
                if not self.wvfs.exists():
                    self.wvfs.makedirs()
                self.vfs.makedir(notindexed=True)
                requirements = self._baserequirements(create)
                if self.ui.configbool('format', 'usestore', True):
                    self.vfs.mkdir("store")
                    requirements.append("store")
                    if self.ui.configbool('format', 'usefncache', True):
                        requirements.append("fncache")
                        if self.ui.configbool('format', 'dotencode', True):
                            requirements.append('dotencode')
                    # create an invalid changelog
                    self.vfs.append(
                        "00changelog.i",
                        '\0\0\0\2' # represents revlogv2
                        ' dummy changelog to prevent using the old repo layout'
                    )
                if self.ui.configbool('format', 'generaldelta', False):
                    requirements.append("generaldelta")
                requirements = set(requirements)
            else:
                raise error.RepoError(_("repository %s not found") % path)
        elif create:
            raise error.RepoError(_("repository %s already exists") % path)
        else:
            try:
                requirements = scmutil.readrequires(self.vfs, self.supported)
            except IOError, inst:
                if inst.errno != errno.ENOENT:
                    raise
                requirements = set()

        self.sharedpath = self.path
        try:
            vfs = scmutil.vfs(self.vfs.read("sharedpath").rstrip('\n'),
                              realpath=True)
            s = vfs.base
            if not vfs.exists():
                raise error.RepoError(
                    _('.hg/sharedpath points to nonexistent directory %s') % s)
            self.sharedpath = s
        except IOError, inst:
            if inst.errno != errno.ENOENT:
                raise

        self.store = store.store(requirements, self.sharedpath, scmutil.vfs)
        self.spath = self.store.path
        self.svfs = self.store.vfs
        self.sopener = self.svfs
        self.sjoin = self.store.join
        self.vfs.createmode = self.store.createmode
        self._applyrequirements(requirements)
        if create:
            self._writerequirements()


        self._branchcaches = {}
        self.filterpats = {}
        self._datafilters = {}
        self._transref = self._lockref = self._wlockref = None

        # A cache for various files under .hg/ that tracks file changes,
        # (used by the filecache decorator)
        #
        # Maps a property name to its util.filecacheentry
        self._filecache = {}

        # hold sets of revision to be filtered
        # should be cleared when something might have changed the filter value:
        # - new changesets,
        # - phase change,
        # - new obsolescence marker,
        # - working directory parent change,
        # - bookmark changes
        self.filteredrevcache = {}

    def close(self):
        pass

    def _restrictcapabilities(self, caps):
        return caps

    def _applyrequirements(self, requirements):
        self.requirements = requirements
        self.sopener.options = dict((r, 1) for r in requirements
                                           if r in self.openerreqs)

    def _writerequirements(self):
        reqfile = self.opener("requires", "w")
        for r in sorted(self.requirements):
            reqfile.write("%s\n" % r)
        reqfile.close()

    def _checknested(self, path):
        """Determine if path is a legal nested repository."""
        if not path.startswith(self.root):
            return False
        subpath = path[len(self.root) + 1:]
        normsubpath = util.pconvert(subpath)

        # XXX: Checking against the current working copy is wrong in
        # the sense that it can reject things like
        #
        #   $ hg cat -r 10 sub/x.txt
        #
        # if sub/ is no longer a subrepository in the working copy
        # parent revision.
        #
        # However, it can of course also allow things that would have
        # been rejected before, such as the above cat command if sub/
        # is a subrepository now, but was a normal directory before.
        # The old path auditor would have rejected by mistake since it
        # panics when it sees sub/.hg/.
        #
        # All in all, checking against the working copy seems sensible
        # since we want to prevent access to nested repositories on
        # the filesystem *now*.
        ctx = self[None]
        parts = util.splitpath(subpath)
        while parts:
            prefix = '/'.join(parts)
            if prefix in ctx.substate:
                if prefix == normsubpath:
                    return True
                else:
                    sub = ctx.sub(prefix)
                    return sub.checknested(subpath[len(prefix) + 1:])
            else:
                parts.pop()
        return False

    def peer(self):
        return localpeer(self) # not cached to avoid reference cycle

    def unfiltered(self):
        """Return unfiltered version of the repository

        Intended to be overwritten by filtered repo."""
        return self

    def filtered(self, name):
        """Return a filtered version of a repository"""
        # build a new class with the mixin and the current class
        # (possibly subclass of the repo)
        class proxycls(repoview.repoview, self.unfiltered().__class__):
            pass
        return proxycls(self, name)

    @repofilecache('bookmarks')
    def _bookmarks(self):
        return bookmarks.bmstore(self)

    @repofilecache('bookmarks.current')
    def _bookmarkcurrent(self):
        return bookmarks.readcurrent(self)

    def bookmarkheads(self, bookmark):
        name = bookmark.split('@', 1)[0]
        heads = []
        for mark, n in self._bookmarks.iteritems():
            if mark.split('@', 1)[0] == name:
                heads.append(n)
        return heads

    @storecache('phaseroots')
    def _phasecache(self):
        return phases.phasecache(self, self._phasedefaults)

    @storecache('obsstore')
    def obsstore(self):
        store = obsolete.obsstore(self.sopener)
        if store and not obsolete._enabled:
            # message is rare enough to not be translated
            msg = 'obsolete feature not enabled but %i markers found!\n'
            self.ui.warn(msg % len(list(store)))
        return store

    @storecache('00changelog.i')
    def changelog(self):
        c = changelog.changelog(self.sopener)
        if 'HG_PENDING' in os.environ:
            p = os.environ['HG_PENDING']
            if p.startswith(self.root):
                c.readpending('00changelog.i.a')
        return c

    @storecache('00manifest.i')
    def manifest(self):
        return manifest.manifest(self.sopener)

    @repofilecache('dirstate')
    def dirstate(self):
        warned = [0]
        def validate(node):
            try:
                self.changelog.rev(node)
                return node
            except error.LookupError:
                if not warned[0]:
                    warned[0] = True
                    self.ui.warn(_("warning: ignoring unknown"
                                   " working parent %s!\n") % short(node))
                return nullid

        return dirstate.dirstate(self.opener, self.ui, self.root, validate)

    def __getitem__(self, changeid):
        if changeid is None:
            return context.workingctx(self)
        return context.changectx(self, changeid)

    def __contains__(self, changeid):
        try:
            return bool(self.lookup(changeid))
        except error.RepoLookupError:
            return False

    def __nonzero__(self):
        return True

    def __len__(self):
        return len(self.changelog)

    def __iter__(self):
        return iter(self.changelog)

    def revs(self, expr, *args):
        '''Return a list of revisions matching the given revset'''
        expr = revset.formatspec(expr, *args)
        m = revset.match(None, expr)
        return [r for r in m(self, list(self))]

    def set(self, expr, *args):
        '''
        Yield a context for each matching revision, after doing arg
        replacement via revset.formatspec
        '''
        for r in self.revs(expr, *args):
            yield self[r]

    def url(self):
        return 'file:' + self.root

    def hook(self, name, throw=False, **args):
        return hook.hook(self.ui, self, name, throw, **args)

    @unfilteredmethod
    def _tag(self, names, node, message, local, user, date, extra={}):
        if isinstance(names, str):
            names = (names,)

        branches = self.branchmap()
        for name in names:
            self.hook('pretag', throw=True, node=hex(node), tag=name,
                      local=local)
            if name in branches:
                self.ui.warn(_("warning: tag %s conflicts with existing"
                " branch name\n") % name)

        def writetags(fp, names, munge, prevtags):
            fp.seek(0, 2)
            if prevtags and prevtags[-1] != '\n':
                fp.write('\n')
            for name in names:
                m = munge and munge(name) or name
                if (self._tagscache.tagtypes and
                    name in self._tagscache.tagtypes):
                    old = self.tags().get(name, nullid)
                    fp.write('%s %s\n' % (hex(old), m))
                fp.write('%s %s\n' % (hex(node), m))
            fp.close()

        prevtags = ''
        if local:
            try:
                fp = self.opener('localtags', 'r+')
            except IOError:
                fp = self.opener('localtags', 'a')
            else:
                prevtags = fp.read()

            # local tags are stored in the current charset
            writetags(fp, names, None, prevtags)
            for name in names:
                self.hook('tag', node=hex(node), tag=name, local=local)
            return

        try:
            fp = self.wfile('.hgtags', 'rb+')
        except IOError, e:
            if e.errno != errno.ENOENT:
                raise
            fp = self.wfile('.hgtags', 'ab')
        else:
            prevtags = fp.read()

        # committed tags are stored in UTF-8
        writetags(fp, names, encoding.fromlocal, prevtags)

        fp.close()

        self.invalidatecaches()

        if '.hgtags' not in self.dirstate:
            self[None].add(['.hgtags'])

        m = matchmod.exact(self.root, '', ['.hgtags'])
        tagnode = self.commit(message, user, date, extra=extra, match=m)

        for name in names:
            self.hook('tag', node=hex(node), tag=name, local=local)

        return tagnode

    def tag(self, names, node, message, local, user, date):
        '''tag a revision with one or more symbolic names.

        names is a list of strings or, when adding a single tag, names may be a
        string.

        if local is True, the tags are stored in a per-repository file.
        otherwise, they are stored in the .hgtags file, and a new
        changeset is committed with the change.

        keyword arguments:

        local: whether to store tags in non-version-controlled file
        (default False)

        message: commit message to use if committing

        user: name of user to use if committing

        date: date tuple to use if committing'''

        if not local:
            for x in self.status()[:5]:
                if '.hgtags' in x:
                    raise util.Abort(_('working copy of .hgtags is changed '
                                       '(please commit .hgtags manually)'))

        self.tags() # instantiate the cache
        self._tag(names, node, message, local, user, date)

    @filteredpropertycache
    def _tagscache(self):
        '''Returns a tagscache object that contains various tags related
        caches.'''

        # This simplifies its cache management by having one decorated
        # function (this one) and the rest simply fetch things from it.
        class tagscache(object):
            def __init__(self):
                # These two define the set of tags for this repository. tags
                # maps tag name to node; tagtypes maps tag name to 'global' or
                # 'local'. (Global tags are defined by .hgtags across all
                # heads, and local tags are defined in .hg/localtags.)
                # They constitute the in-memory cache of tags.
                self.tags = self.tagtypes = None

                self.nodetagscache = self.tagslist = None

        cache = tagscache()
        cache.tags, cache.tagtypes = self._findtags()

        return cache

    def tags(self):
        '''return a mapping of tag to node'''
        t = {}
        if self.changelog.filteredrevs:
            tags, tt = self._findtags()
        else:
            tags = self._tagscache.tags
        for k, v in tags.iteritems():
            try:
                # ignore tags to unknown nodes
                self.changelog.rev(v)
                t[k] = v
            except (error.LookupError, ValueError):
                pass
        return t

    def _findtags(self):
        '''Do the hard work of finding tags.  Return a pair of dicts
        (tags, tagtypes) where tags maps tag name to node, and tagtypes
        maps tag name to a string like \'global\' or \'local\'.
        Subclasses or extensions are free to add their own tags, but
        should be aware that the returned dicts will be retained for the
        duration of the localrepo object.'''

        # XXX what tagtype should subclasses/extensions use?  Currently
        # mq and bookmarks add tags, but do not set the tagtype at all.
        # Should each extension invent its own tag type?  Should there
        # be one tagtype for all such "virtual" tags?  Or is the status
        # quo fine?

        alltags = {}                    # map tag name to (node, hist)
        tagtypes = {}

        tagsmod.findglobaltags(self.ui, self, alltags, tagtypes)
        tagsmod.readlocaltags(self.ui, self, alltags, tagtypes)

        # Build the return dicts.  Have to re-encode tag names because
        # the tags module always uses UTF-8 (in order not to lose info
        # writing to the cache), but the rest of Mercurial wants them in
        # local encoding.
        tags = {}
        for (name, (node, hist)) in alltags.iteritems():
            if node != nullid:
                tags[encoding.tolocal(name)] = node
        tags['tip'] = self.changelog.tip()
        tagtypes = dict([(encoding.tolocal(name), value)
                         for (name, value) in tagtypes.iteritems()])
        return (tags, tagtypes)

    def tagtype(self, tagname):
        '''
        return the type of the given tag. result can be:

        'local'  : a local tag
        'global' : a global tag
        None     : tag does not exist
        '''

        return self._tagscache.tagtypes.get(tagname)

    def tagslist(self):
        '''return a list of tags ordered by revision'''
        if not self._tagscache.tagslist:
            l = []
            for t, n in self.tags().iteritems():
                r = self.changelog.rev(n)
                l.append((r, t, n))
            self._tagscache.tagslist = [(t, n) for r, t, n in sorted(l)]

        return self._tagscache.tagslist

    def nodetags(self, node):
        '''return the tags associated with a node'''
        if not self._tagscache.nodetagscache:
            nodetagscache = {}
            for t, n in self._tagscache.tags.iteritems():
                nodetagscache.setdefault(n, []).append(t)
            for tags in nodetagscache.itervalues():
                tags.sort()
            self._tagscache.nodetagscache = nodetagscache
        return self._tagscache.nodetagscache.get(node, [])

    def nodebookmarks(self, node):
        marks = []
        for bookmark, n in self._bookmarks.iteritems():
            if n == node:
                marks.append(bookmark)
        return sorted(marks)

    def branchmap(self):
        '''returns a dictionary {branch: [branchheads]}'''
        branchmap.updatecache(self)
        return self._branchcaches[self.filtername]


    def _branchtip(self, heads):
        '''return the tipmost branch head in heads'''
        tip = heads[-1]
        for h in reversed(heads):
            if not self[h].closesbranch():
                tip = h
                break
        return tip

    def branchtip(self, branch):
        '''return the tip node for a given branch'''
        if branch not in self.branchmap():
            raise error.RepoLookupError(_("unknown branch '%s'") % branch)
        return self._branchtip(self.branchmap()[branch])

    def branchtags(self):
        '''return a dict where branch names map to the tipmost head of
        the branch, open heads come before closed'''
        bt = {}
        for bn, heads in self.branchmap().iteritems():
            bt[bn] = self._branchtip(heads)
        return bt

    def lookup(self, key):
        return self[key].node()

    def lookupbranch(self, key, remote=None):
        repo = remote or self
        if key in repo.branchmap():
            return key

        repo = (remote and remote.local()) and remote or self
        return repo[key].branch()

    def known(self, nodes):
        nm = self.changelog.nodemap
        pc = self._phasecache
        result = []
        for n in nodes:
            r = nm.get(n)
            resp = not (r is None or pc.phase(self, r) >= phases.secret)
            result.append(resp)
        return result

    def local(self):
        return self

    def cancopy(self):
        return self.local() # so statichttprepo's override of local() works

    def join(self, f):
        return os.path.join(self.path, f)

    def wjoin(self, f):
        return os.path.join(self.root, f)

    def file(self, f):
        if f[0] == '/':
            f = f[1:]
        return filelog.filelog(self.sopener, f)

    def changectx(self, changeid):
        return self[changeid]

    def parents(self, changeid=None):
        '''get list of changectxs for parents of changeid'''
        return self[changeid].parents()

    def setparents(self, p1, p2=nullid):
        copies = self.dirstate.setparents(p1, p2)
        pctx = self[p1]
        if copies:
            # Adjust copy records, the dirstate cannot do it, it
            # requires access to parents manifests. Preserve them
            # only for entries added to first parent.
            for f in copies:
                if f not in pctx and copies[f] in pctx:
                    self.dirstate.copy(copies[f], f)
        if p2 == nullid:
            for f, s in sorted(self.dirstate.copies().items()):
                if f not in pctx and s not in pctx:
                    self.dirstate.copy(None, f)

    def filectx(self, path, changeid=None, fileid=None):
        """changeid can be a changeset revision, node, or tag.
           fileid can be a file revision or node."""
        return context.filectx(self, path, changeid, fileid)

    def getcwd(self):
        return self.dirstate.getcwd()

    def pathto(self, f, cwd=None):
        return self.dirstate.pathto(f, cwd)

    def wfile(self, f, mode='r'):
        return self.wopener(f, mode)

    def _link(self, f):
        return self.wvfs.islink(f)

    def _loadfilter(self, filter):
        if filter not in self.filterpats:
            l = []
            for pat, cmd in self.ui.configitems(filter):
                if cmd == '!':
                    continue
                mf = matchmod.match(self.root, '', [pat])
                fn = None
                params = cmd
                for name, filterfn in self._datafilters.iteritems():
                    if cmd.startswith(name):
                        fn = filterfn
                        params = cmd[len(name):].lstrip()
                        break
                if not fn:
                    fn = lambda s, c, **kwargs: util.filter(s, c)
                # Wrap old filters not supporting keyword arguments
                if not inspect.getargspec(fn)[2]:
                    oldfn = fn
                    fn = lambda s, c, **kwargs: oldfn(s, c)
                l.append((mf, fn, params))
            self.filterpats[filter] = l
        return self.filterpats[filter]

    def _filter(self, filterpats, filename, data):
        for mf, fn, cmd in filterpats:
            if mf(filename):
                self.ui.debug("filtering %s through %s\n" % (filename, cmd))
                data = fn(data, cmd, ui=self.ui, repo=self, filename=filename)
                break

        return data

    @unfilteredpropertycache
    def _encodefilterpats(self):
        return self._loadfilter('encode')

    @unfilteredpropertycache
    def _decodefilterpats(self):
        return self._loadfilter('decode')

    def adddatafilter(self, name, filter):
        self._datafilters[name] = filter

    def wread(self, filename):
        if self._link(filename):
            data = self.wvfs.readlink(filename)
        else:
            data = self.wopener.read(filename)
        return self._filter(self._encodefilterpats, filename, data)

    def wwrite(self, filename, data, flags):
        data = self._filter(self._decodefilterpats, filename, data)
        if 'l' in flags:
            self.wopener.symlink(data, filename)
        else:
            self.wopener.write(filename, data)
            if 'x' in flags:
                self.wvfs.setflags(filename, False, True)

    def wwritedata(self, filename, data):
        return self._filter(self._decodefilterpats, filename, data)

    def transaction(self, desc):
        tr = self._transref and self._transref() or None
        if tr and tr.running():
            return tr.nest()

        # abort here if the journal already exists
        if self.svfs.exists("journal"):
            raise error.RepoError(
                _("abandoned transaction found - run hg recover"))

        self._writejournal(desc)
        renames = [(vfs, x, undoname(x)) for vfs, x in self._journalfiles()]

        tr = transaction.transaction(self.ui.warn, self.sopener,
                                     self.sjoin("journal"),
                                     aftertrans(renames),
                                     self.store.createmode)
        self._transref = weakref.ref(tr)
        return tr

    def _journalfiles(self):
        return ((self.svfs, 'journal'),
                (self.vfs, 'journal.dirstate'),
                (self.vfs, 'journal.branch'),
                (self.vfs, 'journal.desc'),
                (self.vfs, 'journal.bookmarks'),
                (self.svfs, 'journal.phaseroots'))

    def undofiles(self):
        return [vfs.join(undoname(x)) for vfs, x in self._journalfiles()]

    def _writejournal(self, desc):
        self.opener.write("journal.dirstate",
                          self.opener.tryread("dirstate"))
        self.opener.write("journal.branch",
                          encoding.fromlocal(self.dirstate.branch()))
        self.opener.write("journal.desc",
                          "%d\n%s\n" % (len(self), desc))
        self.opener.write("journal.bookmarks",
                          self.opener.tryread("bookmarks"))
        self.sopener.write("journal.phaseroots",
                           self.sopener.tryread("phaseroots"))

    def recover(self):
        lock = self.lock()
        try:
            if self.svfs.exists("journal"):
                self.ui.status(_("rolling back interrupted transaction\n"))
                transaction.rollback(self.sopener, self.sjoin("journal"),
                                     self.ui.warn)
                self.invalidate()
                return True
            else:
                self.ui.warn(_("no interrupted transaction available\n"))
                return False
        finally:
            lock.release()

    def rollback(self, dryrun=False, force=False):
        wlock = lock = None
        try:
            wlock = self.wlock()
            lock = self.lock()
            if self.svfs.exists("undo"):
                return self._rollback(dryrun, force)
            else:
                self.ui.warn(_("no rollback information available\n"))
                return 1
        finally:
            release(lock, wlock)

    @unfilteredmethod # Until we get smarter cache management
    def _rollback(self, dryrun, force):
        ui = self.ui
        try:
            args = self.opener.read('undo.desc').splitlines()
            (oldlen, desc, detail) = (int(args[0]), args[1], None)
            if len(args) >= 3:
                detail = args[2]
            oldtip = oldlen - 1

            if detail and ui.verbose:
                msg = (_('repository tip rolled back to revision %s'
                         ' (undo %s: %s)\n')
                       % (oldtip, desc, detail))
            else:
                msg = (_('repository tip rolled back to revision %s'
                         ' (undo %s)\n')
                       % (oldtip, desc))
        except IOError:
            msg = _('rolling back unknown transaction\n')
            desc = None

        if not force and self['.'] != self['tip'] and desc == 'commit':
            raise util.Abort(
                _('rollback of last commit while not checked out '
                  'may lose data'), hint=_('use -f to force'))

        ui.status(msg)
        if dryrun:
            return 0

        parents = self.dirstate.parents()
        self.destroying()
        transaction.rollback(self.sopener, self.sjoin('undo'), ui.warn)
        if self.vfs.exists('undo.bookmarks'):
            self.vfs.rename('undo.bookmarks', 'bookmarks')
        if self.svfs.exists('undo.phaseroots'):
            self.svfs.rename('undo.phaseroots', 'phaseroots')
        self.invalidate()

        parentgone = (parents[0] not in self.changelog.nodemap or
                      parents[1] not in self.changelog.nodemap)
        if parentgone:
            self.vfs.rename('undo.dirstate', 'dirstate')
            try:
                branch = self.opener.read('undo.branch')
                self.dirstate.setbranch(encoding.tolocal(branch))
            except IOError:
                ui.warn(_('named branch could not be reset: '
                          'current branch is still \'%s\'\n')
                        % self.dirstate.branch())

            self.dirstate.invalidate()
            parents = tuple([p.rev() for p in self.parents()])
            if len(parents) > 1:
                ui.status(_('working directory now based on '
                            'revisions %d and %d\n') % parents)
            else:
                ui.status(_('working directory now based on '
                            'revision %d\n') % parents)
        # TODO: if we know which new heads may result from this rollback, pass
        # them to destroy(), which will prevent the branchhead cache from being
        # invalidated.
        self.destroyed()
        return 0

    def invalidatecaches(self):

        if '_tagscache' in vars(self):
            # can't use delattr on proxy
            del self.__dict__['_tagscache']

        self.unfiltered()._branchcaches.clear()
        self.invalidatevolatilesets()

    def invalidatevolatilesets(self):
        self.filteredrevcache.clear()
        obsolete.clearobscaches(self)

    def invalidatedirstate(self):
        '''Invalidates the dirstate, causing the next call to dirstate
        to check if it was modified since the last time it was read,
        rereading it if it has.

        This is different to dirstate.invalidate() that it doesn't always
        rereads the dirstate. Use dirstate.invalidate() if you want to
        explicitly read the dirstate again (i.e. restoring it to a previous
        known good state).'''
        if hasunfilteredcache(self, 'dirstate'):
            for k in self.dirstate._filecache:
                try:
                    delattr(self.dirstate, k)
                except AttributeError:
                    pass
            delattr(self.unfiltered(), 'dirstate')

    def invalidate(self):
        unfiltered = self.unfiltered() # all file caches are stored unfiltered
        for k in self._filecache:
            # dirstate is invalidated separately in invalidatedirstate()
            if k == 'dirstate':
                continue

            try:
                delattr(unfiltered, k)
            except AttributeError:
                pass
        self.invalidatecaches()

    def _lock(self, lockname, wait, releasefn, acquirefn, desc):
        try:
            l = lock.lock(lockname, 0, releasefn, desc=desc)
        except error.LockHeld, inst:
            if not wait:
                raise
            self.ui.warn(_("waiting for lock on %s held by %r\n") %
                         (desc, inst.locker))
            # default to 600 seconds timeout
            l = lock.lock(lockname, int(self.ui.config("ui", "timeout", "600")),
                          releasefn, desc=desc)
        if acquirefn:
            acquirefn()
        return l

    def _afterlock(self, callback):
        """add a callback to the current repository lock.

        The callback will be executed on lock release."""
        l = self._lockref and self._lockref()
        if l:
            l.postrelease.append(callback)
        else:
            callback()

    def lock(self, wait=True):
        '''Lock the repository store (.hg/store) and return a weak reference
        to the lock. Use this before modifying the store (e.g. committing or
        stripping). If you are opening a transaction, get a lock as well.)'''
        l = self._lockref and self._lockref()
        if l is not None and l.held:
            l.lock()
            return l

        def unlock():
            self.store.write()
            if hasunfilteredcache(self, '_phasecache'):
                self._phasecache.write()
            for k, ce in self._filecache.items():
                if k == 'dirstate' or k not in self.__dict__:
                    continue
                ce.refresh()

        l = self._lock(self.sjoin("lock"), wait, unlock,
                       self.invalidate, _('repository %s') % self.origroot)
        self._lockref = weakref.ref(l)
        return l

    def wlock(self, wait=True):
        '''Lock the non-store parts of the repository (everything under
        .hg except .hg/store) and return a weak reference to the lock.
        Use this before modifying files in .hg.'''
        l = self._wlockref and self._wlockref()
        if l is not None and l.held:
            l.lock()
            return l

        def unlock():
            self.dirstate.write()
            self._filecache['dirstate'].refresh()

        l = self._lock(self.join("wlock"), wait, unlock,
                       self.invalidatedirstate, _('working directory of %s') %
                       self.origroot)
        self._wlockref = weakref.ref(l)
        return l

    def _filecommit(self, fctx, manifest1, manifest2, linkrev, tr, changelist):
        """
        commit an individual file as part of a larger transaction
        """

        fname = fctx.path()
        text = fctx.data()
        flog = self.file(fname)
        fparent1 = manifest1.get(fname, nullid)
        fparent2 = fparent2o = manifest2.get(fname, nullid)

        meta = {}
        copy = fctx.renamed()
        if copy and copy[0] != fname:
            # Mark the new revision of this file as a copy of another
            # file.  This copy data will effectively act as a parent
            # of this new revision.  If this is a merge, the first
            # parent will be the nullid (meaning "look up the copy data")
            # and the second one will be the other parent.  For example:
            #
            # 0 --- 1 --- 3   rev1 changes file foo
            #   \       /     rev2 renames foo to bar and changes it
            #    \- 2 -/      rev3 should have bar with all changes and
            #                      should record that bar descends from
            #                      bar in rev2 and foo in rev1
            #
            # this allows this merge to succeed:
            #
            # 0 --- 1 --- 3   rev4 reverts the content change from rev2
            #   \       /     merging rev3 and rev4 should use bar@rev2
            #    \- 2 --- 4        as the merge base
            #

            cfname = copy[0]
            crev = manifest1.get(cfname)
            newfparent = fparent2

            if manifest2: # branch merge
                if fparent2 == nullid or crev is None: # copied on remote side
                    if cfname in manifest2:
                        crev = manifest2[cfname]
                        newfparent = fparent1

            # find source in nearest ancestor if we've lost track
            if not crev:
                self.ui.debug(" %s: searching for copy revision for %s\n" %
                              (fname, cfname))
                for ancestor in self[None].ancestors():
                    if cfname in ancestor:
                        crev = ancestor[cfname].filenode()
                        break

            if crev:
                self.ui.debug(" %s: copy %s:%s\n" % (fname, cfname, hex(crev)))
                meta["copy"] = cfname
                meta["copyrev"] = hex(crev)
                fparent1, fparent2 = nullid, newfparent
            else:
                self.ui.warn(_("warning: can't find ancestor for '%s' "
                               "copied from '%s'!\n") % (fname, cfname))

        elif fparent2 != nullid:
            # is one parent an ancestor of the other?
            fparentancestor = flog.ancestor(fparent1, fparent2)
            if fparentancestor == fparent1:
                fparent1, fparent2 = fparent2, nullid
            elif fparentancestor == fparent2:
                fparent2 = nullid

        # is the file changed?
        if fparent2 != nullid or flog.cmp(fparent1, text) or meta:
            changelist.append(fname)
            return flog.add(text, meta, tr, linkrev, fparent1, fparent2)

        # are just the flags changed during merge?
        if fparent1 != fparent2o and manifest1.flags(fname) != fctx.flags():
            changelist.append(fname)

        return fparent1

    @unfilteredmethod
    def commit(self, text="", user=None, date=None, match=None, force=False,
               editor=False, extra={}):
        """Add a new revision to current repository.

        Revision information is gathered from the working directory,
        match can be used to filter the committed files. If editor is
        supplied, it is called to get a commit message.
        """

        def fail(f, msg):
            raise util.Abort('%s: %s' % (f, msg))

        if not match:
            match = matchmod.always(self.root, '')

        if not force:
            vdirs = []
            match.dir = vdirs.append
            match.bad = fail

        wlock = self.wlock()
        try:
            wctx = self[None]
            merge = len(wctx.parents()) > 1

            if (not force and merge and match and
                (match.files() or match.anypats())):
                raise util.Abort(_('cannot partially commit a merge '
                                   '(do not specify files or patterns)'))

            changes = self.status(match=match, clean=force)
            if force:
                changes[0].extend(changes[6]) # mq may commit unchanged files

            # check subrepos
            subs = []
            commitsubs = set()
            newstate = wctx.substate.copy()
            # only manage subrepos and .hgsubstate if .hgsub is present
            if '.hgsub' in wctx:
                # we'll decide whether to track this ourselves, thanks
                if '.hgsubstate' in changes[0]:
                    changes[0].remove('.hgsubstate')
                if '.hgsubstate' in changes[2]:
                    changes[2].remove('.hgsubstate')

                # compare current state to last committed state
                # build new substate based on last committed state
                oldstate = wctx.p1().substate
                for s in sorted(newstate.keys()):
                    if not match(s):
                        # ignore working copy, use old state if present
                        if s in oldstate:
                            newstate[s] = oldstate[s]
                            continue
                        if not force:
                            raise util.Abort(
                                _("commit with new subrepo %s excluded") % s)
                    if wctx.sub(s).dirty(True):
                        if not self.ui.configbool('ui', 'commitsubrepos'):
                            raise util.Abort(
                                _("uncommitted changes in subrepo %s") % s,
                                hint=_("use --subrepos for recursive commit"))
                        subs.append(s)
                        commitsubs.add(s)
                    else:
                        bs = wctx.sub(s).basestate()
                        newstate[s] = (newstate[s][0], bs, newstate[s][2])
                        if oldstate.get(s, (None, None, None))[1] != bs:
                            subs.append(s)

                # check for removed subrepos
                for p in wctx.parents():
                    r = [s for s in p.substate if s not in newstate]
                    subs += [s for s in r if match(s)]
                if subs:
                    if (not match('.hgsub') and
                        '.hgsub' in (wctx.modified() + wctx.added())):
                        raise util.Abort(
                            _("can't commit subrepos without .hgsub"))
                    changes[0].insert(0, '.hgsubstate')

            elif '.hgsub' in changes[2]:
                # clean up .hgsubstate when .hgsub is removed
                if ('.hgsubstate' in wctx and
                    '.hgsubstate' not in changes[0] + changes[1] + changes[2]):
                    changes[2].insert(0, '.hgsubstate')

            # make sure all explicit patterns are matched
            if not force and match.files():
                matched = set(changes[0] + changes[1] + changes[2])

                for f in match.files():
                    f = self.dirstate.normalize(f)
                    if f == '.' or f in matched or f in wctx.substate:
                        continue
                    if f in changes[3]: # missing
                        fail(f, _('file not found!'))
                    if f in vdirs: # visited directory
                        d = f + '/'
                        for mf in matched:
                            if mf.startswith(d):
                                break
                        else:
                            fail(f, _("no match under directory!"))
                    elif f not in self.dirstate:
                        fail(f, _("file not tracked!"))

            cctx = context.workingctx(self, text, user, date, extra, changes)

            if (not force and not extra.get("close") and not merge
                and not cctx.files()
                and wctx.branch() == wctx.p1().branch()):
                return None

            if merge and cctx.deleted():
                raise util.Abort(_("cannot commit merge with missing files"))

            ms = mergemod.mergestate(self)
            for f in changes[0]:
                if f in ms and ms[f] == 'u':
                    raise util.Abort(_("unresolved merge conflicts "
                                       "(see hg help resolve)"))

            if editor:
                cctx._text = editor(self, cctx, subs)
            edited = (text != cctx._text)

            # commit subs and write new state
            if subs:
                for s in sorted(commitsubs):
                    sub = wctx.sub(s)
                    self.ui.status(_('committing subrepository %s\n') %
                        subrepo.subrelpath(sub))
                    sr = sub.commit(cctx._text, user, date)
                    newstate[s] = (newstate[s][0], sr)
                subrepo.writestate(self, newstate)

            # Save commit message in case this transaction gets rolled back
            # (e.g. by a pretxncommit hook).  Leave the content alone on
            # the assumption that the user will use the same editor again.
            msgfn = self.savecommitmessage(cctx._text)

            p1, p2 = self.dirstate.parents()
            hookp1, hookp2 = hex(p1), (p2 != nullid and hex(p2) or '')
            try:
                self.hook("precommit", throw=True, parent1=hookp1,
                          parent2=hookp2)
                ret = self.commitctx(cctx, True)
            except: # re-raises
                if edited:
                    self.ui.write(
                        _('note: commit message saved in %s\n') % msgfn)
                raise

            # update bookmarks, dirstate and mergestate
            bookmarks.update(self, [p1, p2], ret)
            cctx.markcommitted(ret)
            ms.reset()
        finally:
            wlock.release()

        def commithook(node=hex(ret), parent1=hookp1, parent2=hookp2):
            self.hook("commit", node=node, parent1=parent1, parent2=parent2)
        self._afterlock(commithook)
        return ret

    @unfilteredmethod
    def commitctx(self, ctx, error=False):
        """Add a new revision to current repository.
        Revision information is passed via the context argument.
        """

        tr = lock = None
        removed = list(ctx.removed())
        p1, p2 = ctx.p1(), ctx.p2()
        user = ctx.user()

        lock = self.lock()
        try:
            tr = self.transaction("commit")
            trp = weakref.proxy(tr)

            if ctx.files():
                m1 = p1.manifest().copy()
                m2 = p2.manifest()

                # check in files
                new = {}
                changed = []
                linkrev = len(self)
                for f in sorted(ctx.modified() + ctx.added()):
                    self.ui.note(f + "\n")
                    try:
                        fctx = ctx[f]
                        new[f] = self._filecommit(fctx, m1, m2, linkrev, trp,
                                                  changed)
                        m1.set(f, fctx.flags())
                    except OSError, inst:
                        self.ui.warn(_("trouble committing %s!\n") % f)
                        raise
                    except IOError, inst:
                        errcode = getattr(inst, 'errno', errno.ENOENT)
                        if error or errcode and errcode != errno.ENOENT:
                            self.ui.warn(_("trouble committing %s!\n") % f)
                            raise
                        else:
                            removed.append(f)

                # update manifest
                m1.update(new)
                removed = [f for f in sorted(removed) if f in m1 or f in m2]
                drop = [f for f in removed if f in m1]
                for f in drop:
                    del m1[f]
                mn = self.manifest.add(m1, trp, linkrev, p1.manifestnode(),
                                       p2.manifestnode(), (new, drop))
                files = changed + removed
            else:
                mn = p1.manifestnode()
                files = []

            # update changelog
            self.changelog.delayupdate()
            n = self.changelog.add(mn, files, ctx.description(),
                                   trp, p1.node(), p2.node(),
                                   user, ctx.date(), ctx.extra().copy())
            p = lambda: self.changelog.writepending() and self.root or ""
            xp1, xp2 = p1.hex(), p2 and p2.hex() or ''
            self.hook('pretxncommit', throw=True, node=hex(n), parent1=xp1,
                      parent2=xp2, pending=p)
            self.changelog.finalize(trp)
            # set the new commit is proper phase
            targetphase = phases.newcommitphase(self.ui)
            if targetphase:
                # retract boundary do not alter parent changeset.
                # if a parent have higher the resulting phase will
                # be compliant anyway
                #
                # if minimal phase was 0 we don't need to retract anything
                phases.retractboundary(self, targetphase, [n])
            tr.close()
            branchmap.updatecache(self.filtered('served'))
            return n
        finally:
            if tr:
                tr.release()
            lock.release()

    @unfilteredmethod
    def destroying(self):
        '''Inform the repository that nodes are about to be destroyed.
        Intended for use by strip and rollback, so there's a common
        place for anything that has to be done before destroying history.

        This is mostly useful for saving state that is in memory and waiting
        to be flushed when the current lock is released. Because a call to
        destroyed is imminent, the repo will be invalidated causing those
        changes to stay in memory (waiting for the next unlock), or vanish
        completely.
        '''
        # When using the same lock to commit and strip, the phasecache is left
        # dirty after committing. Then when we strip, the repo is invalidated,
        # causing those changes to disappear.
        if '_phasecache' in vars(self):
            self._phasecache.write()

    @unfilteredmethod
    def destroyed(self):
        '''Inform the repository that nodes have been destroyed.
        Intended for use by strip and rollback, so there's a common
        place for anything that has to be done after destroying history.
        '''
        # When one tries to:
        # 1) destroy nodes thus calling this method (e.g. strip)
        # 2) use phasecache somewhere (e.g. commit)
        #
        # then 2) will fail because the phasecache contains nodes that were
        # removed. We can either remove phasecache from the filecache,
        # causing it to reload next time it is accessed, or simply filter
        # the removed nodes now and write the updated cache.
        self._phasecache.filterunknown(self)
        self._phasecache.write()

        # update the 'served' branch cache to help read only server process
        # Thanks to branchcache collaboration this is done from the nearest
        # filtered subset and it is expected to be fast.
        branchmap.updatecache(self.filtered('served'))

        # Ensure the persistent tag cache is updated.  Doing it now
        # means that the tag cache only has to worry about destroyed
        # heads immediately after a strip/rollback.  That in turn
        # guarantees that "cachetip == currenttip" (comparing both rev
        # and node) always means no nodes have been added or destroyed.

        # XXX this is suboptimal when qrefresh'ing: we strip the current
        # head, refresh the tag cache, then immediately add a new head.
        # But I think doing it this way is necessary for the "instant
        # tag cache retrieval" case to work.
        self.invalidate()

    def walk(self, match, node=None):
        '''
        walk recursively through the directory tree or a given
        changeset, finding all files matched by the match
        function
        '''
        return self[node].walk(match)

    def status(self, node1='.', node2=None, match=None,
               ignored=False, clean=False, unknown=False,
               listsubrepos=False):
        """return status of files between two nodes or node and working
        directory.

        If node1 is None, use the first dirstate parent instead.
        If node2 is None, compare node1 with working directory.
        """

        def mfmatches(ctx):
            mf = ctx.manifest().copy()
            if match.always():
                return mf
            for fn in mf.keys():
                if not match(fn):
                    del mf[fn]
            return mf

        if isinstance(node1, context.changectx):
            ctx1 = node1
        else:
            ctx1 = self[node1]
        if isinstance(node2, context.changectx):
            ctx2 = node2
        else:
            ctx2 = self[node2]

        working = ctx2.rev() is None
        parentworking = working and ctx1 == self['.']
        match = match or matchmod.always(self.root, self.getcwd())
        listignored, listclean, listunknown = ignored, clean, unknown

        # load earliest manifest first for caching reasons
        if not working and ctx2.rev() < ctx1.rev():
            ctx2.manifest()

        if not parentworking:
            def bad(f, msg):
                # 'f' may be a directory pattern from 'match.files()',
                # so 'f not in ctx1' is not enough
                if f not in ctx1 and f not in ctx1.dirs():
                    self.ui.warn('%s: %s\n' % (self.dirstate.pathto(f), msg))
            match.bad = bad

        if working: # we need to scan the working dir
            subrepos = []
            if '.hgsub' in self.dirstate:
                subrepos = sorted(ctx2.substate)
            s = self.dirstate.status(match, subrepos, listignored,
                                     listclean, listunknown)
            cmp, modified, added, removed, deleted, unknown, ignored, clean = s

            # check for any possibly clean files
            if parentworking and cmp:
                fixup = []
                # do a full compare of any files that might have changed
                for f in sorted(cmp):
                    if (f not in ctx1 or ctx2.flags(f) != ctx1.flags(f)
                        or ctx1[f].cmp(ctx2[f])):
                        modified.append(f)
                    else:
                        fixup.append(f)

                # update dirstate for files that are actually clean
                if fixup:
                    if listclean:
                        clean += fixup

                    try:
                        # updating the dirstate is optional
                        # so we don't wait on the lock
                        wlock = self.wlock(False)
                        try:
                            for f in fixup:
                                self.dirstate.normal(f)
                        finally:
                            wlock.release()
                    except error.LockError:
                        pass

        if not parentworking:
            mf1 = mfmatches(ctx1)
            if working:
                # we are comparing working dir against non-parent
                # generate a pseudo-manifest for the working dir
                mf2 = mfmatches(self['.'])
                for f in cmp + modified + added:
                    mf2[f] = None
                    mf2.set(f, ctx2.flags(f))
                for f in removed:
                    if f in mf2:
                        del mf2[f]
            else:
                # we are comparing two revisions
                deleted, unknown, ignored = [], [], []
                mf2 = mfmatches(ctx2)

            modified, added, clean = [], [], []
            withflags = mf1.withflags() | mf2.withflags()
            for fn, mf2node in mf2.iteritems():
                if fn in mf1:
                    if (fn not in deleted and
                        ((fn in withflags and mf1.flags(fn) != mf2.flags(fn)) or
                         (mf1[fn] != mf2node and
                          (mf2node or ctx1[fn].cmp(ctx2[fn]))))):
                        modified.append(fn)
                    elif listclean:
                        clean.append(fn)
                    del mf1[fn]
                elif fn not in deleted:
                    added.append(fn)
            removed = mf1.keys()

        if working and modified and not self.dirstate._checklink:
            # Symlink placeholders may get non-symlink-like contents
            # via user error or dereferencing by NFS or Samba servers,
            # so we filter out any placeholders that don't look like a
            # symlink
            sane = []
            for f in modified:
                if ctx2.flags(f) == 'l':
                    d = ctx2[f].data()
                    if len(d) >= 1024 or '\n' in d or util.binary(d):
                        self.ui.debug('ignoring suspect symlink placeholder'
                                      ' "%s"\n' % f)
                        continue
                sane.append(f)
            modified = sane

        r = modified, added, removed, deleted, unknown, ignored, clean

        if listsubrepos:
            for subpath, sub in subrepo.itersubrepos(ctx1, ctx2):
                if working:
                    rev2 = None
                else:
                    rev2 = ctx2.substate[subpath][1]
                try:
                    submatch = matchmod.narrowmatcher(subpath, match)
                    s = sub.status(rev2, match=submatch, ignored=listignored,
                                   clean=listclean, unknown=listunknown,
                                   listsubrepos=True)
                    for rfiles, sfiles in zip(r, s):
                        rfiles.extend("%s/%s" % (subpath, f) for f in sfiles)
                except error.LookupError:
                    self.ui.status(_("skipping missing subrepository: %s\n")
                                   % subpath)

        for l in r:
            l.sort()
        return r

    def heads(self, start=None):
        heads = self.changelog.heads(start)
        # sort the output in rev descending order
        return sorted(heads, key=self.changelog.rev, reverse=True)

    def branchheads(self, branch=None, start=None, closed=False):
        '''return a (possibly filtered) list of heads for the given branch

        Heads are returned in topological order, from newest to oldest.
        If branch is None, use the dirstate branch.
        If start is not None, return only heads reachable from start.
        If closed is True, return heads that are marked as closed as well.
        '''
        if branch is None:
            branch = self[None].branch()
        branches = self.branchmap()
        if branch not in branches:
            return []
        # the cache returns heads ordered lowest to highest
        bheads = list(reversed(branches[branch]))
        if start is not None:
            # filter out the heads that cannot be reached from startrev
            fbheads = set(self.changelog.nodesbetween([start], bheads)[2])
            bheads = [h for h in bheads if h in fbheads]
        if not closed:
            bheads = [h for h in bheads if not self[h].closesbranch()]
        return bheads

    def branches(self, nodes):
        if not nodes:
            nodes = [self.changelog.tip()]
        b = []
        for n in nodes:
            t = n
            while True:
                p = self.changelog.parents(n)
                if p[1] != nullid or p[0] == nullid:
                    b.append((t, n, p[0], p[1]))
                    break
                n = p[0]
        return b

    def between(self, pairs):
        r = []

        for top, bottom in pairs:
            n, l, i = top, [], 0
            f = 1

            while n != bottom and n != nullid:
                p = self.changelog.parents(n)[0]
                if i == f:
                    l.append(n)
                    f = f * 2
                n = p
                i += 1

            r.append(l)

        return r

    def pull(self, remote, heads=None, force=False):
        # don't open transaction for nothing or you break future useful
        # rollback call
        tr = None
        trname = 'pull\n' + util.hidepassword(remote.url())
        lock = self.lock()
        try:
            tmp = discovery.findcommonincoming(self, remote, heads=heads,
                                               force=force)
            common, fetch, rheads = tmp
            if not fetch:
                self.ui.status(_("no changes found\n"))
                added = []
                result = 0
            else:
                tr = self.transaction(trname)
                if heads is None and list(common) == [nullid]:
                    self.ui.status(_("requesting all changes\n"))
                elif heads is None and remote.capable('changegroupsubset'):
                    # issue1320, avoid a race if remote changed after discovery
                    heads = rheads

                if remote.capable('getbundle'):
                    cg = remote.getbundle('pull', common=common,
                                          heads=heads or rheads)
                elif heads is None:
                    cg = remote.changegroup(fetch, 'pull')
                elif not remote.capable('changegroupsubset'):
                    raise util.Abort(_("partial pull cannot be done because "
                                           "other repository doesn't support "
                                           "changegroupsubset."))
                else:
                    cg = remote.changegroupsubset(fetch, heads, 'pull')
                # we use unfiltered changelog here because hidden revision must
                # be taken in account for phase synchronization. They may
                # becomes public and becomes visible again.
                cl = self.unfiltered().changelog
                clstart = len(cl)
                result = self.addchangegroup(cg, 'pull', remote.url())
                clend = len(cl)
                added = [cl.node(r) for r in xrange(clstart, clend)]

            # compute target subset
            if heads is None:
                # We pulled every thing possible
                # sync on everything common
                subset = common + added
            else:
                # We pulled a specific subset
                # sync on this subset
                subset = heads

            # Get remote phases data from remote
            remotephases = remote.listkeys('phases')
            publishing = bool(remotephases.get('publishing', False))
            if remotephases and not publishing:
                # remote is new and unpublishing
                pheads, _dr = phases.analyzeremotephases(self, subset,
                                                         remotephases)
                phases.advanceboundary(self, phases.public, pheads)
                phases.advanceboundary(self, phases.draft, subset)
            else:
                # Remote is old or publishing all common changesets
                # should be seen as public
                phases.advanceboundary(self, phases.public, subset)

            def gettransaction():
                if tr is None:
                    return self.transaction(trname)
                return tr

            obstr = obsolete.syncpull(self, remote, gettransaction)
            if obstr is not None:
                tr = obstr

            if tr is not None:
                tr.close()
        finally:
            if tr is not None:
                tr.release()
            lock.release()

        return result

    def checkpush(self, force, revs):
        """Extensions can override this function if additional checks have
        to be performed before pushing, or call it if they override push
        command.
        """
        pass

    def push(self, remote, force=False, revs=None, newbranch=False):
        '''Push outgoing changesets (limited by revs) from the current
        repository to remote. Return an integer:
          - None means nothing to push
          - 0 means HTTP error
          - 1 means we pushed and remote head count is unchanged *or*
            we have outgoing changesets but refused to push
          - other values as described by addchangegroup()
        '''
        # there are two ways to push to remote repo:
        #
        # addchangegroup assumes local user can lock remote
        # repo (local filesystem, old ssh servers).
        #
        # unbundle assumes local user cannot lock remote repo (new ssh
        # servers, http servers).

        if not remote.canpush():
            raise util.Abort(_("destination does not support push"))
        unfi = self.unfiltered()
        def localphasemove(nodes, phase=phases.public):
            """move <nodes> to <phase> in the local source repo"""
            if locallock is not None:
                phases.advanceboundary(self, phase, nodes)
            else:
                # repo is not locked, do not change any phases!
                # Informs the user that phases should have been moved when
                # applicable.
                actualmoves = [n for n in nodes if phase < self[n].phase()]
                phasestr = phases.phasenames[phase]
                if actualmoves:
                    self.ui.status(_('cannot lock source repo, skipping local'
                                     ' %s phase update\n') % phasestr)
        # get local lock as we might write phase data
        locallock = None
        try:
            locallock = self.lock()
        except IOError, err:
            if err.errno != errno.EACCES:
                raise
            # source repo cannot be locked.
            # We do not abort the push, but just disable the local phase
            # synchronisation.
            msg = 'cannot lock source repository: %s\n' % err
            self.ui.debug(msg)
        try:
            self.checkpush(force, revs)
            lock = None
            unbundle = remote.capable('unbundle')
            if not unbundle:
                lock = remote.lock()
            try:
                # discovery
                fci = discovery.findcommonincoming
                commoninc = fci(unfi, remote, force=force)
                common, inc, remoteheads = commoninc
                fco = discovery.findcommonoutgoing
                outgoing = fco(unfi, remote, onlyheads=revs,
                               commoninc=commoninc, force=force)


                if not outgoing.missing:
                    # nothing to push
                    scmutil.nochangesfound(unfi.ui, unfi, outgoing.excluded)
                    ret = None
                else:
                    # something to push
                    if not force:
                        # if self.obsstore == False --> no obsolete
                        # then, save the iteration
                        if unfi.obsstore:
                            # this message are here for 80 char limit reason
                            mso = _("push includes obsolete changeset: %s!")
                            mst = "push includes %s changeset: %s!"
                            # plain versions for i18n tool to detect them
                            _("push includes unstable changeset: %s!")
                            _("push includes bumped changeset: %s!")
                            _("push includes divergent changeset: %s!")
                            # If we are to push if there is at least one
                            # obsolete or unstable changeset in missing, at
                            # least one of the missinghead will be obsolete or
                            # unstable. So checking heads only is ok
                            for node in outgoing.missingheads:
                                ctx = unfi[node]
                                if ctx.obsolete():
                                    raise util.Abort(mso % ctx)
                                elif ctx.troubled():
                                    raise util.Abort(_(mst)
                                                     % (ctx.troubles()[0],
                                                        ctx))
                        discovery.checkheads(unfi, remote, outgoing,
                                             remoteheads, newbranch,
                                             bool(inc))

                    # create a changegroup from local
                    if revs is None and not outgoing.excluded:
                        # push everything,
                        # use the fast path, no race possible on push
                        cg = self._changegroup(outgoing.missing, 'push')
                    else:
                        cg = self.getlocalbundle('push', outgoing)

                    # apply changegroup to remote
                    if unbundle:
                        # local repo finds heads on server, finds out what
                        # revs it must push. once revs transferred, if server
                        # finds it has different heads (someone else won
                        # commit/push race), server aborts.
                        if force:
                            remoteheads = ['force']
                        # ssh: return remote's addchangegroup()
                        # http: return remote's addchangegroup() or 0 for error
                        ret = remote.unbundle(cg, remoteheads, 'push')
                    else:
                        # we return an integer indicating remote head count
                        # change
                        ret = remote.addchangegroup(cg, 'push', self.url())

                if ret:
                    # push succeed, synchronize target of the push
                    cheads = outgoing.missingheads
                elif revs is None:
                    # All out push fails. synchronize all common
                    cheads = outgoing.commonheads
                else:
                    # I want cheads = heads(::missingheads and ::commonheads)
                    # (missingheads is revs with secret changeset filtered out)
                    #
                    # This can be expressed as:
                    #     cheads = ( (missingheads and ::commonheads)
                    #              + (commonheads and ::missingheads))"
                    #              )
                    #
                    # while trying to push we already computed the following:
                    #     common = (::commonheads)
                    #     missing = ((commonheads::missingheads) - commonheads)
                    #
                    # We can pick:
                    # * missingheads part of common (::commonheads)
                    common = set(outgoing.common)
                    cheads = [node for node in revs if node in common]
                    # and
                    # * commonheads parents on missing
                    revset = unfi.set('%ln and parents(roots(%ln))',
                                     outgoing.commonheads,
                                     outgoing.missing)
                    cheads.extend(c.node() for c in revset)
                # even when we don't push, exchanging phase data is useful
                remotephases = remote.listkeys('phases')
                if (self.ui.configbool('ui', '_usedassubrepo', False)
                    and remotephases    # server supports phases
                    and ret is None # nothing was pushed
                    and remotephases.get('publishing', False)):
                    # When:
                    # - this is a subrepo push
                    # - and remote support phase
                    # - and no changeset was pushed
                    # - and remote is publishing
                    # We may be in issue 3871 case!
                    # We drop the possible phase synchronisation done by
                    # courtesy to publish changesets possibly locally draft
                    # on the remote.
                    remotephases = {'publishing': 'True'}
                if not remotephases: # old server or public only repo
                    localphasemove(cheads)
                    # don't push any phase data as there is nothing to push
                else:
                    ana = phases.analyzeremotephases(self, cheads, remotephases)
                    pheads, droots = ana
                    ### Apply remote phase on local
                    if remotephases.get('publishing', False):
                        localphasemove(cheads)
                    else: # publish = False
                        localphasemove(pheads)
                        localphasemove(cheads, phases.draft)
                    ### Apply local phase on remote

                    # Get the list of all revs draft on remote by public here.
                    # XXX Beware that revset break if droots is not strictly
                    # XXX root we may want to ensure it is but it is costly
                    outdated =  unfi.set('heads((%ln::%ln) and public())',
                                         droots, cheads)
                    for newremotehead in outdated:
                        r = remote.pushkey('phases',
                                           newremotehead.hex(),
                                           str(phases.draft),
                                           str(phases.public))
                        if not r:
                            self.ui.warn(_('updating %s to public failed!\n')
                                            % newremotehead)
                self.ui.debug('try to push obsolete markers to remote\n')
                obsolete.syncpush(self, remote)
            finally:
                if lock is not None:
                    lock.release()
        finally:
            if locallock is not None:
                locallock.release()

        self.ui.debug("checking for updated bookmarks\n")
        rb = remote.listkeys('bookmarks')
        for k in rb.keys():
            if k in unfi._bookmarks:
                nr, nl = rb[k], hex(self._bookmarks[k])
                if nr in unfi:
                    cr = unfi[nr]
                    cl = unfi[nl]
                    if bookmarks.validdest(unfi, cr, cl):
                        r = remote.pushkey('bookmarks', k, nr, nl)
                        if r:
                            self.ui.status(_("updating bookmark %s\n") % k)
                        else:
                            self.ui.warn(_('updating bookmark %s'
                                           ' failed!\n') % k)

        return ret

    def changegroupinfo(self, nodes, source):
        if self.ui.verbose or source == 'bundle':
            self.ui.status(_("%d changesets found\n") % len(nodes))
        if self.ui.debugflag:
            self.ui.debug("list of changesets:\n")
            for node in nodes:
                self.ui.debug("%s\n" % hex(node))

    def changegroupsubset(self, bases, heads, source):
        """Compute a changegroup consisting of all the nodes that are
        descendants of any of the bases and ancestors of any of the heads.
        Return a chunkbuffer object whose read() method will return
        successive changegroup chunks.

        It is fairly complex as determining which filenodes and which
        manifest nodes need to be included for the changeset to be complete
        is non-trivial.

        Another wrinkle is doing the reverse, figuring out which changeset in
        the changegroup a particular filenode or manifestnode belongs to.
        """
        cl = self.changelog
        if not bases:
            bases = [nullid]
        csets, bases, heads = cl.nodesbetween(bases, heads)
        # We assume that all ancestors of bases are known
        common = cl.ancestors([cl.rev(n) for n in bases])
        return self._changegroupsubset(common, csets, heads, source)

    def getlocalbundle(self, source, outgoing):
        """Like getbundle, but taking a discovery.outgoing as an argument.

        This is only implemented for local repos and reuses potentially
        precomputed sets in outgoing."""
        if not outgoing.missing:
            return None
        return self._changegroupsubset(outgoing.common,
                                       outgoing.missing,
                                       outgoing.missingheads,
                                       source)

    def getbundle(self, source, heads=None, common=None):
        """Like changegroupsubset, but returns the set difference between the
        ancestors of heads and the ancestors common.

        If heads is None, use the local heads. If common is None, use [nullid].

        The nodes in common might not all be known locally due to the way the
        current discovery protocol works.
        """
        cl = self.changelog
        if common:
            hasnode = cl.hasnode
            common = [n for n in common if hasnode(n)]
        else:
            common = [nullid]
        if not heads:
            heads = cl.heads()
        return self.getlocalbundle(source,
                                   discovery.outgoing(cl, common, heads))

    @unfilteredmethod
    def _changegroupsubset(self, commonrevs, csets, heads, source):

        cl = self.changelog
        mf = self.manifest
        mfs = {} # needed manifests
        fnodes = {} # needed file nodes
        changedfiles = set()
        fstate = ['', {}]
        count = [0, 0]

        # can we go through the fast path ?
        heads.sort()
        if heads == sorted(self.heads()):
            return self._changegroup(csets, source)

        # slow path
        self.hook('preoutgoing', throw=True, source=source)
        self.changegroupinfo(csets, source)

        # filter any nodes that claim to be part of the known set
        def prune(revlog, missing):
            rr, rl = revlog.rev, revlog.linkrev
            return [n for n in missing
                    if rl(rr(n)) not in commonrevs]

        progress = self.ui.progress
        _bundling = _('bundling')
        _changesets = _('changesets')
        _manifests = _('manifests')
        _files = _('files')

        def lookup(revlog, x):
            if revlog == cl:
                c = cl.read(x)
                changedfiles.update(c[3])
                mfs.setdefault(c[0], x)
                count[0] += 1
                progress(_bundling, count[0],
                         unit=_changesets, total=count[1])
                return x
            elif revlog == mf:
                clnode = mfs[x]
                mdata = mf.readfast(x)
                for f, n in mdata.iteritems():
                    if f in changedfiles:
                        fnodes[f].setdefault(n, clnode)
                count[0] += 1
                progress(_bundling, count[0],
                         unit=_manifests, total=count[1])
                return clnode
            else:
                progress(_bundling, count[0], item=fstate[0],
                         unit=_files, total=count[1])
                return fstate[1][x]

        bundler = changegroup.bundle10(lookup)
        reorder = self.ui.config('bundle', 'reorder', 'auto')
        if reorder == 'auto':
            reorder = None
        else:
            reorder = util.parsebool(reorder)

        def gengroup():
            # Create a changenode group generator that will call our functions
            # back to lookup the owning changenode and collect information.
            count[:] = [0, len(csets)]
            for chunk in cl.group(csets, bundler, reorder=reorder):
                yield chunk
            progress(_bundling, None)

            # Create a generator for the manifestnodes that calls our lookup
            # and data collection functions back.
            for f in changedfiles:
                fnodes[f] = {}
            count[:] = [0, len(mfs)]
            for chunk in mf.group(prune(mf, mfs), bundler, reorder=reorder):
                yield chunk
            progress(_bundling, None)

            mfs.clear()

            # Go through all our files in order sorted by name.
            count[:] = [0, len(changedfiles)]
            for fname in sorted(changedfiles):
                filerevlog = self.file(fname)
                if not len(filerevlog):
                    raise util.Abort(_("empty or missing revlog for %s")
                                     % fname)
                fstate[0] = fname
                fstate[1] = fnodes.pop(fname, {})

                nodelist = prune(filerevlog, fstate[1])
                if nodelist:
                    count[0] += 1
                    yield bundler.fileheader(fname)
                    for chunk in filerevlog.group(nodelist, bundler, reorder):
                        yield chunk

            # Signal that no more groups are left.
            yield bundler.close()
            progress(_bundling, None)

            if csets:
                self.hook('outgoing', node=hex(csets[0]), source=source)

        return changegroup.unbundle10(util.chunkbuffer(gengroup()), 'UN')

    def changegroup(self, basenodes, source):
        # to avoid a race we use changegroupsubset() (issue1320)
        return self.changegroupsubset(basenodes, self.heads(), source)

    @unfilteredmethod
    def _changegroup(self, nodes, source):
        """Compute the changegroup of all nodes that we have that a recipient
        doesn't.  Return a chunkbuffer object whose read() method will return
        successive changegroup chunks.

        This is much easier than the previous function as we can assume that
        the recipient has any changenode we aren't sending them.

        nodes is the set of nodes to send"""

        cl = self.changelog
        mf = self.manifest
        mfs = {}
        changedfiles = set()
        fstate = ['']
        count = [0, 0]

        self.hook('preoutgoing', throw=True, source=source)
        self.changegroupinfo(nodes, source)

        revset = set([cl.rev(n) for n in nodes])

        def gennodelst(log):
            ln, llr = log.node, log.linkrev
            return [ln(r) for r in log if llr(r) in revset]

        progress = self.ui.progress
        _bundling = _('bundling')
        _changesets = _('changesets')
        _manifests = _('manifests')
        _files = _('files')

        def lookup(revlog, x):
            if revlog == cl:
                c = cl.read(x)
                changedfiles.update(c[3])
                mfs.setdefault(c[0], x)
                count[0] += 1
                progress(_bundling, count[0],
                         unit=_changesets, total=count[1])
                return x
            elif revlog == mf:
                count[0] += 1
                progress(_bundling, count[0],
                         unit=_manifests, total=count[1])
                return cl.node(revlog.linkrev(revlog.rev(x)))
            else:
                progress(_bundling, count[0], item=fstate[0],
                    total=count[1], unit=_files)
                return cl.node(revlog.linkrev(revlog.rev(x)))

        bundler = changegroup.bundle10(lookup)
        reorder = self.ui.config('bundle', 'reorder', 'auto')
        if reorder == 'auto':
            reorder = None
        else:
            reorder = util.parsebool(reorder)

        def gengroup():
            '''yield a sequence of changegroup chunks (strings)'''
            # construct a list of all changed files

            count[:] = [0, len(nodes)]
            for chunk in cl.group(nodes, bundler, reorder=reorder):
                yield chunk
            progress(_bundling, None)

            count[:] = [0, len(mfs)]
            for chunk in mf.group(gennodelst(mf), bundler, reorder=reorder):
                yield chunk
            progress(_bundling, None)

            count[:] = [0, len(changedfiles)]
            for fname in sorted(changedfiles):
                filerevlog = self.file(fname)
                if not len(filerevlog):
                    raise util.Abort(_("empty or missing revlog for %s")
                                     % fname)
                fstate[0] = fname
                nodelist = gennodelst(filerevlog)
                if nodelist:
                    count[0] += 1
                    yield bundler.fileheader(fname)
                    for chunk in filerevlog.group(nodelist, bundler, reorder):
                        yield chunk
            yield bundler.close()
            progress(_bundling, None)

            if nodes:
                self.hook('outgoing', node=hex(nodes[0]), source=source)

        return changegroup.unbundle10(util.chunkbuffer(gengroup()), 'UN')

    @unfilteredmethod
    def addchangegroup(self, source, srctype, url, emptyok=False):
        """Add the changegroup returned by source.read() to this repo.
        srctype is a string like 'push', 'pull', or 'unbundle'.  url is
        the URL of the repo where this changegroup is coming from.

        Return an integer summarizing the change to this repo:
        - nothing changed or no source: 0
        - more heads than before: 1+added heads (2..n)
        - fewer heads than before: -1-removed heads (-2..-n)
        - number of heads stays the same: 1
        """
        def csmap(x):
            self.ui.debug("add changeset %s\n" % short(x))
            return len(cl)

        def revmap(x):
            return cl.rev(x)

        if not source:
            return 0

        self.hook('prechangegroup', throw=True, source=srctype, url=url)

        changesets = files = revisions = 0
        efiles = set()

        # write changelog data to temp files so concurrent readers will not see
        # inconsistent view
        cl = self.changelog
        cl.delayupdate()
        oldheads = cl.heads()

        tr = self.transaction("\n".join([srctype, util.hidepassword(url)]))
        try:
            trp = weakref.proxy(tr)
            # pull off the changeset group
            self.ui.status(_("adding changesets\n"))
            clstart = len(cl)
            class prog(object):
                step = _('changesets')
                count = 1
                ui = self.ui
                total = None
                def __call__(self):
                    self.ui.progress(self.step, self.count, unit=_('chunks'),
                                     total=self.total)
                    self.count += 1
            pr = prog()
            source.callback = pr

            source.changelogheader()
            srccontent = cl.addgroup(source, csmap, trp)
            if not (srccontent or emptyok):
                raise util.Abort(_("received changelog group is empty"))
            clend = len(cl)
            changesets = clend - clstart
            for c in xrange(clstart, clend):
                efiles.update(self[c].files())
            efiles = len(efiles)
            self.ui.progress(_('changesets'), None)

            # pull off the manifest group
            self.ui.status(_("adding manifests\n"))
            pr.step = _('manifests')
            pr.count = 1
            pr.total = changesets # manifests <= changesets
            # no need to check for empty manifest group here:
            # if the result of the merge of 1 and 2 is the same in 3 and 4,
            # no new manifest will be created and the manifest group will
            # be empty during the pull
            source.manifestheader()
            self.manifest.addgroup(source, revmap, trp)
            self.ui.progress(_('manifests'), None)

            needfiles = {}
            if self.ui.configbool('server', 'validate', default=False):
                # validate incoming csets have their manifests
                for cset in xrange(clstart, clend):
                    mfest = self.changelog.read(self.changelog.node(cset))[0]
                    mfest = self.manifest.readdelta(mfest)
                    # store file nodes we must see
                    for f, n in mfest.iteritems():
                        needfiles.setdefault(f, set()).add(n)

            # process the files
            self.ui.status(_("adding file changes\n"))
            pr.step = _('files')
            pr.count = 1
            pr.total = efiles
            source.callback = None

            while True:
                chunkdata = source.filelogheader()
                if not chunkdata:
                    break
                f = chunkdata["filename"]
                self.ui.debug("adding %s revisions\n" % f)
                pr()
                fl = self.file(f)
                o = len(fl)
                if not fl.addgroup(source, revmap, trp):
                    raise util.Abort(_("received file revlog group is empty"))
                revisions += len(fl) - o
                files += 1
                if f in needfiles:
                    needs = needfiles[f]
                    for new in xrange(o, len(fl)):
                        n = fl.node(new)
                        if n in needs:
                            needs.remove(n)
                        else:
                            raise util.Abort(
                                _("received spurious file revlog entry"))
                    if not needs:
                        del needfiles[f]
            self.ui.progress(_('files'), None)

            for f, needs in needfiles.iteritems():
                fl = self.file(f)
                for n in needs:
                    try:
                        fl.rev(n)
                    except error.LookupError:
                        raise util.Abort(
                            _('missing file data for %s:%s - run hg verify') %
                            (f, hex(n)))

            dh = 0
            if oldheads:
                heads = cl.heads()
                dh = len(heads) - len(oldheads)
                for h in heads:
                    if h not in oldheads and self[h].closesbranch():
                        dh -= 1
            htext = ""
            if dh:
                htext = _(" (%+d heads)") % dh

            self.ui.status(_("added %d changesets"
                             " with %d changes to %d files%s\n")
                             % (changesets, revisions, files, htext))
            self.invalidatevolatilesets()

            if changesets > 0:
                p = lambda: cl.writepending() and self.root or ""
                self.hook('pretxnchangegroup', throw=True,
                          node=hex(cl.node(clstart)), source=srctype,
                          url=url, pending=p)

            added = [cl.node(r) for r in xrange(clstart, clend)]
            publishing = self.ui.configbool('phases', 'publish', True)
            if srctype == 'push':
                # Old server can not push the boundary themself.
                # New server won't push the boundary if changeset already
                # existed locally as secrete
                #
                # We should not use added here but the list of all change in
                # the bundle
                if publishing:
                    phases.advanceboundary(self, phases.public, srccontent)
                else:
                    phases.advanceboundary(self, phases.draft, srccontent)
                    phases.retractboundary(self, phases.draft, added)
            elif srctype != 'strip':
                # publishing only alter behavior during push
                #
                # strip should not touch boundary at all
                phases.retractboundary(self, phases.draft, added)

            # make changelog see real files again
            cl.finalize(trp)

            tr.close()

            if changesets > 0:
                if srctype != 'strip':
                    # During strip, branchcache is invalid but coming call to
                    # `destroyed` will repair it.
                    # In other case we can safely update cache on disk.
                    branchmap.updatecache(self.filtered('served'))
                def runhooks():
                    # forcefully update the on-disk branch cache
                    self.ui.debug("updating the branch cache\n")
                    self.hook("changegroup", node=hex(cl.node(clstart)),
                              source=srctype, url=url)

                    for n in added:
                        self.hook("incoming", node=hex(n), source=srctype,
                                  url=url)

                    newheads = [h for h in self.heads() if h not in oldheads]
                    self.ui.log("incoming",
                                "%s incoming changes - new heads: %s\n",
                                len(added),
                                ', '.join([hex(c[:6]) for c in newheads]))
                self._afterlock(runhooks)

        finally:
            tr.release()
        # never return 0 here:
        if dh < 0:
            return dh - 1
        else:
            return dh + 1

    def stream_in(self, remote, requirements):
        lock = self.lock()
        try:
            # Save remote branchmap. We will use it later
            # to speed up branchcache creation
            rbranchmap = None
            if remote.capable("branchmap"):
                rbranchmap = remote.branchmap()

            fp = remote.stream_out()
            l = fp.readline()
            try:
                resp = int(l)
            except ValueError:
                raise error.ResponseError(
                    _('unexpected response from remote server:'), l)
            if resp == 1:
                raise util.Abort(_('operation forbidden by server'))
            elif resp == 2:
                raise util.Abort(_('locking the remote repository failed'))
            elif resp != 0:
                raise util.Abort(_('the server sent an unknown error code'))
            self.ui.status(_('streaming all changes\n'))
            l = fp.readline()
            try:
                total_files, total_bytes = map(int, l.split(' ', 1))
            except (ValueError, TypeError):
                raise error.ResponseError(
                    _('unexpected response from remote server:'), l)
            self.ui.status(_('%d files to transfer, %s of data\n') %
                           (total_files, util.bytecount(total_bytes)))
            handled_bytes = 0
            self.ui.progress(_('clone'), 0, total=total_bytes)
            start = time.time()
            for i in xrange(total_files):
                # XXX doesn't support '\n' or '\r' in filenames
                l = fp.readline()
                try:
                    name, size = l.split('\0', 1)
                    size = int(size)
                except (ValueError, TypeError):
                    raise error.ResponseError(
                        _('unexpected response from remote server:'), l)
                if self.ui.debugflag:
                    self.ui.debug('adding %s (%s)\n' %
                                  (name, util.bytecount(size)))
                # for backwards compat, name was partially encoded
                ofp = self.sopener(store.decodedir(name), 'w')
                for chunk in util.filechunkiter(fp, limit=size):
                    handled_bytes += len(chunk)
                    self.ui.progress(_('clone'), handled_bytes,
                                     total=total_bytes)
                    ofp.write(chunk)
                ofp.close()
            elapsed = time.time() - start
            if elapsed <= 0:
                elapsed = 0.001
            self.ui.progress(_('clone'), None)
            self.ui.status(_('transferred %s in %.1f seconds (%s/sec)\n') %
                           (util.bytecount(total_bytes), elapsed,
                            util.bytecount(total_bytes / elapsed)))

            # new requirements = old non-format requirements +
            #                    new format-related
            # requirements from the streamed-in repository
            requirements.update(set(self.requirements) - self.supportedformats)
            self._applyrequirements(requirements)
            self._writerequirements()

            if rbranchmap:
                rbheads = []
                for bheads in rbranchmap.itervalues():
                    rbheads.extend(bheads)

                if rbheads:
                    rtiprev = max((int(self.changelog.rev(node))
                            for node in rbheads))
                    cache = branchmap.branchcache(rbranchmap,
                                                  self[rtiprev].node(),
                                                  rtiprev)
                    # Try to stick it as low as possible
                    # filter above served are unlikely to be fetch from a clone
                    for candidate in ('base', 'immutable', 'served'):
                        rview = self.filtered(candidate)
                        if cache.validfor(rview):
                            self._branchcaches[candidate] = cache
                            cache.write(rview)
                            break
            self.invalidate()
            return len(self.heads()) + 1
        finally:
            lock.release()

    def clone(self, remote, heads=[], stream=False):
        '''clone remote repository.

        keyword arguments:
        heads: list of revs to clone (forces use of pull)
        stream: use streaming clone if possible'''

        # now, all clients that can request uncompressed clones can
        # read repo formats supported by all servers that can serve
        # them.

        # if revlog format changes, client will have to check version
        # and format flags on "stream" capability, and use
        # uncompressed only if compatible.

        if not stream:
            # if the server explicitly prefers to stream (for fast LANs)
            stream = remote.capable('stream-preferred')

        if stream and not heads:
            # 'stream' means remote revlog format is revlogv1 only
            if remote.capable('stream'):
                return self.stream_in(remote, set(('revlogv1',)))
            # otherwise, 'streamreqs' contains the remote revlog format
            streamreqs = remote.capable('streamreqs')
            if streamreqs:
                streamreqs = set(streamreqs.split(','))
                # if we support it, stream in and adjust our requirements
                if not streamreqs - self.supportedformats:
                    return self.stream_in(remote, streamreqs)
        return self.pull(remote, heads)

    def pushkey(self, namespace, key, old, new):
        self.hook('prepushkey', throw=True, namespace=namespace, key=key,
                  old=old, new=new)
        self.ui.debug('pushing key for "%s:%s"\n' % (namespace, key))
        ret = pushkey.push(self, namespace, key, old, new)
        self.hook('pushkey', namespace=namespace, key=key, old=old, new=new,
                  ret=ret)
        return ret

    def listkeys(self, namespace):
        self.hook('prelistkeys', throw=True, namespace=namespace)
        self.ui.debug('listing keys for "%s"\n' % namespace)
        values = pushkey.list(self, namespace)
        self.hook('listkeys', namespace=namespace, values=values)
        return values

    def debugwireargs(self, one, two, three=None, four=None, five=None):
        '''used to test argument passing over the wire'''
        return "%s %s %s %s %s" % (one, two, three, four, five)

    def savecommitmessage(self, text):
        fp = self.opener('last-message.txt', 'wb')
        try:
            fp.write(text)
        finally:
            fp.close()
        return self.pathto(fp.name[len(self.root) + 1:])

# used to avoid circular references so destructors work
def aftertrans(files):
    renamefiles = [tuple(t) for t in files]
    def a():
        for vfs, src, dest in renamefiles:
            try:
                vfs.rename(src, dest)
            except OSError: # journal file does not yet exist
                pass
    return a

def undoname(fn):
    base, name = os.path.split(fn)
    assert name.startswith('journal')
    return os.path.join(base, name.replace('journal', 'undo', 1))

def instance(ui, path, create):
    return localrepository(ui, util.urllocalpath(path), create)

def islocal(path):
    return True
