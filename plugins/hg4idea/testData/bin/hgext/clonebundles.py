# This software may be used and distributed according to the terms of the
# GNU General Public License version 2 or any later version.

"""advertise pre-generated bundles to seed clones

"clonebundles" is a server-side extension used to advertise the existence
of pre-generated, externally hosted bundle files to clients that are
cloning so that cloning can be faster, more reliable, and require less
resources on the server. "pullbundles" is a related feature for sending
pre-generated bundle files to clients as part of pull operations.

Cloning can be a CPU and I/O intensive operation on servers. Traditionally,
the server, in response to a client's request to clone, dynamically generates
a bundle containing the entire repository content and sends it to the client.
There is no caching on the server and the server will have to redundantly
generate the same outgoing bundle in response to each clone request. For
servers with large repositories or with high clone volume, the load from
clones can make scaling the server challenging and costly.

This extension provides server operators the ability to offload
potentially expensive clone load to an external service. Pre-generated
bundles also allow using more CPU intensive compression, reducing the
effective bandwidth requirements.

Here's how clone bundles work:

1. A server operator establishes a mechanism for making bundle files available
   on a hosting service where Mercurial clients can fetch them.
2. A manifest file listing available bundle URLs and some optional metadata
   is added to the Mercurial repository on the server.
3. A client initiates a clone against a clone bundles aware server.
4. The client sees the server is advertising clone bundles and fetches the
   manifest listing available bundles.
5. The client filters and sorts the available bundles based on what it
   supports and prefers.
6. The client downloads and applies an available bundle from the
   server-specified URL.
7. The client reconnects to the original server and performs the equivalent
   of :hg:`pull` to retrieve all repository data not in the bundle. (The
   repository could have been updated between when the bundle was created
   and when the client started the clone.) This may use "pullbundles".

Instead of the server generating full repository bundles for every clone
request, it generates full bundles once and they are subsequently reused to
bootstrap new clones. The server may still transfer data at clone time.
However, this is only data that has been added/changed since the bundle was
created. For large, established repositories, this can reduce server load for
clones to less than 1% of original.

Here's how pullbundles work:

1. A manifest file listing available bundles and describing the revisions
   is added to the Mercurial repository on the server.
2. A new-enough client informs the server that it supports partial pulls
   and initiates a pull.
3. If the server has pull bundles enabled and sees the client advertising
   partial pulls, it checks for a matching pull bundle in the manifest.
   A bundle matches if the format is supported by the client, the client
   has the required revisions already and needs something from the bundle.
4. If there is at least one matching bundle, the server sends it to the client.
5. The client applies the bundle and notices that the server reply was
   incomplete. It initiates another pull.

To work, this extension requires the following of server operators:

* Generating bundle files of repository content (typically periodically,
  such as once per day).
* Clone bundles: A file server that clients have network access to and that
  Python knows how to talk to through its normal URL handling facility
  (typically an HTTP/HTTPS server).
* A process for keeping the bundles manifest in sync with available bundle
  files.

Strictly speaking, using a static file hosting server isn't required: a server
operator could use a dynamic service for retrieving bundle data. However,
static file hosting services are simple and scalable and should be sufficient
for most needs.

Bundle files can be generated with the :hg:`bundle` command. Typically
:hg:`bundle --all` is used to produce a bundle of the entire repository.

The bundlespec option `stream` (see :hg:`help bundlespec`)
can be used to produce a special *streaming clonebundle*, typically using
:hg:`bundle --all --type="none-streamv2"`.
These are bundle files that are extremely efficient
to produce and consume (read: fast). However, they are larger than
traditional bundle formats and require that clients support the exact set
of repository data store formats in use by the repository that created them.
Typically, a newer server can serve data that is compatible with older clients.
However, *streaming clone bundles* don't have this guarantee. **Server
operators need to be aware that newer versions of Mercurial may produce
streaming clone bundles incompatible with older Mercurial versions.**

A server operator is responsible for creating a ``.hg/clonebundles.manifest``
file containing the list of available bundle files suitable for seeding
clones. If this file does not exist, the repository will not advertise the
existence of clone bundles when clients connect. For pull bundles,
``.hg/pullbundles.manifest`` is used.

The manifest file contains a newline (\\n) delimited list of entries.

Each line in this file defines an available bundle. Lines have the format:

    <URL> [<key>=<value>[ <key>=<value>]]

That is, a URL followed by an optional, space-delimited list of key=value
pairs describing additional properties of this bundle. Both keys and values
are URI encoded.

For pull bundles, the URL is a path under the ``.hg`` directory of the
repository.

Keys in UPPERCASE are reserved for use by Mercurial and are defined below.
All non-uppercase keys can be used by site installations. An example use
for custom properties is to use the *datacenter* attribute to define which
data center a file is hosted in. Clients could then prefer a server in the
data center closest to them.

The following reserved keys are currently defined:

BUNDLESPEC
   A "bundle specification" string that describes the type of the bundle.

   These are string values that are accepted by the "--type" argument of
   :hg:`bundle`.

   The values are parsed in strict mode, which means they must be of the
   "<compression>-<type>" form. See
   mercurial.exchange.parsebundlespec() for more details.

   :hg:`debugbundle --spec` can be used to print the bundle specification
   string for a bundle file. The output of this command can be used verbatim
   for the value of ``BUNDLESPEC`` (it is already escaped).

   Clients will automatically filter out specifications that are unknown or
   unsupported so they won't attempt to download something that likely won't
   apply.

   The actual value doesn't impact client behavior beyond filtering:
   clients will still sniff the bundle type from the header of downloaded
   files.

   **Use of this key is highly recommended**, as it allows clients to
   easily skip unsupported bundles. If this key is not defined, an old
   client may attempt to apply a bundle that it is incapable of reading.

REQUIRESNI
   Whether Server Name Indication (SNI) is required to connect to the URL.
   SNI allows servers to use multiple certificates on the same IP. It is
   somewhat common in CDNs and other hosting providers. Older Python
   versions do not support SNI. Defining this attribute enables clients
   with older Python versions to filter this entry without experiencing
   an opaque SSL failure at connection time.

   If this is defined, it is important to advertise a non-SNI fallback
   URL or clients running old Python releases may not be able to clone
   with the clonebundles facility.

   Value should be "true".

REQUIREDRAM
   Value specifies expected memory requirements to decode the payload.
   Values can have suffixes for common bytes sizes. e.g. "64MB".

   This key is often used with zstd-compressed bundles using a high
   compression level / window size, which can require 100+ MB of memory
   to decode.

heads
   Used for pull bundles. This contains the ``;`` separated changeset
   hashes of the heads of the bundle content.

bases
   Used for pull bundles. This contains the ``;`` separated changeset
   hashes of the roots of the bundle content. This can be skipped if
   the bundle was created without ``--base``.

Manifests can contain multiple entries. Assuming metadata is defined, clients
will filter entries from the manifest that they don't support. The remaining
entries are optionally sorted by client preferences
(``ui.clonebundleprefers`` config option). The client then attempts
to fetch the bundle at the first URL in the remaining list.

**Errors when downloading a bundle will fail the entire clone operation:
clients do not automatically fall back to a traditional clone.** The reason
for this is that if a server is using clone bundles, it is probably doing so
because the feature is necessary to help it scale. In other words, there
is an assumption that clone load will be offloaded to another service and
that the Mercurial server isn't responsible for serving this clone load.
If that other service experiences issues and clients start mass falling back to
the original Mercurial server, the added clone load could overwhelm the server
due to unexpected load and effectively take it offline. Not having clients
automatically fall back to cloning from the original server mitigates this
scenario.

Because there is no automatic Mercurial server fallback on failure of the
bundle hosting service, it is important for server operators to view the bundle
hosting service as an extension of the Mercurial server in terms of
availability and service level agreements: if the bundle hosting service goes
down, so does the ability for clients to clone. Note: clients will see a
message informing them how to bypass the clone bundles facility when a failure
occurs. So server operators should prepare for some people to follow these
instructions when a failure occurs, thus driving more load to the original
Mercurial server when the bundle hosting service fails.


inline clonebundles
-------------------

It is possible to transmit clonebundles inline in case repositories are
accessed over SSH. This avoids having to setup an external HTTPS server
and results in the same access control as already present for the SSH setup.

Inline clonebundles should be placed into the `.hg/bundle-cache` directory.
A clonebundle at `.hg/bundle-cache/mybundle.bundle` is referred to
in the `clonebundles.manifest` file as `peer-bundle-cache://mybundle.bundle`.


auto-generation of clone bundles
--------------------------------

It is possible to set Mercurial to automatically re-generate clone bundles when
enough new content is available.

Mercurial will take care of the process asynchronously. The defined list of
bundle-type will be generated, uploaded, and advertised. Older bundles will get
decommissioned as newer ones replace them.

Bundles Generation:
...................

The extension can generate multiple variants of the clone bundle. Each
different variant will be defined by the "bundle-spec" they use::

    [clone-bundles]
    auto-generate.formats= zstd-v2, gzip-v2

See `hg help bundlespec` for details about available options.

By default, new bundles are generated when 5% of the repository contents or at
least 1000 revisions are not contained in the cached bundles. This option can
be controlled by the `clone-bundles.trigger.below-bundled-ratio` option
(default 0.95) and the `clone-bundles.trigger.revs` option (default 1000)::

    [clone-bundles]
    trigger.below-bundled-ratio=0.95
    trigger.revs=1000

This logic can be manually triggered using the `admin::clone-bundles-refresh`
command, or automatically on each repository change if
`clone-bundles.auto-generate.on-change` is set to `yes`::

    [clone-bundles]
    auto-generate.on-change=yes
    auto-generate.formats= zstd-v2, gzip-v2

Automatic Inline serving
........................

The simplest way to serve the generated bundle is through the Mercurial
protocol. However it is not the most efficient as request will still be served
by that main server. It is useful in case where authentication is complexe or
when an efficient mirror system is already in use anyway. See the `inline
clonebundles` section above for details about inline clonebundles

To automatically serve generated bundle through inline clonebundle, simply set
the following option::

    auto-generate.serve-inline=yes

Enabling this option disable the managed upload and serving explained below.

Bundles Upload and Serving:
...........................

This is the most efficient way to serve automatically generated clone bundles,
but requires some setup.

The generated bundles need to be made available to users through a "public" URL.
This should be donne through `clone-bundles.upload-command` configuration. The
value of this command should be a shell command. It will have access to the
bundle file path through the `$HGCB_BUNDLE_PATH` variable. And the expected
basename in the "public" URL is accessible at::

  [clone-bundles]
  upload-command=sftp put $HGCB_BUNDLE_PATH \
      sftp://bundles.host/clone-bundles/$HGCB_BUNDLE_BASENAME

If the file was already uploaded, the command must still succeed.

After upload, the file should be available at an url defined by
`clone-bundles.url-template`.

  [clone-bundles]
  url-template=https://bundles.host/cache/clone-bundles/{basename}

Old bundles cleanup:
....................

When new bundles are generated, the older ones are no longer necessary and can
be removed from storage. This is done through the `clone-bundles.delete-command`
configuration. The command is given the url of the artifact to delete through
the `$HGCB_BUNDLE_URL` environment variable.

  [clone-bundles]
  delete-command=sftp rm sftp://bundles.host/clone-bundles/$HGCB_BUNDLE_BASENAME

If the file was already deleted, the command must still succeed.
"""


