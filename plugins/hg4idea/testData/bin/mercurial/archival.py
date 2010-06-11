# archival.py - revision archival for mercurial
#
# Copyright 2006 Vadim Gelfer <vadim.gelfer@gmail.com>
#
# This software may be used and distributed according to the terms of the
# GNU General Public License version 2 or any later version.

from i18n import _
from node import hex
import cmdutil
import util
import cStringIO, os, stat, tarfile, time, zipfile
import zlib, gzip

def tidyprefix(dest, prefix, suffixes):
    '''choose prefix to use for names in archive.  make sure prefix is
    safe for consumers.'''

    if prefix:
        prefix = util.normpath(prefix)
    else:
        if not isinstance(dest, str):
            raise ValueError('dest must be string if no prefix')
        prefix = os.path.basename(dest)
        lower = prefix.lower()
        for sfx in suffixes:
            if lower.endswith(sfx):
                prefix = prefix[:-len(sfx)]
                break
    lpfx = os.path.normpath(util.localpath(prefix))
    prefix = util.pconvert(lpfx)
    if not prefix.endswith('/'):
        prefix += '/'
    if prefix.startswith('../') or os.path.isabs(lpfx) or '/../' in prefix:
        raise util.Abort(_('archive prefix contains illegal components'))
    return prefix

class tarit(object):
    '''write archive to tar file or stream.  can write uncompressed,
    or compress with gzip or bzip2.'''

    class GzipFileWithTime(gzip.GzipFile):

        def __init__(self, *args, **kw):
            timestamp = None
            if 'timestamp' in kw:
                timestamp = kw.pop('timestamp')
            if timestamp is None:
                self.timestamp = time.time()
            else:
                self.timestamp = timestamp
            gzip.GzipFile.__init__(self, *args, **kw)

        def _write_gzip_header(self):
            self.fileobj.write('\037\213')             # magic header
            self.fileobj.write('\010')                 # compression method
            # Python 2.6 deprecates self.filename
            fname = getattr(self, 'name', None) or self.filename
            flags = 0
            if fname:
                flags = gzip.FNAME
            self.fileobj.write(chr(flags))
            gzip.write32u(self.fileobj, long(self.timestamp))
            self.fileobj.write('\002')
            self.fileobj.write('\377')
            if fname:
                self.fileobj.write(fname + '\000')

    def __init__(self, dest, prefix, mtime, kind=''):
        self.prefix = tidyprefix(dest, prefix, ['.tar', '.tar.bz2', '.tar.gz',
                                                '.tgz', '.tbz2'])
        self.mtime = mtime

        def taropen(name, mode, fileobj=None):
            if kind == 'gz':
                mode = mode[0]
                if not fileobj:
                    fileobj = open(name, mode + 'b')
                gzfileobj = self.GzipFileWithTime(name, mode + 'b',
                                                  zlib.Z_BEST_COMPRESSION,
                                                  fileobj, timestamp=mtime)
                return tarfile.TarFile.taropen(name, mode, gzfileobj)
            else:
                return tarfile.open(name, mode + kind, fileobj)

        if isinstance(dest, str):
            self.z = taropen(dest, mode='w:')
        else:
            # Python 2.5-2.5.1 have a regression that requires a name arg
            self.z = taropen(name='', mode='w|', fileobj=dest)

    def addfile(self, name, mode, islink, data):
        i = tarfile.TarInfo(self.prefix + name)
        i.mtime = self.mtime
        i.size = len(data)
        if islink:
            i.type = tarfile.SYMTYPE
            i.mode = 0777
            i.linkname = data
            data = None
            i.size = 0
        else:
            i.mode = mode
            data = cStringIO.StringIO(data)
        self.z.addfile(i, data)

    def done(self):
        self.z.close()

class tellable(object):
    '''provide tell method for zipfile.ZipFile when writing to http
    response file object.'''

    def __init__(self, fp):
        self.fp = fp
        self.offset = 0

    def __getattr__(self, key):
        return getattr(self.fp, key)

    def write(self, s):
        self.fp.write(s)
        self.offset += len(s)

    def tell(self):
        return self.offset

