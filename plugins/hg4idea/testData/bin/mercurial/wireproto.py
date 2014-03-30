# wireproto.py - generic wire protocol support functions
#
# Copyright 2005-2010 Matt Mackall <mpm@selenic.com>
#
# This software may be used and distributed according to the terms of the
# GNU General Public License version 2 or any later version.

import urllib, tempfile, os, sys
from i18n import _
from node import bin, hex
import changegroup as changegroupmod
import peer, error, encoding, util, store

# abstract batching support

class future(object):
    '''placeholder for a value to be set later'''
    def set(self, value):
        if util.safehasattr(self, 'value'):
            raise error.RepoError("future is already set")
        self.value = value

class batcher(object):
    '''base class for batches of commands submittable in a single request

    All methods invoked on instances of this class are simply queued and
    return a a future for the result. Once you call submit(), all the queued
    calls are performed and the results set in their respective futures.
    '''
    def __init__(self):
        self.calls = []
    def __getattr__(self, name):
        def call(*args, **opts):
            resref = future()
            self.calls.append((name, args, opts, resref,))
            return resref
        return call
    def submit(self):
        pass

class localbatch(batcher):
    '''performs the queued calls directly'''
    def __init__(self, local):
        batcher.__init__(self)
        self.local = local
    def submit(self):
        for name, args, opts, resref in self.calls:
            resref.set(getattr(self.local, name)(*args, **opts))

class remotebatch(batcher):
    '''batches the queued calls; uses as few roundtrips as possible'''
    def __init__(self, remote):
        '''remote must support _submitbatch(encbatch) and
        _submitone(op, encargs)'''
        batcher.__init__(self)
        self.remote = remote
    def submit(self):
        req, rsp = [], []
        for name, args, opts, resref in self.calls:
            mtd = getattr(self.remote, name)
            batchablefn = getattr(mtd, 'batchable', None)
            if batchablefn is not None:
                batchable = batchablefn(mtd.im_self, *args, **opts)
                encargsorres, encresref = batchable.next()
                if encresref:
                    req.append((name, encargsorres,))
                    rsp.append((batchable, encresref, resref,))
                else:
                    resref.set(encargsorres)
            else:
                if req:
                    self._submitreq(req, rsp)
                    req, rsp = [], []
                resref.set(mtd(*args, **opts))
        if req:
            self._submitreq(req, rsp)
    def _submitreq(self, req, rsp):
        encresults = self.remote._submitbatch(req)
        for encres, r in zip(encresults, rsp):
            batchable, encresref, resref = r
            encresref.set(encres)
            resref.set(batchable.next())

def batchable(f):
    '''annotation for batchable methods

    Such methods must implement a coroutine as follows:

    @batchable
    def sample(self, one, two=None):
        # Handle locally computable results first:
        if not one:
            yield "a local result", None
        # Build list of encoded arguments suitable for your wire protocol:
        encargs = [('one', encode(one),), ('two', encode(two),)]
        # Create future for injection of encoded result:
        encresref = future()
        # Return encoded arguments and future:
        yield encargs, encresref
        # Assuming the future to be filled with the result from the batched
        # request now. Decode it:
        yield decode(encresref.value)

    The decorator returns a function which wraps this coroutine as a plain
    method, but adds the original method as an attribute called "batchable",
    which is used by remotebatch to split the call into separate encoding and
    decoding phases.
    '''
    def plain(*args, **opts):
        batchable = f(*args, **opts)
        encargsorres, encresref = batchable.next()
        if not encresref:
            return encargsorres # a local result in this case
        self = args[0]
        encresref.set(self._submitone(f.func_name, encargsorres))
        return batchable.next()
    setattr(plain, 'batchable', f)
    return plain

# list of nodes encoding / decoding

def decodelist(l, sep=' '):
    if l:
        return map(bin, l.split(sep))
    return []

def encodelist(l, sep=' '):
    return sep.join(map(hex, l))

# batched call argument encoding

def escapearg(plain):
    return (plain
            .replace(':', '::')
            .replace(',', ':,')
            .replace(';', ':;')
            .replace('=', ':='))