import os
import weakref

from mercurial.i18n import _

from mercurial import (
    bundlecaches,
    commands,
    error,
    extensions,
    localrepo,
    lock,
    node,
    registrar,
    util,
    wireprotov1server,
)


from mercurial.utils import (
    procutil,
)

testedwith = b'ships-with-hg-core'


def capabilities(orig, repo, proto):
    caps = orig(repo, proto)

    # Only advertise if a manifest exists. This does add some I/O to requests.
    # But this should be cheaper than a wasted network round trip due to
    # missing file.
    if repo.vfs.exists(bundlecaches.CB_MANIFEST_FILE):
        caps.append(b'clonebundles')
        caps.append(b'clonebundles_manifest')

    return caps


def extsetup(ui):
    extensions.wrapfunction(wireprotov1server, '_capabilities', capabilities)


# logic for bundle auto-generation


configtable = {}
configitem = registrar.configitem(configtable)

cmdtable = {}
command = registrar.command(cmdtable)

configitem(b'clone-bundles', b'auto-generate.on-change', default=False)
configitem(b'clone-bundles', b'auto-generate.formats', default=list)
configitem(b'clone-bundles', b'auto-generate.serve-inline', default=False)
configitem(b'clone-bundles', b'trigger.below-bundled-ratio', default=0.95)
configitem(b'clone-bundles', b'trigger.revs', default=1000)

