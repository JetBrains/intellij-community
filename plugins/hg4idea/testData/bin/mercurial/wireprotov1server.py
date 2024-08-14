# wireprotov1server.py - Wire protocol version 1 server functionality
#
# Copyright 2005-2010 Olivia Mackall <olivia@selenic.com>
#
# This software may be used and distributed according to the terms of the
# GNU General Public License version 2 or any later version.


import binascii
import os

from .i18n import _
from .node import hex

from . import (
    bundle2,
    bundlecaches,
    changegroup as changegroupmod,
    discovery,
    encoding,
    error,
    exchange,
    hook,
    pushkey as pushkeymod,
    pycompat,
    repoview,
    requirements as requirementsmod,
    streamclone,
    util,
    wireprototypes,
)

from .utils import (
    procutil,
    stringutil,
)

urlerr = util.urlerr
urlreq = util.urlreq

bundle2requiredmain = _(b'incompatible Mercurial client; bundle2 required')
bundle2requiredhint = _(
    b'see https://www.mercurial-scm.org/wiki/IncompatibleClient'
)
bundle2required = b'%s\n(%s)\n' % (bundle2requiredmain, bundle2requiredhint)


def clientcompressionsupport(proto):
    """Returns a list of compression methods supported by the client.

    Returns a list of the compression methods supported by the client
    according to the protocol capabilities. If no such capability has
    been announced, fallback to the default of zlib and uncompressed.
    """
    for cap in proto.getprotocaps():
        if cap.startswith(b'comp='):
            return cap[5:].split(b',')
    return [b'zlib', b'none']


# wire protocol command can either return a string or one of these classes.


def getdispatchrepo(repo, proto, command, accesshidden=False):
    """Obtain the repo used for processing wire protocol commands.

    The intent of this function is to serve as a monkeypatch point for
    extensions that need commands to operate on different repo views under
    specialized circumstances.
    """
    viewconfig = repo.ui.config(b'server', b'view')

    # Only works if the filter actually supports being upgraded to show hidden
    # changesets.
    if (
        accesshidden
        and viewconfig is not None
        and viewconfig + b'.hidden' in repoview.filtertable
    ):
        viewconfig += b'.hidden'

    return repo.filtered(viewconfig)


def dispatch(repo, proto, command, accesshidden=False):
    repo = getdispatchrepo(repo, proto, command, accesshidden=accesshidden)

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
        procutil.stderr.write(
            b"warning: %s ignored unexpected arguments %s\n"
            % (cmd, b",".join(others))
        )
    return opts


def bundle1allowed(repo, action):
    """Whether a bundle1 operation is allowed from the server.

    Priority is:

    1. server.bundle1gd.<action> (if generaldelta active)
    2. server.bundle1.<action>
    3. server.bundle1gd (if generaldelta active)
    4. server.bundle1
    """
    ui = repo.ui
    gd = requirementsmod.GENERALDELTA_REQUIREMENT in repo.requirements

    if gd:
        v = ui.configbool(b'server', b'bundle1gd.%s' % action)
        if v is not None:
            return v

    v = ui.configbool(b'server', b'bundle1.%s' % action)
    if v is not None:
        return v

    if gd:
        v = ui.configbool(b'server', b'bundle1gd')
        if v is not None:
            return v

    return ui.configbool(b'server', b'bundle1')


commands = wireprototypes.commanddict()