def unescapearg(escaped):
    return (escaped
            .replace(':=', '=')
            .replace(':;', ';')
            .replace(':,', ',')
            .replace('::', ':'))

# client side

def todict(**args):
    return args

class wirepeer(peer.peerrepository):

    def batch(self):
        return remotebatch(self)
    def _submitbatch(self, req):
        cmds = []
        for op, argsdict in req:
            args = ','.join('%s=%s' % p for p in argsdict.iteritems())
            cmds.append('%s %s' % (op, args))
        rsp = self._call("batch", cmds=';'.join(cmds))
        return rsp.split(';')
    def _submitone(self, op, args):
        return self._call(op, **args)

    @batchable
    def lookup(self, key):
        self.requirecap('lookup', _('look up remote revision'))
        f = future()
        yield todict(key=encoding.fromlocal(key)), f
        d = f.value
        success, data = d[:-1].split(" ", 1)
        if int(success):
            yield bin(data)
        self._abort(error.RepoError(data))

    @batchable
    def heads(self):
        f = future()
        yield {}, f
        d = f.value
        try:
            yield decodelist(d[:-1])
        except ValueError:
            self._abort(error.ResponseError(_("unexpected response:"), d))

    @batchable
    def known(self, nodes):
        f = future()
        yield todict(nodes=encodelist(nodes)), f
        d = f.value
        try:
            yield [bool(int(f)) for f in d]
        except ValueError:
            self._abort(error.ResponseError(_("unexpected response:"), d))

    @batchable
    def branchmap(self):
        f = future()
        yield {}, f
        d = f.value
        try:
            branchmap = {}
            for branchpart in d.splitlines():
                branchname, branchheads = branchpart.split(' ', 1)
                branchname = encoding.tolocal(urllib.unquote(branchname))
                branchheads = decodelist(branchheads)
                branchmap[branchname] = branchheads
            yield branchmap
        except TypeError:
            self._abort(error.ResponseError(_("unexpected response:"), d))

    def branches(self, nodes):
        n = encodelist(nodes)
        d = self._call("branches", nodes=n)
        try:
            br = [tuple(decodelist(b)) for b in d.splitlines()]
            return br
        except ValueError:
            self._abort(error.ResponseError(_("unexpected response:"), d))

    def between(self, pairs):
        batch = 8 # avoid giant requests
        r = []
        for i in xrange(0, len(pairs), batch):
            n = " ".join([encodelist(p, '-') for p in pairs[i:i + batch]])
            d = self._call("between", pairs=n)
            try:
                r.extend(l and decodelist(l) or [] for l in d.splitlines())
            except ValueError:
                self._abort(error.ResponseError(_("unexpected response:"), d))
        return r

    @batchable
    def pushkey(self, namespace, key, old, new):
        if not self.capable('pushkey'):
            yield False, None
        f = future()
        self.ui.debug('preparing pushkey for "%s:%s"\n' % (namespace, key))
        yield todict(namespace=encoding.fromlocal(namespace),
                     key=encoding.fromlocal(key),
                     old=encoding.fromlocal(old),
                     new=encoding.fromlocal(new)), f
        d = f.value
        d, output = d.split('\n', 1)
        try:
            d = bool(int(d))
        except ValueError:
            raise error.ResponseError(
                _('push failed (unexpected response):'), d)
        for l in output.splitlines(True):
            self.ui.status(_('remote: '), l)
        yield d

    @batchable
    def listkeys(self, namespace):
        if not self.capable('pushkey'):
            yield {}, None
        f = future()
        self.ui.debug('preparing listkeys for "%s"\n' % namespace)
        yield todict(namespace=encoding.fromlocal(namespace)), f
        d = f.value
        r = {}
        for l in d.splitlines():
            k, v = l.split('\t')
            r[encoding.tolocal(k)] = encoding.tolocal(v)
        yield r

    def stream_out(self):
        return self._callstream('stream_out')

    def changegroup(self, nodes, kind):
        n = encodelist(nodes)
        f = self._callstream("changegroup", roots=n)
        return changegroupmod.unbundle10(self._decompress(f), 'UN')

    def changegroupsubset(self, bases, heads, kind):
        self.requirecap('changegroupsubset', _('look up remote changes'))
        bases = encodelist(bases)
        heads = encodelist(heads)
        f = self._callstream("changegroupsubset",
                             bases=bases, heads=heads)
        return changegroupmod.unbundle10(self._decompress(f), 'UN')

    def getbundle(self, source, heads=None, common=None):
        self.requirecap('getbundle', _('look up remote changes'))
        opts = {}
        if heads is not None:
            opts['heads'] = encodelist(heads)
        if common is not None:
            opts['common'] = encodelist(common)
        f = self._callstream("getbundle", **opts)
        return changegroupmod.unbundle10(self._decompress(f), 'UN')

    def unbundle(self, cg, heads, source):
        '''Send cg (a readable file-like object representing the
        changegroup to push, typically a chunkbuffer object) to the
        remote server as a bundle. Return an integer indicating the
        result of the push (see localrepository.addchangegroup()).'''

        if heads != ['force'] and self.capable('unbundlehash'):
            heads = encodelist(['hashed',
                                util.sha1(''.join(sorted(heads))).digest()])
        else:
            heads = encodelist(heads)

        ret, output = self._callpush("unbundle", cg, heads=heads)
        if ret == "":
            raise error.ResponseError(
                _('push failed:'), output)
        try:
            ret = int(ret)
        except ValueError:
            raise error.ResponseError(
                _('push failed (unexpected response):'), ret)

        for l in output.splitlines(True):
            self.ui.status(_('remote: '), l)
        return ret

    def debugwireargs(self, one, two, three=None, four=None, five=None):
        # don't pass optional arguments left at their default value
        opts = {}
        if three is not None:
            opts['three'] = three
        if four is not None:
            opts['four'] = four
        return self._call('debugwireargs', one=one, two=two, **opts)