configitem(b'clone-bundles', b'upload-command', default=None)

configitem(b'clone-bundles', b'delete-command', default=None)

configitem(b'clone-bundles', b'url-template', default=None)

configitem(b'devel', b'debug.clonebundles', default=False)


# category for the post-close transaction hooks
CAT_POSTCLOSE = b"clonebundles-autobundles"

# template for bundle file names
BUNDLE_MASK = (
    b"full-%(bundle_type)s-%(revs)d_revs-%(tip_short)s_tip-%(op_id)s.hg"
)


# file in .hg/ use to track clonebundles being auto-generated
AUTO_GEN_FILE = b'clonebundles.auto-gen'


class BundleBase(object):
    """represents the core of properties that matters for us in a bundle

    :bundle_type: the bundlespec (see hg help bundlespec)
    :revs:        the number of revisions in the repo at bundle creation time
    :tip_rev:     the rev-num of the tip revision
    :tip_node:    the node id of the tip-most revision in the bundle

    :ready:       True if the bundle is ready to be served
    """

    ready = False

    def __init__(self, bundle_type, revs, tip_rev, tip_node):
        self.bundle_type = bundle_type
        self.revs = revs
        self.tip_rev = tip_rev
        self.tip_node = tip_node

    def valid_for(self, repo):
        """is this bundle applicable to the current repository

        This is useful for detecting bundles made irrelevant by stripping.
        """
        tip_node = node.bin(self.tip_node)
        return repo.changelog.index.get_rev(tip_node) == self.tip_rev

    def __eq__(self, other):
        left = (self.ready, self.bundle_type, self.tip_rev, self.tip_node)
        right = (other.ready, other.bundle_type, other.tip_rev, other.tip_node)
        return left == right

    def __neq__(self, other):
        return not self == other

    def __cmp__(self, other):
        if self == other:
            return 0
        return -1