def wireprotocommand(name, args=None, permission=b'push'):
    """Decorator to declare a wire protocol command.

    ``name`` is the name of the wire protocol command being provided.

    ``args`` defines the named arguments accepted by the command. It is
    a space-delimited list of argument names. ``*`` denotes a special value
    that says to accept all named arguments.

    ``permission`` defines the permission type needed to run this command.
    Can be ``push`` or ``pull``. These roughly map to read-write and read-only,
    respectively. Default is to assume command requires ``push`` permissions
    because otherwise commands not declaring their permissions could modify
    a repository that is supposed to be read-only.
    """
    transports = {
        k for k, v in wireprototypes.TRANSPORTS.items() if v[b'version'] == 1
    }

    if permission not in (b'push', b'pull'):
        raise error.ProgrammingError(
            b'invalid wire protocol permission; '
            b'got %s; expected "push" or "pull"' % permission
        )

    if args is None:
        args = b''

    if not isinstance(args, bytes):
        raise error.ProgrammingError(
            b'arguments for version 1 commands must be declared as bytes'
        )

    def register(func):
        if name in commands:
            raise error.ProgrammingError(
                b'%s command already registered for version 1' % name
            )
        commands[name] = wireprototypes.commandentry(
            func, args=args, transports=transports, permission=permission
        )

        return func

    return register


# TODO define a more appropriate permissions type to use for this.
@wireprotocommand(b'batch', b'cmds *', permission=b'pull')
def batch(repo, proto, cmds, others):
    unescapearg = wireprototypes.unescapebatcharg
    res = []
    for pair in cmds.split(b';'):
        op, args = pair.split(b' ', 1)
        vals = {}
        for a in args.split(b','):
            if a:
                n, v = a.split(b'=')
                vals[unescapearg(n)] = unescapearg(v)
        func, spec = commands[op]

        # Validate that client has permissions to perform this command.
        perm = commands[op].permission
        assert perm in (b'push', b'pull')
        proto.checkperm(perm)

        if spec:
            keys = spec.split()
            data = {}
            for k in keys:
                if k == b'*':
                    star = {}
                    for key in vals.keys():
                        if key not in keys:
                            star[key] = vals[key]
                    data[b'*'] = star
                else:
                    data[k] = vals[k]
            result = func(repo, proto, *[data[k] for k in keys])
        else:
            result = func(repo, proto)
        if isinstance(result, wireprototypes.ooberror):
            return result

        # For now, all batchable commands must return bytesresponse or
        # raw bytes (for backwards compatibility).
        assert isinstance(result, (wireprototypes.bytesresponse, bytes))
        if isinstance(result, wireprototypes.bytesresponse):
            result = result.data
        res.append(wireprototypes.escapebatcharg(result))

    return wireprototypes.bytesresponse(b';'.join(res))


@wireprotocommand(b'between', b'pairs', permission=b'pull')
def between(repo, proto, pairs):
    pairs = [wireprototypes.decodelist(p, b'-') for p in pairs.split(b" ")]
    r = []
    for b in repo.between(pairs):
        r.append(wireprototypes.encodelist(b) + b"\n")

    return wireprototypes.bytesresponse(b''.join(r))


@wireprotocommand(b'branchmap', permission=b'pull')
def branchmap(repo, proto):
    branchmap = repo.branchmap()
    heads = []
    for branch, nodes in branchmap.items():
        branchname = urlreq.quote(encoding.fromlocal(branch))
        branchnodes = wireprototypes.encodelist(nodes)
        heads.append(b'%s %s' % (branchname, branchnodes))

    return wireprototypes.bytesresponse(b'\n'.join(heads))


@wireprotocommand(b'branches', b'nodes', permission=b'pull')
def branches(repo, proto, nodes):
    nodes = wireprototypes.decodelist(nodes)
    r = []
    for b in repo.branches(nodes):
        r.append(wireprototypes.encodelist(b) + b"\n")

    return wireprototypes.bytesresponse(b''.join(r))


