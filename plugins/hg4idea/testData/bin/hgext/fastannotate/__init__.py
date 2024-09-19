# Copyright 2016-present Facebook. All Rights Reserved.
#
# fastannotate: faster annotate implementation using linelog
#
# This software may be used and distributed according to the terms of the
# GNU General Public License version 2 or any later version.
"""yet another annotate implementation that might be faster (EXPERIMENTAL)

The fastannotate extension provides a 'fastannotate' command that makes
use of the linelog data structure as a cache layer and is expected to
be faster than the vanilla 'annotate' if the cache is present.

In most cases, fastannotate requires a setup that mainbranch is some pointer
that always moves forward, to be most efficient.

Using fastannotate together with linkrevcache would speed up building the
annotate cache greatly. Run "debugbuildlinkrevcache" before
"debugbuildannotatecache".

::

    [fastannotate]
    # specify the main branch head. the internal linelog will only contain
    # the linear (ignoring p2) "mainbranch". since linelog cannot move
    # backwards without a rebuild, this should be something that always moves
    # forward, usually it is "master" or "@".
    mainbranch = master

    # fastannotate supports different modes to expose its feature.
    # a list of combination:
    # - fastannotate: expose the feature via the "fastannotate" command which
    #   deals with everything in a most efficient way, and provides extra
    #   features like --deleted etc.
    # - fctx: replace fctx.annotate implementation. note:
    #     a. it is less efficient than the "fastannotate" command
    #     b. it will make it practically impossible to access the old (disk
    #        side-effect free) annotate implementation
    #     c. it implies "hgweb".
    # - hgweb: replace hgweb's annotate implementation. conflict with "fctx".
    # (default: fastannotate)
    modes = fastannotate

    # default format when no format flags are used (default: number)
    defaultformat = changeset, user, date

    # serve the annotate cache via wire protocol (default: False)
    # tip: the .hg/fastannotate directory is portable - can be rsynced
    server = True

    # build annotate cache on demand for every client request (default: True)
    # disabling it could make server response faster, useful when there is a
    # cronjob building the cache.
    serverbuildondemand = True

    # update local annotate cache from remote on demand
    client = False

    # path to use when connecting to the remote server (default: default)
    remotepath = default

    # minimal length of the history of a file required to fetch linelog from
    # the server. (default: 10)
    clientfetchthreshold = 10

    # for "fctx" mode, always follow renames regardless of command line option.
    # this is a BC with the original command but will reduced the space needed
    # for annotate cache, and is useful for client-server setup since the
    # server will only provide annotate cache with default options (i.e. with
    # follow). do not affect "fastannotate" mode. (default: True)
    forcefollow = True

    # for "fctx" mode, always treat file as text files, to skip the "isbinary"
    # check. this is consistent with the "fastannotate" command and could help
    # to avoid a file fetch if remotefilelog is used. (default: True)
    forcetext = True

    # use unfiltered repo for better performance.
    unfilteredrepo = True

    # sacrifice correctness in some corner cases for performance. it does not
    # affect the correctness of the annotate cache being built. the option
    # is experimental and may disappear in the future (default: False)
    perfhack = True
"""

# TODO from import:
# * `branch` is probably the wrong term, throughout the code.
#
# * replace the fastannotate `modes` configuration with a collection
#   of booleans.
#
# * Use the templater instead of bespoke formatting
#
# * rename the config knob for updating the local cache from a remote server
#
# * revise wireprotocol for sharing annotate files
#
# * figure out a sensible default for `mainbranch` (with the caveat
#   that we probably also want to figure out a better term than
#   `branch`, see above)
#
# * format changes to the revmap file (maybe use length-encoding
#   instead of null-terminated file paths at least?)
from __future__ import absolute_import

from mercurial.i18n import _
from mercurial import (
    error as hgerror,
    localrepo,
    registrar,
)

from . import (
    commands,
    protocol,
)

# Note for extension authors: ONLY specify testedwith = 'ships-with-hg-core' for
# extensions which SHIP WITH MERCURIAL. Non-mainline extensions should
# be specifying the version(s) of Mercurial they are tested with, or
# leave the attribute unspecified.
testedwith = b'ships-with-hg-core'

cmdtable = commands.cmdtable

configtable = {}
configitem = registrar.configitem(configtable)

configitem(b'fastannotate', b'modes', default=[b'fastannotate'])
configitem(b'fastannotate', b'server', default=False)
configitem(b'fastannotate', b'client', default=False)
configitem(b'fastannotate', b'unfilteredrepo', default=True)
configitem(b'fastannotate', b'defaultformat', default=[b'number'])
configitem(b'fastannotate', b'perfhack', default=False)
configitem(b'fastannotate', b'mainbranch')
configitem(b'fastannotate', b'forcetext', default=True)
configitem(b'fastannotate', b'forcefollow', default=True)
configitem(b'fastannotate', b'clientfetchthreshold', default=10)
configitem(b'fastannotate', b'serverbuildondemand', default=True)
configitem(b'fastannotate', b'remotepath', default=b'default')


def uisetup(ui):
    modes = set(ui.configlist(b'fastannotate', b'modes'))
    if b'fctx' in modes:
        modes.discard(b'hgweb')
    for name in modes:
        if name == b'fastannotate':
            commands.registercommand()
        elif name == b'hgweb':
            from . import support

            support.replacehgwebannotate()
        elif name == b'fctx':
            from . import support

            support.replacefctxannotate()
            commands.wrapdefault()
        else:
            raise hgerror.Abort(_(b'fastannotate: invalid mode: %s') % name)

    if ui.configbool(b'fastannotate', b'server'):
        protocol.serveruisetup(ui)


def extsetup(ui):
    # fastannotate has its own locking, without depending on repo lock
    # TODO: avoid mutating this unless the specific repo has it enabled
    localrepo.localrepository._wlockfreeprefix.add(b'fastannotate/')


def reposetup(ui, repo):
    if ui.configbool(b'fastannotate', b'client'):
        protocol.clientreposetup(ui, repo)