class RequestedBundle(BundleBase):
    """A bundle that should be generated.

    Additional attributes compared to BundleBase
    :heads:       list of head revisions (as rev-num)
    :op_id:       a "unique" identifier for the operation triggering the change
    """

    def __init__(self, bundle_type, revs, tip_rev, tip_node, head_revs, op_id):
        self.head_revs = head_revs
        self.op_id = op_id
        super(RequestedBundle, self).__init__(
            bundle_type,
            revs,
            tip_rev,
            tip_node,
        )

    @property
    def suggested_filename(self):
        """A filename that can be used for the generated bundle"""
        data = {
            b'bundle_type': self.bundle_type,
            b'revs': self.revs,
            b'heads': self.head_revs,
            b'tip_rev': self.tip_rev,
            b'tip_node': self.tip_node,
            b'tip_short': self.tip_node[:12],
            b'op_id': self.op_id,
        }
        return BUNDLE_MASK % data

    def generate_bundle(self, repo, file_path):
        """generate the bundle at `filepath`"""
        commands.bundle(
            repo.ui,
            repo,
            file_path,
            base=[b"null"],
            rev=self.head_revs,
            type=self.bundle_type,
            quiet=True,
        )

    def generating(self, file_path, hostname=None, pid=None):
        """return a GeneratingBundle object from this object"""
        if pid is None:
            pid = os.getpid()
        if hostname is None:
            hostname = lock._getlockprefix()
        return GeneratingBundle(
            self.bundle_type,
            self.revs,
            self.tip_rev,
            self.tip_node,
            hostname,
            pid,
            file_path,
        )


class GeneratingBundle(BundleBase):
    """A bundle being generated

    extra attributes compared to BundleBase:

    :hostname: the hostname of the machine generating the bundle
    :pid:      the pid of the process generating the bundle
    :filepath: the target filename of the bundle

    These attributes exist to help detect stalled generation processes.
    """

    ready = False

    def __init__(
        self, bundle_type, revs, tip_rev, tip_node, hostname, pid, filepath
    ):
        self.hostname = hostname
        self.pid = pid
        self.filepath = filepath
        super(GeneratingBundle, self).__init__(
            bundle_type, revs, tip_rev, tip_node
        )

    @classmethod
    def from_line(cls, line):
        """create an object by deserializing a line from AUTO_GEN_FILE"""
        assert line.startswith(b'PENDING-v1 ')
        (
            __,
            bundle_type,
            revs,
            tip_rev,
            tip_node,
            hostname,
            pid,
            filepath,
        ) = line.split()
        hostname = util.urlreq.unquote(hostname)
        filepath = util.urlreq.unquote(filepath)
        revs = int(revs)
        tip_rev = int(tip_rev)
        pid = int(pid)
        return cls(
            bundle_type, revs, tip_rev, tip_node, hostname, pid, filepath
        )

    def to_line(self):
        """serialize the object to include as a line in AUTO_GEN_FILE"""
        templ = b"PENDING-v1 %s %d %d %s %s %d %s"
        data = (
            self.bundle_type,
            self.revs,
            self.tip_rev,
            self.tip_node,
            util.urlreq.quote(self.hostname),
            self.pid,
            util.urlreq.quote(self.filepath),
        )
        return templ % data

    def __eq__(self, other):
        if not super(GeneratingBundle, self).__eq__(other):
            return False
        left = (self.hostname, self.pid, self.filepath)
        right = (other.hostname, other.pid, other.filepath)
        return left == right

    def uploaded(self, url, basename):
        """return a GeneratedBundle from this object"""
        return GeneratedBundle(
            self.bundle_type,
            self.revs,
            self.tip_rev,
            self.tip_node,
            url,
            basename,
        )