@wireprotocommand(b'get_cached_bundle_inline', b'path', permission=b'pull')
def get_cached_bundle_inline(repo, proto, path):
    """
    Server command to send a clonebundle to the client
    """
    if hook.hashook(repo.ui, b'pretransmit-inline-clone-bundle'):
        hook.hook(
            repo.ui,
            repo,
            b'pretransmit-inline-clone-bundle',
            throw=True,
            clonebundlepath=path,
        )

    bundle_dir = repo.vfs.join(bundlecaches.BUNDLE_CACHE_DIR)
    clonebundlepath = repo.vfs.join(bundle_dir, path)
    if not repo.vfs.exists(clonebundlepath):
        raise error.Abort(b'clonebundle %s does not exist' % path)

    clonebundles_dir = os.path.realpath(bundle_dir)
    if not os.path.realpath(clonebundlepath).startswith(clonebundles_dir):
        raise error.Abort(b'clonebundle %s is using an illegal path' % path)

    def generator(vfs, bundle_path):
        with vfs(bundle_path) as f:
            length = os.fstat(f.fileno())[6]
            yield util.uvarintencode(length)
            for chunk in util.filechunkiter(f):
                yield chunk

    stream = generator(repo.vfs, clonebundlepath)
    return wireprototypes.streamres(gen=stream, prefer_uncompressed=True)


@wireprotocommand(b'clonebundles', b'', permission=b'pull')
def clonebundles(repo, proto):
    """A legacy version of clonebundles_manifest

    This version filtered out new url scheme (like peer-bundle-cache://) to
    avoid confusion in older clients.
    """
    manifest_contents = bundlecaches.get_manifest(repo)
    # Filter out peer-bundle-cache:// entries
    modified_manifest = []
    for line in manifest_contents.splitlines():
        if line.startswith(bundlecaches.CLONEBUNDLESCHEME):
            continue
        modified_manifest.append(line)
    modified_manifest.append(b'')
    return wireprototypes.bytesresponse(b'\n'.join(modified_manifest))


@wireprotocommand(b'clonebundles_manifest', b'*', permission=b'pull')
def clonebundles_2(repo, proto, args):
    """Server command for returning info for available bundles to seed clones.

    Clients will parse this response and determine what bundle to fetch.

    Extensions may wrap this command to filter or dynamically emit data
    depending on the request. e.g. you could advertise URLs for the closest
    data center given the client's IP address.

    The only filter on the server side is filtering out inline clonebundles
    in case a client does not support them.
    Otherwise, older clients would retrieve and error out on those.
    """
    manifest_contents = bundlecaches.get_manifest(repo)
    return wireprototypes.bytesresponse(manifest_contents)


wireprotocaps = [
    b'lookup',
    b'branchmap',
    b'pushkey',
    b'known',
    b'getbundle',
    b'unbundlehash',
]


def _capabilities(repo, proto):
    """return a list of capabilities for a repo

    This function exists to allow extensions to easily wrap capabilities
    computation

    - returns a lists: easy to alter
    - change done here will be propagated to both `capabilities` and `hello`
      command without any other action needed.
    """
    # copy to prevent modification of the global list
    caps = list(wireprotocaps)

    # Command of same name as capability isn't exposed to version 1 of
    # transports. So conditionally add it.
    if commands.commandavailable(b'changegroupsubset', proto):
        caps.append(b'changegroupsubset')

    if streamclone.allowservergeneration(repo):
        if repo.ui.configbool(b'server', b'preferuncompressed'):
            caps.append(b'stream-preferred')
        requiredformats = streamclone.streamed_requirements(repo)
        # if our local revlogs are just revlogv1, add 'stream' cap
        if not requiredformats - {requirementsmod.REVLOGV1_REQUIREMENT}:
            caps.append(b'stream')
        # otherwise, add 'streamreqs' detailing our local revlog format
        else:
            caps.append(b'streamreqs=%s' % b','.join(sorted(requiredformats)))
    if repo.ui.configbool(b'experimental', b'bundle2-advertise'):
        capsblob = bundle2.encodecaps(bundle2.getrepocaps(repo, role=b'server'))
        caps.append(b'bundle2=' + urlreq.quote(capsblob))
    caps.append(b'unbundle=%s' % b','.join(bundle2.bundlepriority))

    if repo.ui.configbool(b'experimental', b'narrow'):
        caps.append(wireprototypes.NARROWCAP)
        if repo.ui.configbool(b'experimental', b'narrowservebrokenellipses'):
            caps.append(wireprototypes.ELLIPSESCAP)

    return proto.addcapabilities(repo, caps)