# server side

class streamres(object):
    def __init__(self, gen):
        self.gen = gen

class pushres(object):
    def __init__(self, res):
        self.res = res

class pusherr(object):
    def __init__(self, res):
        self.res = res

class ooberror(object):
    def __init__(self, message):
        self.message = message

def dispatch(repo, proto, command):
    repo = repo.filtered("served")
    func, spec = commands[command]
    args = proto.getargs(spec)
    return func(repo, proto, *args)

def options(cmd, keys, others):
    opts = {}
    for k in keys:
        if k in others:
            opts[k] = others[k]
            del others[k]
    if others:
        sys.stderr.write("abort: %s got unexpected arguments %s\n"
                         % (cmd, ",".join(others)))
    return opts

def batch(repo, proto, cmds, others):
    repo = repo.filtered("served")
    res = []
    for pair in cmds.split(';'):
        op, args = pair.split(' ', 1)
        vals = {}
        for a in args.split(','):
            if a:
                n, v = a.split('=')
                vals[n] = unescapearg(v)
        func, spec = commands[op]
        if spec:
            keys = spec.split()
            data = {}
            for k in keys:
                if k == '*':
                    star = {}
                    for key in vals.keys():
                        if key not in keys:
                            star[key] = vals[key]
                    data['*'] = star
                else:
                    data[k] = vals[k]
            result = func(repo, proto, *[data[k] for k in keys])
        else:
            result = func(repo, proto)
        if isinstance(result, ooberror):
            return result
        res.append(escapearg(result))
    return ';'.join(res)

def between(repo, proto, pairs):
    pairs = [decodelist(p, '-') for p in pairs.split(" ")]
    r = []
    for b in repo.between(pairs):
        r.append(encodelist(b) + "\n")
    return "".join(r)

def branchmap(repo, proto):
    branchmap = repo.branchmap()
    heads = []
    for branch, nodes in branchmap.iteritems():
        branchname = urllib.quote(encoding.fromlocal(branch))
        branchnodes = encodelist(nodes)
        heads.append('%s %s' % (branchname, branchnodes))
    return '\n'.join(heads)