class GeneratedBundle(BundleBase):
    """A bundle that is done being generated and can be served

    extra attributes compared to BundleBase:

    :file_url: the url where the bundle is available.
    :basename: the "basename" used to upload (useful for deletion)

    These attributes exist to generate a bundle manifest
    (.hg/pullbundles.manifest)
    """

    ready = True

    def __init__(
        self, bundle_type, revs, tip_rev, tip_node, file_url, basename
    ):
        self.file_url = file_url
        self.basename = basename
        super(GeneratedBundle, self).__init__(
            bundle_type, revs, tip_rev, tip_node
        )

    @classmethod
    def from_line(cls, line):
        """create an object by deserializing a line from AUTO_GEN_FILE"""
        assert line.startswith(b'DONE-v1 ')
        (
            __,
            bundle_type,
            revs,
            tip_rev,
            tip_node,
            file_url,
            basename,
        ) = line.split()
        revs = int(revs)
        tip_rev = int(tip_rev)
        file_url = util.urlreq.unquote(file_url)
        return cls(bundle_type, revs, tip_rev, tip_node, file_url, basename)

    def to_line(self):
        """serialize the object to include as a line in AUTO_GEN_FILE"""
        templ = b"DONE-v1 %s %d %d %s %s %s"
        data = (
            self.bundle_type,
            self.revs,
            self.tip_rev,
            self.tip_node,
            util.urlreq.quote(self.file_url),
            self.basename,
        )
        return templ % data

    def manifest_line(self):
        """serialize the object to include as a line in pullbundles.manifest"""
        templ = b"%s BUNDLESPEC=%s"
        if self.file_url.startswith(b'http'):
            templ += b" REQUIRESNI=true"
        return templ % (self.file_url, self.bundle_type)

    def __eq__(self, other):
        if not super(GeneratedBundle, self).__eq__(other):
            return False
        return self.file_url == other.file_url


def parse_auto_gen(content):
    """parse the AUTO_GEN_FILE to return a list of Bundle object"""
    bundles = []
    for line in content.splitlines():
        if line.startswith(b'PENDING-v1 '):
            bundles.append(GeneratingBundle.from_line(line))
        elif line.startswith(b'DONE-v1 '):
            bundles.append(GeneratedBundle.from_line(line))
    return bundles


def dumps_auto_gen(bundles):
    """serialize a list of Bundle as a AUTO_GEN_FILE content"""
    lines = []
    for b in bundles:
        lines.append(b"%s\n" % b.to_line())
    lines.sort()
    return b"".join(lines)


def read_auto_gen(repo):
    """read the AUTO_GEN_FILE for the <repo> a list of Bundle object"""
    data = repo.vfs.tryread(AUTO_GEN_FILE)
    if not data:
        return []
    return parse_auto_gen(data)


def write_auto_gen(repo, bundles):
    """write a list of Bundle objects into the repo's AUTO_GEN_FILE"""
    assert repo._cb_lock_ref is not None
    data = dumps_auto_gen(bundles)
    with repo.vfs(AUTO_GEN_FILE, mode=b'wb', atomictemp=True) as f:
        f.write(data)


def generate_manifest(bundles):
    """write a list of Bundle objects into the repo's AUTO_GEN_FILE"""
    bundles = list(bundles)
    bundles.sort(key=lambda b: b.bundle_type)
    lines = []
    for b in bundles:
        lines.append(b"%s\n" % b.manifest_line())
    return b"".join(lines)


def update_ondisk_manifest(repo):
    """update the clonebundle manifest with latest url"""
    with repo.clonebundles_lock():
        bundles = read_auto_gen(repo)

        per_types = {}
        for b in bundles:
            if not (b.ready and b.valid_for(repo)):
                continue
            current = per_types.get(b.bundle_type)
            if current is not None and current.revs >= b.revs:
                continue
            per_types[b.bundle_type] = b
        manifest = generate_manifest(per_types.values())
        with repo.vfs(
            bundlecaches.CB_MANIFEST_FILE, mode=b"wb", atomictemp=True
        ) as f:
            f.write(manifest)