# If you are writing an extension and consider wrapping this function. Wrap
# `_capabilities` instead.
@wireprotocommand(b'capabilities', permission=b'pull')
def capabilities(repo, proto):
    caps = _capabilities(repo, proto)
    return wireprototypes.bytesresponse(b' '.join(sorted(caps)))


@wireprotocommand(b'changegroup', b'roots', permission=b'pull')
def changegroup(repo, proto, roots):
    nodes = wireprototypes.decodelist(roots)
    outgoing = discovery.outgoing(
        repo, missingroots=nodes, ancestorsof=repo.heads()
    )
    cg = changegroupmod.makechangegroup(repo, outgoing, b'01', b'serve')
    gen = iter(lambda: cg.read(32768), b'')
    return wireprototypes.streamres(gen=gen)


@wireprotocommand(b'changegroupsubset', b'bases heads', permission=b'pull')
def changegroupsubset(repo, proto, bases, heads):
    bases = wireprototypes.decodelist(bases)
    heads = wireprototypes.decodelist(heads)
    outgoing = discovery.outgoing(repo, missingroots=bases, ancestorsof=heads)
    cg = changegroupmod.makechangegroup(repo, outgoing, b'01', b'serve')
    gen = iter(lambda: cg.read(32768), b'')
    return wireprototypes.streamres(gen=gen)


@wireprotocommand(b'debugwireargs', b'one two *', permission=b'pull')
def debugwireargs(repo, proto, one, two, others):
    # only accept optional args from the known set
    opts = options(b'debugwireargs', [b'three', b'four'], others)
    return wireprototypes.bytesresponse(
        repo.debugwireargs(one, two, **pycompat.strkwargs(opts))
    )


def find_pullbundle(repo, proto, opts, clheads, heads, common):
    """Return a file object for the first matching pullbundle.

    Pullbundles are specified in .hg/pullbundles.manifest similar to
    clonebundles.
    For each entry, the bundle specification is checked for compatibility:
    - Client features vs the BUNDLESPEC.
    - Revisions shared with the clients vs base revisions of the bundle.
      A bundle can be applied only if all its base revisions are known by
      the client.
    - At least one leaf of the bundle's DAG is missing on the client.
    - Every leaf of the bundle's DAG is part of node set the client wants.
      E.g. do not send a bundle of all changes if the client wants only
      one specific branch of many.
    """

    def decodehexstring(s):
        return {binascii.unhexlify(h) for h in s.split(b';')}

    manifest = repo.vfs.tryread(b'pullbundles.manifest')
    if not manifest:
        return None
    res = bundlecaches.parseclonebundlesmanifest(repo, manifest)
    res = bundlecaches.filterclonebundleentries(repo, res, pullbundles=True)
    if not res:
        return None
    cl = repo.unfiltered().changelog
    heads_anc = cl.ancestors([cl.rev(rev) for rev in heads], inclusive=True)
    common_anc = cl.ancestors([cl.rev(rev) for rev in common], inclusive=True)
    compformats = clientcompressionsupport(proto)
    for entry in res:
        comp = entry.get(b'COMPRESSION')
        altcomp = util.compengines._bundlenames.get(comp)
        if comp and comp not in compformats and altcomp not in compformats:
            continue
        # No test yet for VERSION, since V2 is supported by any client
        # that advertises partial pulls
        if b'heads' in entry:
            try:
                bundle_heads = decodehexstring(entry[b'heads'])
            except TypeError:
                # Bad heads entry
                continue
            if bundle_heads.issubset(common):
                continue  # Nothing new
            if all(cl.rev(rev) in common_anc for rev in bundle_heads):
                continue  # Still nothing new
            if any(
                cl.rev(rev) not in heads_anc and cl.rev(rev) not in common_anc
                for rev in bundle_heads
            ):
                continue
        if b'bases' in entry:
            try:
                bundle_bases = decodehexstring(entry[b'bases'])
            except TypeError:
                # Bad bases entry
                continue
            if not all(cl.rev(rev) in common_anc for rev in bundle_bases):
                continue
        path = entry[b'URL']
        repo.ui.debug(b'sending pullbundle "%s"\n' % path)
        try:
            return repo.vfs.open(path)
        except IOError:
            repo.ui.debug(b'pullbundle "%s" not accessible\n' % path)
            continue
    return None


