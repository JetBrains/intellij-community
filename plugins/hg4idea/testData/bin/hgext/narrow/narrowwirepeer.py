# narrowwirepeer.py - passes narrow spec with unbundle command
#
# Copyright 2017 Google, Inc.
#
# This software may be used and distributed according to the terms of the
# GNU General Public License version 2 or any later version.

from __future__ import absolute_import

from mercurial import (
    bundle2,
    error,
    extensions,
    hg,
    narrowspec,
    wireprototypes,
    wireprotov1peer,
    wireprotov1server,
)

from . import narrowbundle2


def uisetup():
    wireprotov1peer.wirepeer.narrow_widen = peernarrowwiden


def reposetup(repo):
    def wirereposetup(ui, peer):
        def wrapped(orig, cmd, *args, **kwargs):
            if cmd == b'unbundle':
                # TODO: don't blindly add include/exclude wireproto
                # arguments to unbundle.
                include, exclude = repo.narrowpats
                kwargs["includepats"] = b','.join(include)
                kwargs["excludepats"] = b','.join(exclude)
            return orig(cmd, *args, **kwargs)

        extensions.wrapfunction(peer, b'_calltwowaystream', wrapped)

    hg.wirepeersetupfuncs.append(wirereposetup)


@wireprotov1server.wireprotocommand(
    b'narrow_widen',
    b'oldincludes oldexcludes'
    b' newincludes newexcludes'
    b' commonheads cgversion'
    b' known ellipses',
    permission=b'pull',
)
def narrow_widen(
    repo,
    proto,
    oldincludes,
    oldexcludes,
    newincludes,
    newexcludes,
    commonheads,
    cgversion,
    known,
    ellipses,
):
    """wireprotocol command to send data when a narrow clone is widen. We will
    be sending a changegroup here.

    The current set of arguments which are required:
    oldincludes: the old includes of the narrow copy
    oldexcludes: the old excludes of the narrow copy
    newincludes: the new includes of the narrow copy
    newexcludes: the new excludes of the narrow copy
    commonheads: list of heads which are common between the server and client
    cgversion(maybe): the changegroup version to produce
    known: list of nodes which are known on the client (used in ellipses cases)
    ellipses: whether to send ellipses data or not
    """

    preferuncompressed = False
    try:

        def splitpaths(data):
            # work around ''.split(',') => ['']
            return data.split(b',') if data else []

        oldincludes = splitpaths(oldincludes)
        newincludes = splitpaths(newincludes)
        oldexcludes = splitpaths(oldexcludes)
        newexcludes = splitpaths(newexcludes)
        # validate the patterns
        narrowspec.validatepatterns(set(oldincludes))
        narrowspec.validatepatterns(set(newincludes))
        narrowspec.validatepatterns(set(oldexcludes))
        narrowspec.validatepatterns(set(newexcludes))

        common = wireprototypes.decodelist(commonheads)
        known = wireprototypes.decodelist(known)
        if ellipses == b'0':
            ellipses = False
        else:
            ellipses = bool(ellipses)
        cgversion = cgversion

        bundler = bundle2.bundle20(repo.ui)
        newmatch = narrowspec.match(
            repo.root, include=newincludes, exclude=newexcludes
        )
        oldmatch = narrowspec.match(
            repo.root, include=oldincludes, exclude=oldexcludes
        )
        if not ellipses:
            bundle2.widen_bundle(
                bundler,
                repo,
                oldmatch,
                newmatch,
                common,
                known,
                cgversion,
                ellipses,
            )
        else:
            narrowbundle2.generate_ellipses_bundle2_for_widening(
                bundler,
                repo,
                oldmatch,
                newmatch,
                cgversion,
                common,
                known,
            )
    except error.Abort as exc:
        bundler = bundle2.bundle20(repo.ui)
        manargs = [(b'message', exc.message)]
        advargs = []
        if exc.hint is not None:
            advargs.append((b'hint', exc.hint))
        bundler.addpart(bundle2.bundlepart(b'error:abort', manargs, advargs))
        preferuncompressed = True

    chunks = bundler.getchunks()
    return wireprototypes.streamres(
        gen=chunks, prefer_uncompressed=preferuncompressed
    )


def peernarrowwiden(remote, **kwargs):
    for ch in ('commonheads', 'known'):
        kwargs[ch] = wireprototypes.encodelist(kwargs[ch])

    for ch in ('oldincludes', 'newincludes', 'oldexcludes', 'newexcludes'):
        kwargs[ch] = b','.join(kwargs[ch])

    kwargs['ellipses'] = b'%i' % bool(kwargs['ellipses'])
    f = remote._callcompressable(b'narrow_widen', **kwargs)
    return bundle2.getunbundler(remote.ui, f)