def update_bundle_list(repo, new_bundles=(), del_bundles=()):
    """modify the repo's AUTO_GEN_FILE

    This method also regenerates the clone bundle manifest when needed"""
    with repo.clonebundles_lock():
        bundles = read_auto_gen(repo)
        if del_bundles:
            bundles = [b for b in bundles if b not in del_bundles]
        new_bundles = [b for b in new_bundles if b not in bundles]
        bundles.extend(new_bundles)
        write_auto_gen(repo, bundles)
        all_changed = []
        all_changed.extend(new_bundles)
        all_changed.extend(del_bundles)
        if any(b.ready for b in all_changed):
            update_ondisk_manifest(repo)


def cleanup_tmp_bundle(repo, target):
    """remove a GeneratingBundle file and entry"""
    assert not target.ready
    with repo.clonebundles_lock():
        repo.vfs.tryunlink(target.filepath)
        update_bundle_list(repo, del_bundles=[target])


def finalize_one_bundle(repo, target):
    """upload a generated bundle and advertise it in the clonebundles.manifest"""
    with repo.clonebundles_lock():
        bundles = read_auto_gen(repo)
        if target in bundles and target.valid_for(repo):
            result = upload_bundle(repo, target)
            update_bundle_list(repo, new_bundles=[result])
    cleanup_tmp_bundle(repo, target)


def find_outdated_bundles(repo, bundles):
    """finds outdated bundles"""
    olds = []
    per_types = {}
    for b in bundles:
        if not b.valid_for(repo):
            olds.append(b)
            continue
        l = per_types.setdefault(b.bundle_type, [])
        l.append(b)
    for key in sorted(per_types):
        all = per_types[key]
        if len(all) > 1:
            all.sort(key=lambda b: b.revs, reverse=True)
            olds.extend(all[1:])
    return olds


def collect_garbage(repo):
    """finds outdated bundles and get them deleted"""
    with repo.clonebundles_lock():
        bundles = read_auto_gen(repo)
        olds = find_outdated_bundles(repo, bundles)
        for o in olds:
            delete_bundle(repo, o)
        update_bundle_list(repo, del_bundles=olds)


def upload_bundle(repo, bundle):
    """upload the result of a GeneratingBundle and return a GeneratedBundle

    The upload is done using the `clone-bundles.upload-command`
    """
    inline = repo.ui.config(b'clone-bundles', b'auto-generate.serve-inline')
    basename = repo.vfs.basename(bundle.filepath)
    if inline:
        dest_dir = repo.vfs.join(bundlecaches.BUNDLE_CACHE_DIR)
        repo.vfs.makedirs(dest_dir)
        dest = repo.vfs.join(dest_dir, basename)
        util.copyfiles(bundle.filepath, dest, hardlink=True)
        url = bundlecaches.CLONEBUNDLESCHEME + basename
        return bundle.uploaded(url, basename)
    else:
        cmd = repo.ui.config(b'clone-bundles', b'upload-command')
        url = repo.ui.config(b'clone-bundles', b'url-template')
        filepath = procutil.shellquote(bundle.filepath)
        variables = {
            b'HGCB_BUNDLE_PATH': filepath,
            b'HGCB_BUNDLE_BASENAME': basename,
        }
        env = procutil.shellenviron(environ=variables)
        ret = repo.ui.system(cmd, environ=env)
        if ret:
            raise error.Abort(b"command returned status %d: %s" % (ret, cmd))
        url = (
            url.decode('utf8')
            .format(basename=basename.decode('utf8'))
            .encode('utf8')
        )
        return bundle.uploaded(url, basename)


def delete_bundle(repo, bundle):
    """delete a bundle from storage"""
    assert bundle.ready

    inline = bundle.file_url.startswith(bundlecaches.CLONEBUNDLESCHEME)

    if inline:
        msg = b'clone-bundles: deleting inline bundle %s\n'
    else:
        msg = b'clone-bundles: deleting bundle %s\n'
    msg %= bundle.basename
    if repo.ui.configbool(b'devel', b'debug.clonebundles'):
        repo.ui.write(msg)
    else:
        repo.ui.debug(msg)

    if inline:
        inline_path = repo.vfs.join(
            bundlecaches.BUNDLE_CACHE_DIR,
            bundle.basename,
        )
        util.tryunlink(inline_path)
    else:
        cmd = repo.ui.config(b'clone-bundles', b'delete-command')
        variables = {
            b'HGCB_BUNDLE_URL': bundle.file_url,
            b'HGCB_BASENAME': bundle.basename,
        }
        env = procutil.shellenviron(environ=variables)
        ret = repo.ui.system(cmd, environ=env)
        if ret:
            raise error.Abort(b"command returned status %d: %s" % (ret, cmd))


