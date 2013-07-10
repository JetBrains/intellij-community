# archival.py - revision archival for mercurial
#
# Copyright 2006 Vadim Gelfer <vadim.gelfer@gmail.com>
#
# This software may be used and distributed according to the terms of the
# GNU General Public License version 2 or any later version.

from i18n import _
from node import hex
import match as matchmod
import cmdutil
import scmutil, util, encoding
import cStringIO, os, tarfile, time, zipfile
import zlib, gzip
import struct
import error

# from unzip source code:
_UNX_IFREG = 0x8000
_UNX_IFLNK = 0xa000

def tidyprefix(dest, kind, prefix):
    '''choose prefix to use for names in archive.  make sure prefix is
    safe for consumers.'''

    if prefix:
        prefix = util.normpath(prefix)
    else:
        if not isinstance(dest, str):
            raise ValueError('dest must be string if no prefix')
        prefix = os.path.basename(dest)
        lower = prefix.lower()
        for sfx in exts.get(kind, []):
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

exts = {
    'tar': ['.tar'],
    'tbz2': ['.tbz2', '.tar.bz2'],
    'tgz': ['.tgz', '.tar.gz'],
    'zip': ['.zip'],
    }

def guesskind(dest):
    for kind, extensions in exts.iteritems():
        if util.any(dest.endswith(ext) for ext in extensions):
            return kind
    return None


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
            # Python 2.6 introduced self.name and deprecated self.filename
            try:
                fname = self.name
            except AttributeError:
                fname = self.filename
            if fname and fname.endswith('.gz'):
                fname = fname[:-3]
            flags = 0
            if fname:
                flags = gzip.FNAME
            self.fileobj.write(chr(flags))
            gzip.write32u(self.fileobj, long(self.timestamp))
            self.fileobj.write('\002')
            self.fileobj.write('\377')
            if fname:
                self.fileobj.write(fname + '\000')

    def __init__(self, dest, mtime, kind=''):
        self.mtime = mtime
        self.fileobj = None

        def taropen(name, mode, fileobj=None):
            if kind == 'gz':
                mode = mode[0]
                if not fileobj:
                    fileobj = open(name, mode + 'b')
                gzfileobj = self.GzipFileWithTime(name, mode + 'b',
                                                  zlib.Z_BEST_COMPRESSION,
                                                  fileobj, timestamp=mtime)
                self.fileobj = gzfileobj
                return tarfile.TarFile.taropen(name, mode, gzfileobj)
            else:
                return tarfile.open(name, mode + kind, fileobj)

        if isinstance(dest, str):
            self.z = taropen(dest, mode='w:')
        else:
            # Python 2.5-2.5.1 have a regression that requires a name arg
            self.z = taropen(name='', mode='w|', fileobj=dest)

    def addfile(self, name, mode, islink, data):
        i = tarfile.TarInfo(name)
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
        if self.fileobj:
            self.fileobj.close()

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

    def __init__(self, dest, mtime, compress=True):
        if not isinstance(dest, str):
            try:
                dest.tell()
            except (AttributeError, IOError):
                dest = tellable(dest)
        self.z = zipfile.ZipFile(dest, 'w',
                                 compress and zipfile.ZIP_DEFLATED or
                                 zipfile.ZIP_STORED)

        # Python's zipfile module emits deprecation warnings if we try
        # to store files with a date before 1980.
        epoch = 315532800 # calendar.timegm((1980, 1, 1, 0, 0, 0, 1, 1, 0))
        if mtime < epoch:
            mtime = epoch

        self.mtime = mtime
        self.date_time = time.gmtime(mtime)[:6]

    def addfile(self, name, mode, islink, data):
        i = zipfile.ZipInfo(name, self.date_time)
        i.compress_type = self.z.compression
        # unzip will not honor unix file modes unless file creator is
        # set to unix (id 3).
        i.create_system = 3
        ftype = _UNX_IFREG
        if islink:
            mode = 0777
            ftype = _UNX_IFLNK
        i.external_attr = (mode | ftype) << 16L
        # add "extended-timestamp" extra block, because zip archives
        # without this will be extracted with unexpected timestamp,
        # if TZ is not configured as GMT
        i.extra += struct.pack('<hhBl',
                               0x5455,     # block type: "extended-timestamp"
                               1 + 4,      # size of this block
                               1,          # "modification time is present"
                               int(self.mtime)) # last modification (UTC)
        self.z.writestr(i, data)

    def done(self):
        self.z.close()

class fileit(object):
    '''write archive as files in directory.'''

    def __init__(self, name, mtime):
        self.basedir = name
        self.opener = scmutil.opener(self.basedir)

    def addfile(self, name, mode, islink, data):
        if islink:
            self.opener.symlink(data, name)
            return
        f = self.opener(name, "w", atomictemp=True)
        f.write(data)
        f.close()
        destfile = os.path.join(self.basedir, name)
        os.chmod(destfile, mode)

    def done(self):
        pass

archivers = {
    'files': fileit,
    'tar': tarit,
    'tbz2': lambda name, mtime: tarit(name, mtime, 'bz2'),
    'tgz': lambda name, mtime: tarit(name, mtime, 'gz'),
    'uzip': lambda name, mtime: zipit(name, mtime, False),
    'zip': zipit,
    }

def archive(repo, dest, node, kind, decode=True, matchfn=None,
            prefix=None, mtime=None, subrepos=False):
    '''create archive of repo as it was at node.

    dest can be name of directory, name of archive file, or file
    object to write archive to.

    kind is type of archive to create.

    decode tells whether to put files through decode filters from
    hgrc.

    matchfn is function to filter names of files to write to archive.

    prefix is name of path to put before every archive member.'''

    if kind == 'files':
        if prefix:
            raise util.Abort(_('cannot give prefix when archiving to files'))
    else:
        prefix = tidyprefix(dest, kind, prefix)

    def write(name, mode, islink, getdata):
        data = getdata()
        if decode:
            data = repo.wwritedata(name, data)
        archiver.addfile(prefix + name, mode, islink, data)

    if kind not in archivers:
        raise util.Abort(_("unknown archive type '%s'") % kind)

    ctx = repo[node]
    archiver = archivers[kind](dest, mtime or ctx.date()[0])

    if repo.ui.configbool("ui", "archivemeta", True):
        def metadata():
            base = 'repo: %s\nnode: %s\nbranch: %s\n' % (
                repo[0].hex(), hex(node), encoding.fromlocal(ctx.branch()))

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

        name = '.hg_archival.txt'
        if not matchfn or matchfn(name):
            write(name, 0644, False, metadata)

    if matchfn:
        files = [f for f in ctx.manifest().keys() if matchfn(f)]
    else:
        files = ctx.manifest().keys()
    total = len(files)
    if total:
        files.sort()
        repo.ui.progress(_('archiving'), 0, unit=_('files'), total=total)
        for i, f in enumerate(files):
            ff = ctx.flags(f)
            write(f, 'x' in ff and 0755 or 0644, 'l' in ff, ctx[f].data)
            repo.ui.progress(_('archiving'), i + 1, item=f,
                             unit=_('files'), total=total)
        repo.ui.progress(_('archiving'), None)

    if subrepos:
        for subpath in sorted(ctx.substate):
            sub = ctx.sub(subpath)
            submatch = matchmod.narrowmatcher(subpath, matchfn)
            total += sub.archive(repo.ui, archiver, prefix, submatch)

    if total == 0:
        raise error.Abort(_('no files match the archive pattern'))

    archiver.done()
    return total