def branches(repo, proto, nodes):
    nodes = decodelist(nodes)
    r = []
    for b in repo.branches(nodes):
        r.append(encodelist(b) + "\n")
    return "".join(r)

def capabilities(repo, proto):
    caps = ('lookup changegroupsubset branchmap pushkey known getbundle '
            'unbundlehash batch').split()
    if _allowstream(repo.ui):
        if repo.ui.configbool('server', 'preferuncompressed', False):
            caps.append('stream-preferred')
        requiredformats = repo.requirements & repo.supportedformats
        # if our local revlogs are just revlogv1, add 'stream' cap
        if not requiredformats - set(('revlogv1',)):
            caps.append('stream')
        # otherwise, add 'streamreqs' detailing our local revlog format
        else:
            caps.append('streamreqs=%s' % ','.join(requiredformats))
    caps.append('unbundle=%s' % ','.join(changegroupmod.bundlepriority))
    caps.append('httpheader=1024')
    return ' '.join(caps)

def changegroup(repo, proto, roots):
    nodes = decodelist(roots)
    cg = repo.changegroup(nodes, 'serve')
    return streamres(proto.groupchunks(cg))

def changegroupsubset(repo, proto, bases, heads):
    bases = decodelist(bases)
    heads = decodelist(heads)
    cg = repo.changegroupsubset(bases, heads, 'serve')
    return streamres(proto.groupchunks(cg))

def debugwireargs(repo, proto, one, two, others):
    # only accept optional args from the known set
    opts = options('debugwireargs', ['three', 'four'], others)
    return repo.debugwireargs(one, two, **opts)

def getbundle(repo, proto, others):
    opts = options('getbundle', ['heads', 'common'], others)
    for k, v in opts.iteritems():
        opts[k] = decodelist(v)
    cg = repo.getbundle('serve', **opts)
    return streamres(proto.groupchunks(cg))

def heads(repo, proto):
    h = repo.heads()
    return encodelist(h) + "\n"

def hello(repo, proto):
    '''the hello command returns a set of lines describing various
    interesting things about the server, in an RFC822-like format.
    Currently the only one defined is "capabilities", which
    consists of a line in the form:

    capabilities: space separated list of tokens
    '''
    return "capabilities: %s\n" % (capabilities(repo, proto))

def listkeys(repo, proto, namespace):
    d = repo.listkeys(encoding.tolocal(namespace)).items()
    t = '\n'.join(['%s\t%s' % (encoding.fromlocal(k), encoding.fromlocal(v))
                   for k, v in d])
    return t

def lookup(repo, proto, key):
    try:
        k = encoding.tolocal(key)
        c = repo[k]
        r = c.hex()
        success = 1
    except Exception, inst:
        r = str(inst)
        success = 0
    return "%s %s\n" % (success, r)

def known(repo, proto, nodes, others):
    return ''.join(b and "1" or "0" for b in repo.known(decodelist(nodes)))

def pushkey(repo, proto, namespace, key, old, new):
    # compatibility with pre-1.8 clients which were accidentally
    # sending raw binary nodes rather than utf-8-encoded hex
    if len(new) == 20 and new.encode('string-escape') != new:
        # looks like it could be a binary node
        try:
            new.decode('utf-8')
            new = encoding.tolocal(new) # but cleanly decodes as UTF-8
        except UnicodeDecodeError:
            pass # binary, leave unmodified
    else:
        new = encoding.tolocal(new) # normal path

    if util.safehasattr(proto, 'restore'):

        proto.redirect()

        try:
            r = repo.pushkey(encoding.tolocal(namespace), encoding.tolocal(key),
                             encoding.tolocal(old), new) or False
        except util.Abort:
            r = False

        output = proto.restore()

        return '%s\n%s' % (int(r), output)

    r = repo.pushkey(encoding.tolocal(namespace), encoding.tolocal(key),
                     encoding.tolocal(old), new)
    return '%s\n' % int(r)

def _allowstream(ui):
    return ui.configbool('server', 'uncompressed', True, untrusted=True)