def auto_bundle_needed_actions(repo, bundles, op_id):
    """find the list of bundles that need action

    returns a list of RequestedBundle objects that need to be generated and
    uploaded."""
    create_bundles = []
    delete_bundles = []
    repo = repo.filtered(b"immutable")
    targets = repo.ui.configlist(b'clone-bundles', b'auto-generate.formats')
    ratio = float(
        repo.ui.config(b'clone-bundles', b'trigger.below-bundled-ratio')
    )
    abs_revs = repo.ui.configint(b'clone-bundles', b'trigger.revs')
    revs = len(repo.changelog)
    generic_data = {
        'revs': revs,
        'head_revs': repo.changelog.headrevs(),
        'tip_rev': repo.changelog.tiprev(),
        'tip_node': node.hex(repo.changelog.tip()),
        'op_id': op_id,
    }
    for t in targets:
        t = bundlecaches.parsebundlespec(repo, t, strict=False).as_spec()
        if new_bundle_needed(repo, bundles, ratio, abs_revs, t, revs):
            data = generic_data.copy()
            data['bundle_type'] = t
            b = RequestedBundle(**data)
            create_bundles.append(b)
    delete_bundles.extend(find_outdated_bundles(repo, bundles))
    return create_bundles, delete_bundles


def new_bundle_needed(repo, bundles, ratio, abs_revs, bundle_type, revs):
    """consider the current cached content and trigger new bundles if needed"""
    threshold = max((revs * ratio), (revs - abs_revs))
    for b in bundles:
        if not b.valid_for(repo) or b.bundle_type != bundle_type:
            continue
        if b.revs > threshold:
            return False
    return True


def start_one_bundle(repo, bundle):
    """start the generation of a single bundle file

    the `bundle` argument should be a RequestedBundle object.

    This data is passed to the `debugmakeclonebundles` "as is".
    """
    data = util.pickle.dumps(bundle)
    cmd = [procutil.hgexecutable(), b'--cwd', repo.path, INTERNAL_CMD]
    env = procutil.shellenviron()
    msg = b'clone-bundles: starting bundle generation: %s\n'
    stdout = None
    stderr = None
    waits = []
    record_wait = None
    if repo.ui.configbool(b'devel', b'debug.clonebundles'):
        stdout = procutil.stdout
        stderr = procutil.stderr
        repo.ui.write(msg % bundle.bundle_type)
        record_wait = waits.append
    else:
        repo.ui.debug(msg % bundle.bundle_type)
    bg = procutil.runbgcommand
    bg(
        cmd,
        env,
        stdin_bytes=data,
        stdout=stdout,
        stderr=stderr,
        record_wait=record_wait,
    )
    for f in waits:
        f()


INTERNAL_CMD = b'debug::internal-make-clone-bundles'


@command(INTERNAL_CMD, [], b'')
def debugmakeclonebundles(ui, repo):
    """Internal command to auto-generate debug bundles"""
    requested_bundle = util.pickle.load(procutil.stdin)
    procutil.stdin.close()

    collect_garbage(repo)

    fname = requested_bundle.suggested_filename
    fpath = repo.vfs.makedirs(b'tmp-bundles')
    fpath = repo.vfs.join(b'tmp-bundles', fname)
    bundle = requested_bundle.generating(fpath)
    update_bundle_list(repo, new_bundles=[bundle])

    requested_bundle.generate_bundle(repo, fpath)

    repo.invalidate()
    finalize_one_bundle(repo, bundle)


def make_auto_bundler(source_repo):
    reporef = weakref.ref(source_repo)

    def autobundle(tr):
        repo = reporef()
        assert repo is not None
        bundles = read_auto_gen(repo)
        new, __ = auto_bundle_needed_actions(repo, bundles, b"%d_txn" % id(tr))
        for data in new:
            start_one_bundle(repo, data)
        return None

    return autobundle


