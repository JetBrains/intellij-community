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

:hg:`debugcreatestreamclonebundle` can be used to produce a special
*streaming clonebundle*. These are bundle files that are extremely efficient
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
"""

from __future__ import absolute_import

from mercurial import (
    bundlecaches,
    extensions,
    wireprotov1server,
)

testedwith = b'ships-with-hg-core'


def capabilities(orig, repo, proto):
    caps = orig(repo, proto)

    # Only advertise if a manifest exists. This does add some I/O to requests.
    # But this should be cheaper than a wasted network round trip due to
    # missing file.
    if repo.vfs.exists(bundlecaches.CB_MANIFEST_FILE):
        caps.append(b'clonebundles')

    return caps


def extsetup(ui):
    extensions.wrapfunction(wireprotov1server, b'_capabilities', capabilities)
