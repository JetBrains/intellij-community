# bundlerepo.py - repository class for viewing uncompressed bundles
#
# Copyright 2006, 2007 Benoit Boissinot <bboissin@gmail.com>
#
# This software may be used and distributed according to the terms of the
# GNU General Public License version 2 or any later version.

"""Repository class for viewing uncompressed bundles.

This provides a read-only repository interface to bundles as if they
were part of the actual repository.
"""

from node import nullid
from i18n import _
import os, struct, bz2, zlib, tempfile, shutil
import changegroup, util, mdiff
import localrepo, changelog, manifest, filelog, revlog, error

class bundlerevlog(revlog.revlog):
    def __init__(self, opener, indexfile, bundlefile,
                 linkmapper=None):
        # How it works:
        # to retrieve a revision, we need to know the offset of
        # the revision in the bundlefile (an opened file).
        #
        # We store this offset in the index (start), to differentiate a
        # rev in the bundle and from a rev in the revlog, we check
        # len(index[r]). If the tuple is bigger than 7, it is a bundle
        # (it is bigger since we store the node to which the delta is)
        #
        revlog.revlog.__init__(self, opener, indexfile)
        self.bundlefile = bundlefile
        self.basemap = {}
        def chunkpositer():
            for chunk in changegroup.chunkiter(bundlefile):
                pos = bundlefile.tell()
                yield chunk, pos - len(chunk)
        n = len(self)
        prev = None
        for chunk, start in chunkpositer():
            size = len(chunk)
            if size < 80:
                raise util.Abort(_("invalid changegroup"))
            start += 80
            size -= 80
            node, p1, p2, cs = struct.unpack("20s20s20s20s", chunk[:80])
            if node in self.nodemap:
                prev = node
                continue
            for p in (p1, p2):
                if not p in self.nodemap:
                    raise error.LookupError(p, self.indexfile,
                                            _("unknown parent"))
            if linkmapper is None:
                link = n
            else:
                link = linkmapper(cs)

            if not prev:
                prev = p1
            # start, size, full unc. size, base (unused), link, p1, p2, node
            e = (revlog.offset_type(start, 0), size, -1, -1, link,
                 self.rev(p1), self.rev(p2), node)
            self.basemap[n] = prev
            self.index.insert(-1, e)
            self.nodemap[node] = n
            prev = node
            n += 1

    def bundle(self, rev):
        """is rev from the bundle"""
        if rev < 0:
            return False
        return rev in self.basemap
    def bundlebase(self, rev):
        return self.basemap[rev]
    def _chunk(self, rev):
        # Warning: in case of bundle, the diff is against bundlebase,
        # not against rev - 1
        # XXX: could use some caching
        if not self.bundle(rev):
            return revlog.revlog._chunk(self, rev)
        self.bundlefile.seek(self.start(rev))
        return self.bundlefile.read(self.length(rev))

    def revdiff(self, rev1, rev2):
        """return or calculate a delta between two revisions"""
        if self.bundle(rev1) and self.bundle(rev2):
            # hot path for bundle
            revb = self.rev(self.bundlebase(rev2))
            if revb == rev1:
                return self._chunk(rev2)
        elif not self.bundle(rev1) and not self.bundle(rev2):
            return revlog.revlog.revdiff(self, rev1, rev2)

        return mdiff.textdiff(self.revision(self.node(rev1)),
                         self.revision(self.node(rev2)))

    def revision(self, node):
        """return an uncompressed revision of a given"""
        if node == nullid:
            return ""

        text = None
        chain = []
        iter_node = node
        rev = self.rev(iter_node)
        # reconstruct the revision if it is from a changegroup
        while self.bundle(rev):
            if self._cache and self._cache[0] == iter_node:
                text = self._cache[2]
                break
            chain.append(rev)
            iter_node = self.bundlebase(rev)
            rev = self.rev(iter_node)
        if text is None:
            text = revlog.revlog.revision(self, iter_node)

        while chain:
            delta = self._chunk(chain.pop())
            text = mdiff.patches(text, [delta])

        p1, p2 = self.parents(node)
        if node != revlog.hash(text, p1, p2):
            raise error.RevlogError(_("integrity check failed on %s:%d")
                                     % (self.datafile, self.rev(node)))

        self._cache = (node, self.rev(node), text)
        return text

    def addrevision(self, text, transaction, link, p1=None, p2=None, d=None):
        raise NotImplementedError
    def addgroup(self, revs, linkmapper, transaction):
        raise NotImplementedError
    def strip(self, rev, minlink):
        raise NotImplementedError
    def checksize(self):
        raise NotImplementedError

class bundlechangelog(bundlerevlog, changelog.changelog):
    def __init__(self, opener, bundlefile):
        changelog.changelog.__init__(self, opener)
        bundlerevlog.__init__(self, opener, self.indexfile, bundlefile)

class bundlemanifest(bundlerevlog, manifest.manifest):
    def __init__(self, opener, bundlefile, linkmapper):
        manifest.manifest.__init__(self, opener)
        bundlerevlog.__init__(self, opener, self.indexfile, bundlefile,
                              linkmapper)