def stream(repo, proto):
    '''If the server supports streaming clone, it advertises the "stream"
    capability with a value representing the version and flags of the repo
    it is serving. Client checks to see if it understands the format.

    The format is simple: the server writes out a line with the amount
    of files, then the total amount of bytes to be transferred (separated
    by a space). Then, for each file, the server first writes the filename
    and filesize (separated by the null character), then the file contents.
    '''

    if not _allowstream(repo.ui):
        return '1\n'

    entries = []
    total_bytes = 0
    try:
        # get consistent snapshot of repo, lock during scan
        lock = repo.lock()
        try:
            repo.ui.debug('scanning\n')
            for name, ename, size in repo.store.walk():
                if size:
                    entries.append((name, size))
                    total_bytes += size
        finally:
            lock.release()
    except error.LockError:
        return '2\n' # error: 2

    def streamer(repo, entries, total):
        '''stream out all metadata files in repository.'''
        yield '0\n' # success
        repo.ui.debug('%d files, %d bytes to transfer\n' %
                      (len(entries), total_bytes))
        yield '%d %d\n' % (len(entries), total_bytes)

        sopener = repo.sopener
        oldaudit = sopener.mustaudit
        debugflag = repo.ui.debugflag
        sopener.mustaudit = False

        try:
            for name, size in entries:
                if debugflag:
                    repo.ui.debug('sending %s (%d bytes)\n' % (name, size))
                # partially encode name over the wire for backwards compat
                yield '%s\0%d\n' % (store.encodedir(name), size)
                if size <= 65536:
                    fp = sopener(name)
                    try:
                        data = fp.read(size)
                    finally:
                        fp.close()
                    yield data
                else:
                    for chunk in util.filechunkiter(sopener(name), limit=size):
                        yield chunk
        # replace with "finally:" when support for python 2.4 has been dropped
        except Exception:
            sopener.mustaudit = oldaudit
            raise
        sopener.mustaudit = oldaudit

    return streamres(streamer(repo, entries, total_bytes))

def unbundle(repo, proto, heads):
    their_heads = decodelist(heads)

    def check_heads():
        heads = repo.heads()
        heads_hash = util.sha1(''.join(sorted(heads))).digest()
        return (their_heads == ['force'] or their_heads == heads or
                their_heads == ['hashed', heads_hash])

    proto.redirect()

    # fail early if possible
    if not check_heads():
        return pusherr('repository changed while preparing changes - '
                       'please try again')

    # write bundle data to temporary file because it can be big
    fd, tempname = tempfile.mkstemp(prefix='hg-unbundle-')
    fp = os.fdopen(fd, 'wb+')
    r = 0
    try:
        proto.getfile(fp)
        lock = repo.lock()
        try:
            if not check_heads():
                # someone else committed/pushed/unbundled while we
                # were transferring data
                return pusherr('repository changed while uploading changes - '
                               'please try again')

            # push can proceed
            fp.seek(0)
            gen = changegroupmod.readbundle(fp, None)

            try:
                r = repo.addchangegroup(gen, 'serve', proto._client())
            except util.Abort, inst:
                sys.stderr.write("abort: %s\n" % inst)
        finally:
            lock.release()
        return pushres(r)

    finally:
        fp.close()
        os.unlink(tempname)

commands = {
    'batch': (batch, 'cmds *'),
    'between': (between, 'pairs'),
    'branchmap': (branchmap, ''),
    'branches': (branches, 'nodes'),
    'capabilities': (capabilities, ''),
    'changegroup': (changegroup, 'roots'),
    'changegroupsubset': (changegroupsubset, 'bases heads'),
    'debugwireargs': (debugwireargs, 'one two *'),
    'getbundle': (getbundle, '*'),
    'heads': (heads, ''),
    'hello': (hello, ''),
    'known': (known, 'nodes *'),
    'listkeys': (listkeys, 'namespace'),
    'lookup': (lookup, 'key'),
    'pushkey': (pushkey, 'namespace key old new'),
    'stream_out': (stream, ''),
    'unbundle': (unbundle, 'heads'),
}