@wireprotocommand(b'getbundle', b'*', permission=b'pull')
def getbundle(repo, proto, others):
    opts = options(
        b'getbundle', wireprototypes.GETBUNDLE_ARGUMENTS.keys(), others
    )
    for k, v in opts.items():
        keytype = wireprototypes.GETBUNDLE_ARGUMENTS[k]
        if keytype == b'nodes':
            opts[k] = wireprototypes.decodelist(v)
        elif keytype == b'csv':
            opts[k] = list(v.split(b','))
        elif keytype == b'scsv':
            opts[k] = set(v.split(b','))
        elif keytype == b'boolean':
            # Client should serialize False as '0', which is a non-empty string
            # so it evaluates as a True bool.
            if v == b'0':
                opts[k] = False
            else:
                opts[k] = bool(v)
        elif keytype != b'plain':
            raise KeyError(b'unknown getbundle option type %s' % keytype)

    if not bundle1allowed(repo, b'pull'):
        if not exchange.bundle2requested(opts.get(b'bundlecaps')):
            if proto.name == b'http-v1':
                return wireprototypes.ooberror(bundle2required)
            raise error.Abort(bundle2requiredmain, hint=bundle2requiredhint)

    try:
        clheads = set(repo.changelog.heads())
        heads = set(opts.get(b'heads', set()))
        common = set(opts.get(b'common', set()))
        common.discard(repo.nullid)
        if (
            repo.ui.configbool(b'server', b'pullbundle')
            and b'partial-pull' in proto.getprotocaps()
        ):
            # Check if a pre-built bundle covers this request.
            bundle = find_pullbundle(repo, proto, opts, clheads, heads, common)
            if bundle:
                return wireprototypes.streamres(
                    gen=util.filechunkiter(bundle), prefer_uncompressed=True
                )

        if repo.ui.configbool(b'server', b'disablefullbundle'):
            # Check to see if this is a full clone.
            changegroup = opts.get(b'cg', True)
            if changegroup and not common and clheads == heads:
                raise error.Abort(
                    _(b'server has pull-based clones disabled'),
                    hint=_(b'remove --pull if specified or upgrade Mercurial'),
                )

        info, chunks = exchange.getbundlechunks(
            repo, b'serve', **pycompat.strkwargs(opts)
        )
        prefercompressed = info.get(b'prefercompressed', True)
    except error.Abort as exc:
        # cleanly forward Abort error to the client
        if not exchange.bundle2requested(opts.get(b'bundlecaps')):
            if proto.name == b'http-v1':
                return wireprototypes.ooberror(exc.message + b'\n')
            raise  # cannot do better for bundle1 + ssh
        # bundle2 request expect a bundle2 reply
        bundler = bundle2.bundle20(repo.ui)
        manargs = [(b'message', exc.message)]
        advargs = []
        if exc.hint is not None:
            advargs.append((b'hint', exc.hint))
        bundler.addpart(bundle2.bundlepart(b'error:abort', manargs, advargs))
        chunks = bundler.getchunks()
        prefercompressed = False

    return wireprototypes.streamres(
        gen=chunks, prefer_uncompressed=not prefercompressed
    )


@wireprotocommand(b'heads', permission=b'pull')
def heads(repo, proto):
    h = repo.heads()
    return wireprototypes.bytesresponse(wireprototypes.encodelist(h) + b'\n')