class zipit(object):
    '''write archive to zip file or stream.  can write uncompressed,
    or compressed with deflate.'''

    def __init__(self, dest, prefix, mtime, compress=True):
        self.prefix = tidyprefix(dest, prefix, ('.zip',))
        if not isinstance(dest, str):
            try:
                dest.tell()
            except (AttributeError, IOError):
                dest = tellable(dest)
        self.z = zipfile.ZipFile(dest, 'w',
                                 compress and zipfile.ZIP_DEFLATED or
                                 zipfile.ZIP_STORED)
        self.date_time = time.gmtime(mtime)[:6]

    def addfile(self, name, mode, islink, data):
        i = zipfile.ZipInfo(self.prefix + name, self.date_time)
        i.compress_type = self.z.compression
        # unzip will not honor unix file modes unless file creator is
        # set to unix (id 3).
        i.create_system = 3
        ftype = stat.S_IFREG
        if islink:
            mode = 0777
            ftype = stat.S_IFLNK
        i.external_attr = (mode | ftype) << 16L
        self.z.writestr(i, data)

    def done(self):
        self.z.close()

class fileit(object):
    '''write archive as files in directory.'''

    def __init__(self, name, prefix, mtime):
        if prefix:
            raise util.Abort(_('cannot give prefix when archiving to files'))
        self.basedir = name
        self.opener = util.opener(self.basedir)

    def addfile(self, name, mode, islink, data):
        if islink:
            self.opener.symlink(data, name)
            return
        f = self.opener(name, "w", atomictemp=True)
        f.write(data)
        f.rename()
        destfile = os.path.join(self.basedir, name)
        os.chmod(destfile, mode)

    def done(self):
        pass

archivers = {
    'files': fileit,
    'tar': tarit,
    'tbz2': lambda name, prefix, mtime: tarit(name, prefix, mtime, 'bz2'),
    'tgz': lambda name, prefix, mtime: tarit(name, prefix, mtime, 'gz'),
    'uzip': lambda name, prefix, mtime: zipit(name, prefix, mtime, False),
    'zip': zipit,
    }

def archive(repo, dest, node, kind, decode=True, matchfn=None,
            prefix=None, mtime=None):
    '''create archive of repo as it was at node.

    dest can be name of directory, name of archive file, or file
    object to write archive to.

    kind is type of archive to create.

    decode tells whether to put files through decode filters from
    hgrc.

    matchfn is function to filter names of files to write to archive.

    prefix is name of path to put before every archive member.'''

    def write(name, mode, islink, getdata):
        if matchfn and not matchfn(name):
            return
        data = getdata()
        if decode:
            data = repo.wwritedata(name, data)
        archiver.addfile(name, mode, islink, data)

    if kind not in archivers:
        raise util.Abort(_("unknown archive type '%s'") % kind)

    ctx = repo[node]
    archiver = archivers[kind](dest, prefix, mtime or ctx.date()[0])

    if repo.ui.configbool("ui", "archivemeta", True):
        def metadata():
            base = 'repo: %s\nnode: %s\nbranch: %s\n' % (
                hex(repo.changelog.node(0)), hex(node), ctx.branch())

            tags = ''.join('tag: %s\n' % t for t in ctx.tags()
                           if repo.tagtype(t) == 'global')
            if not tags:
                repo.ui.pushbuffer()
                opts = {'template': '{latesttag}\n{latesttagdistance}',
                        'style': '', 'patch': None, 'git': None}
                cmdutil.show_changeset(repo.ui, repo, opts).show(ctx)
                ltags, dist = repo.ui.popbuffer().split('\n')
                tags = ''.join('latesttag: %s\n' % t for t in ltags.split(':'))
                tags += 'latesttagdistance: %s\n' % dist

            return base + tags

        write('.hg_archival.txt', 0644, False, metadata)

    for f in ctx:
        ff = ctx.flags(f)
        write(f, 'x' in ff and 0755 or 0644, 'l' in ff, ctx[f].data)
    archiver.done()