def reposetup(ui, repo):
    """install the two pieces needed for automatic clonebundle generation

    - add a "post-close" hook that fires bundling when needed
    - introduce a clone-bundle lock to let multiple processes meddle with the
      state files.
    """
    if not repo.local():
        return

    class autobundlesrepo(repo.__class__):
        def transaction(self, *args, **kwargs):
            tr = super(autobundlesrepo, self).transaction(*args, **kwargs)
            enabled = repo.ui.configbool(
                b'clone-bundles',
                b'auto-generate.on-change',
            )
            targets = repo.ui.configlist(
                b'clone-bundles', b'auto-generate.formats'
            )
            if enabled:
                if not targets:
                    repo.ui.warn(
                        _(
                            b'clone-bundle auto-generate enabled, '
                            b'but no formats specified: disabling generation\n'
                        )
                    )
                else:
                    tr.addpostclose(CAT_POSTCLOSE, make_auto_bundler(self))
            return tr

        @localrepo.unfilteredmethod
        def clonebundles_lock(self, wait=True):
            '''Lock the repository file related to clone bundles'''
            if not hasattr(self, '_cb_lock_ref'):
                self._cb_lock_ref = None
            l = self._currentlock(self._cb_lock_ref)
            if l is not None:
                l.lock()
                return l

            l = self._lock(
                vfs=self.vfs,
                lockname=b"clonebundleslock",
                wait=wait,
                releasefn=None,
                acquirefn=None,
                desc=_(b'repository %s') % self.origroot,
            )
            self._cb_lock_ref = weakref.ref(l)
            return l

    repo._wlockfreeprefix.add(AUTO_GEN_FILE)
    repo._wlockfreeprefix.add(bundlecaches.CB_MANIFEST_FILE)
    repo.__class__ = autobundlesrepo


@command(
    b'admin::clone-bundles-refresh',
    [
        (
            b'',
            b'background',
            False,
            _(b'start bundle generation in the background'),
        ),
    ],
    b'',
)
def cmd_admin_clone_bundles_refresh(
    ui,
    repo: localrepo.localrepository,
    background=False,
):
    """generate clone bundles according to the configuration

    This runs the logic for automatic generation, removing outdated bundles and
    generating new ones if necessary. See :hg:`help -e clone-bundles` for
    details about how to configure this feature.
    """
    debug = repo.ui.configbool(b'devel', b'debug.clonebundles')
    bundles = read_auto_gen(repo)
    op_id = b"%d_acbr" % os.getpid()
    create, delete = auto_bundle_needed_actions(repo, bundles, op_id)

    # if some bundles are scheduled for creation in the background, they will
    # deal with garbage collection too, so no need to synchroniously do it.
    #
    # However if no bundles are scheduled for creation, we need to explicitly do
    # it here.
    if not (background and create):
        # we clean up outdated bundles before generating new ones to keep the
        # last two versions of the bundle around for a while and avoid having to
        # deal with clients that just got served a manifest.
        for o in delete:
            delete_bundle(repo, o)
        update_bundle_list(repo, del_bundles=delete)

    if create:
        fpath = repo.vfs.makedirs(b'tmp-bundles')

    if background:
        for requested_bundle in create:
            start_one_bundle(repo, requested_bundle)
    else:
        for requested_bundle in create:
            if debug:
                msg = b'clone-bundles: starting bundle generation: %s\n'
                repo.ui.write(msg % requested_bundle.bundle_type)
            fname = requested_bundle.suggested_filename
            fpath = repo.vfs.join(b'tmp-bundles', fname)
            generating_bundle = requested_bundle.generating(fpath)
            update_bundle_list(repo, new_bundles=[generating_bundle])
            requested_bundle.generate_bundle(repo, fpath)
            result = upload_bundle(repo, generating_bundle)
            update_bundle_list(repo, new_bundles=[result])
            update_ondisk_manifest(repo)
            cleanup_tmp_bundle(repo, generating_bundle)


@command(b'admin::clone-bundles-clear', [], b'')
def cmd_admin_clone_bundles_clear(ui, repo: localrepo.localrepository):
    """remove existing clone bundle caches

    See `hg help admin::clone-bundles-refresh` for details on how to regenerate
    them.

    This command will only affect bundles currently available, it will not
    affect bundles being asynchronously generated.
    """
    bundles = read_auto_gen(repo)
    delete = [b for b in bundles if b.ready]
    for o in delete:
        delete_bundle(repo, o)
    update_bundle_list(repo, del_bundles=delete)