@wireprotocommand(b'hello', permission=b'pull')
def hello(repo, proto):
    """Called as part of SSH handshake to obtain server info.

    Returns a list of lines describing interesting things about the
    server, in an RFC822-like format.

    Currently, the only one defined is ``capabilities``, which consists of a
    line of space separated tokens describing server abilities:

        capabilities: <token0> <token1> <token2>
    """
    caps = capabilities(repo, proto).data
    return wireprototypes.bytesresponse(b'capabilities: %s\n' % caps)


@wireprotocommand(b'listkeys', b'namespace', permission=b'pull')
def listkeys(repo, proto, namespace):
    d = sorted(repo.listkeys(encoding.tolocal(namespace)).items())
    return wireprototypes.bytesresponse(pushkeymod.encodekeys(d))


@wireprotocommand(b'lookup', b'key', permission=b'pull')
def lookup(repo, proto, key):
    try:
        k = encoding.tolocal(key)
        n = repo.lookup(k)
        r = hex(n)
        success = 1
    except Exception as inst:
        r = stringutil.forcebytestr(inst)
        success = 0
    return wireprototypes.bytesresponse(b'%d %s\n' % (success, r))


@wireprotocommand(b'known', b'nodes *', permission=b'pull')
def known(repo, proto, nodes, others):
    v = b''.join(
        b and b'1' or b'0' for b in repo.known(wireprototypes.decodelist(nodes))
    )
    return wireprototypes.bytesresponse(v)


@wireprotocommand(b'protocaps', b'caps', permission=b'pull')
def protocaps(repo, proto, caps):
    if proto.name == wireprototypes.SSHV1:
        proto._protocaps = set(caps.split(b' '))
    return wireprototypes.bytesresponse(b'OK')


@wireprotocommand(b'pushkey', b'namespace key old new', permission=b'push')
def pushkey(repo, proto, namespace, key, old, new):
    # compatibility with pre-1.8 clients which were accidentally
    # sending raw binary nodes rather than utf-8-encoded hex
    if len(new) == 20 and stringutil.escapestr(new) != new:
        # looks like it could be a binary node
        try:
            new.decode('utf-8')
            new = encoding.tolocal(new)  # but cleanly decodes as UTF-8
        except UnicodeDecodeError:
            pass  # binary, leave unmodified
    else:
        new = encoding.tolocal(new)  # normal path

    with proto.mayberedirectstdio() as output:
        r = (
            repo.pushkey(
                encoding.tolocal(namespace),
                encoding.tolocal(key),
                encoding.tolocal(old),
                new,
            )
            or False
        )

    output = output.getvalue() if output else b''
    return wireprototypes.bytesresponse(b'%d\n%s' % (int(r), output))


@wireprotocommand(b'stream_out', permission=b'pull')
def stream(repo, proto):
    """If the server supports streaming clone, it advertises the "stream"
    capability with a value representing the version and flags of the repo
    it is serving. Client checks to see if it understands the format.
    """
    return wireprototypes.streamreslegacy(streamclone.generatev1wireproto(repo))