class bundlefilelog(bundlerevlog, filelog.filelog):
    def __init__(self, opener, path, bundlefile, linkmapper):
        filelog.filelog.__init__(self, opener, path)
        bundlerevlog.__init__(self, opener, self.indexfile, bundlefile,
                              linkmapper)

class bundlerepository(localrepo.localrepository):
    def __init__(self, ui, path, bundlename):
        self._tempparent = None
        try:
            localrepo.localrepository.__init__(self, ui, path)
        except error.RepoError:
            self._tempparent = tempfile.mkdtemp()
            localrepo.instance(ui, self._tempparent, 1)
            localrepo.localrepository.__init__(self, ui, self._tempparent)

        if path:
            self._url = 'bundle:' + util.expandpath(path) + '+' + bundlename
        else:
            self._url = 'bundle:' + bundlename

        self.tempfile = None
        self.bundlefile = open(bundlename, "rb")
        header = self.bundlefile.read(6)
        if not header.startswith("HG"):
            raise util.Abort(_("%s: not a Mercurial bundle file") % bundlename)
        elif not header.startswith("HG10"):
            raise util.Abort(_("%s: unknown bundle version") % bundlename)
        elif (header == "HG10BZ") or (header == "HG10GZ"):
            fdtemp, temp = tempfile.mkstemp(prefix="hg-bundle-",
                                            suffix=".hg10un", dir=self.path)
            self.tempfile = temp
            fptemp = os.fdopen(fdtemp, 'wb')
            def generator(f):
                if header == "HG10BZ":
                    zd = bz2.BZ2Decompressor()
                    zd.decompress("BZ")
                elif header == "HG10GZ":
                    zd = zlib.decompressobj()
                for chunk in f:
                    yield zd.decompress(chunk)
            gen = generator(util.filechunkiter(self.bundlefile, 4096))

            try:
                fptemp.write("HG10UN")
                for chunk in gen:
                    fptemp.write(chunk)
            finally:
                fptemp.close()
                self.bundlefile.close()

            self.bundlefile = open(self.tempfile, "rb")
            # seek right after the header
            self.bundlefile.seek(6)
        elif header == "HG10UN":
            # nothing to do
            pass
        else:
            raise util.Abort(_("%s: unknown bundle compression type")
                             % bundlename)
        # dict with the mapping 'filename' -> position in the bundle
        self.bundlefilespos = {}

    @util.propertycache
    def changelog(self):
        c = bundlechangelog(self.sopener, self.bundlefile)
        self.manstart = self.bundlefile.tell()
        return c

    @util.propertycache
    def manifest(self):
        self.bundlefile.seek(self.manstart)
        m = bundlemanifest(self.sopener, self.bundlefile, self.changelog.rev)
        self.filestart = self.bundlefile.tell()
        return m

    @util.propertycache
    def manstart(self):
        self.changelog
        return self.manstart

    @util.propertycache
    def filestart(self):
        self.manifest
        return self.filestart

    def url(self):
        return self._url

    def file(self, f):
        if not self.bundlefilespos:
            self.bundlefile.seek(self.filestart)
            while 1:
                chunk = changegroup.getchunk(self.bundlefile)
                if not chunk:
                    break
                self.bundlefilespos[chunk] = self.bundlefile.tell()
                for c in changegroup.chunkiter(self.bundlefile):
                    pass

        if f[0] == '/':
            f = f[1:]
        if f in self.bundlefilespos:
            self.bundlefile.seek(self.bundlefilespos[f])
            return bundlefilelog(self.sopener, f, self.bundlefile,
                                 self.changelog.rev)
        else:
            return filelog.filelog(self.sopener, f)

    def close(self):
        """Close assigned bundle file immediately."""
        self.bundlefile.close()

    def __del__(self):
        bundlefile = getattr(self, 'bundlefile', None)
        if bundlefile and not bundlefile.closed:
            bundlefile.close()
        tempfile = getattr(self, 'tempfile', None)
        if tempfile is not None:
            os.unlink(tempfile)
        if self._tempparent:
            shutil.rmtree(self._tempparent, True)

    def cancopy(self):
        return False

    def getcwd(self):
        return os.getcwd() # always outside the repo

def instance(ui, path, create):
    if create:
        raise util.Abort(_('cannot create new bundle repository'))
    parentpath = ui.config("bundle", "mainreporoot", "")
    if parentpath:
        # Try to make the full path relative so we get a nice, short URL.
        # In particular, we don't want temp dir names in test outputs.
        cwd = os.getcwd()
        if parentpath == cwd:
            parentpath = ''
        else:
            cwd = os.path.join(cwd,'')
            if parentpath.startswith(cwd):
                parentpath = parentpath[len(cwd):]
    path = util.drop_scheme('file', path)
    if path.startswith('bundle:'):
        path = util.drop_scheme('bundle', path)
        s = path.split("+", 1)
        if len(s) == 1:
            repopath, bundlename = parentpath, s[0]
        else:
            repopath, bundlename = s
    else:
        repopath, bundlename = parentpath, path
    return bundlerepository(ui, repopath, bundlename)