@wireprotocommand(b'unbundle', b'heads', permission=b'push')
def unbundle(repo, proto, heads):
    their_heads = wireprototypes.decodelist(heads)

    with proto.mayberedirectstdio() as output:
        try:
            exchange.check_heads(repo, their_heads, b'preparing changes')
            cleanup = lambda: None
            try:
                payload = proto.getpayload()
                if repo.ui.configbool(b'server', b'streamunbundle'):

                    def cleanup():
                        # Ensure that the full payload is consumed, so
                        # that the connection doesn't contain trailing garbage.
                        for p in payload:
                            pass

                    fp = util.chunkbuffer(payload)
                else:
                    # write bundle data to temporary file as it can be big
                    fp, tempname = None, None

                    def cleanup():
                        if fp:
                            fp.close()
                        if tempname:
                            os.unlink(tempname)

                    fd, tempname = pycompat.mkstemp(prefix=b'hg-unbundle-')
                    repo.ui.debug(
                        b'redirecting incoming bundle to %s\n' % tempname
                    )
                    fp = os.fdopen(fd, pycompat.sysstr(b'wb+'))
                    for p in payload:
                        fp.write(p)
                    fp.seek(0)

                gen = exchange.readbundle(repo.ui, fp, None)
                if isinstance(
                    gen, changegroupmod.cg1unpacker
                ) and not bundle1allowed(repo, b'push'):
                    if proto.name == b'http-v1':
                        # need to special case http because stderr do not get to
                        # the http client on failed push so we need to abuse
                        # some other error type to make sure the message get to
                        # the user.
                        return wireprototypes.ooberror(bundle2required)
                    raise error.Abort(
                        bundle2requiredmain, hint=bundle2requiredhint
                    )

                r = exchange.unbundle(
                    repo, gen, their_heads, b'serve', proto.client()
                )
                if hasattr(r, 'addpart'):
                    # The return looks streamable, we are in the bundle2 case
                    # and should return a stream.
                    return wireprototypes.streamreslegacy(gen=r.getchunks())
                return wireprototypes.pushres(
                    r, output.getvalue() if output else b''
                )

            finally:
                cleanup()

        except (error.BundleValueError, error.Abort, error.PushRaced) as exc:
            # handle non-bundle2 case first
            if not getattr(exc, 'duringunbundle2', False):
                try:
                    raise
                except error.Abort as exc:
                    # The old code we moved used procutil.stderr directly.
                    # We did not change it to minimise code change.
                    # This need to be moved to something proper.
                    # Feel free to do it.
                    procutil.stderr.write(exc.format())
                    procutil.stderr.flush()
                    return wireprototypes.pushres(
                        0, output.getvalue() if output else b''
                    )
                except error.PushRaced:
                    return wireprototypes.pusherr(
                        pycompat.bytestr(exc),
                        output.getvalue() if output else b'',
                    )

            bundler = bundle2.bundle20(repo.ui)
            for out in getattr(exc, '_bundle2salvagedoutput', ()):
                bundler.addpart(out)
            try:
                try:
                    raise
                except error.PushkeyFailed as exc:
                    # check client caps
                    remotecaps = getattr(exc, '_replycaps', None)
                    if (
                        remotecaps is not None
                        and b'pushkey' not in remotecaps.get(b'error', ())
                    ):
                        # no support remote side, fallback to Abort handler.
                        raise
                    part = bundler.newpart(b'error:pushkey')
                    part.addparam(b'in-reply-to', exc.partid)
                    if exc.namespace is not None:
                        part.addparam(
                            b'namespace', exc.namespace, mandatory=False
                        )
                    if exc.key is not None:
                        part.addparam(b'key', exc.key, mandatory=False)
                    if exc.new is not None:
                        part.addparam(b'new', exc.new, mandatory=False)
                    if exc.old is not None:
                        part.addparam(b'old', exc.old, mandatory=False)
                    if exc.ret is not None:
                        part.addparam(b'ret', exc.ret, mandatory=False)
            except error.BundleValueError as exc:
                errpart = bundler.newpart(b'error:unsupportedcontent')
                if exc.parttype is not None:
                    errpart.addparam(b'parttype', exc.parttype)
                if exc.params:
                    errpart.addparam(b'params', b'\0'.join(exc.params))
            except error.Abort as exc:
                manargs = [(b'message', exc.message)]
                advargs = []
                if exc.hint is not None:
                    advargs.append((b'hint', exc.hint))
                bundler.addpart(
                    bundle2.bundlepart(b'error:abort', manargs, advargs)
                )
            except error.PushRaced as exc:
                bundler.newpart(
                    b'error:pushraced',
                    [(b'message', stringutil.forcebytestr(exc))],
                )
            return wireprototypes.streamreslegacy(gen=bundler.getchunks())
