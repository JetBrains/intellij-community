# diffutil.py - utility functions related to diff and patch
#
# Copyright 2006 Brendan Cully <brendan@kublai.com>
# Copyright 2007 Chris Mason <chris.mason@oracle.com>
# Copyright 2018 Octobus <octobus@octobus.net>
#
# This software may be used and distributed according to the terms of the
# GNU General Public License version 2 or any later version.

import typing

from typing import (
    Any,
    Dict,
    Optional,
)

from .i18n import _
from .node import nullrev

from . import (
    mdiff,
    pycompat,
)

if typing.TYPE_CHECKING:
    from . import ui as uimod

# TODO: narrow the value after the config module is typed
_Opts = Dict[bytes, Any]


def diffallopts(
    ui: "uimod.ui",
    opts: Optional[_Opts] = None,
    untrusted: bool = False,
    section: bytes = b'diff',
    configprefix: bytes = b'',
) -> mdiff.diffopts:
    '''return diffopts with all features supported and parsed'''
    return difffeatureopts(
        ui,
        opts=opts,
        untrusted=untrusted,
        section=section,
        git=True,
        whitespace=True,
        formatchanging=True,
        configprefix=configprefix,
    )


def difffeatureopts(
    ui: "uimod.ui",
    opts: Optional[_Opts] = None,
    untrusted: bool = False,
    section: bytes = b'diff',
    git: bool = False,
    whitespace: bool = False,
    formatchanging: bool = False,
    configprefix: bytes = b'',
) -> mdiff.diffopts:
    """return diffopts with only opted-in features parsed

    Features:
    - git: git-style diffs
    - whitespace: whitespace options like ignoreblanklines and ignorews
    - formatchanging: options that will likely break or cause correctness issues
      with most diff parsers
    """

    def get(
        key: bytes,
        name: Optional[bytes] = None,
        getter=ui.configbool,
        forceplain: Optional[bool] = None,
    ) -> Any:
        if opts:
            v = opts.get(key)
            # diffopts flags are either None-default (which is passed
            # through unchanged, so we can identify unset values), or
            # some other falsey default (eg --unified, which defaults
            # to an empty string). We only want to override the config
            # entries from hgrc with command line values if they
            # appear to have been set, which is any truthy value,
            # True, or False.
            if v or isinstance(v, bool):
                return v
        if forceplain is not None and ui.plain():
            return forceplain
        return getter(
            section, configprefix + (name or key), untrusted=untrusted
        )

    # core options, expected to be understood by every diff parser
    buildopts = {
        b'nodates': get(b'nodates'),
        b'showfunc': get(b'show_function', b'showfunc'),
        b'context': get(b'unified', getter=ui.config),
    }
    buildopts[b'xdiff'] = ui.configbool(b'experimental', b'xdiff')

    if git:
        buildopts[b'git'] = get(b'git')

        # since this is in the experimental section, we need to call
        # ui.configbool directory
        buildopts[b'showsimilarity'] = ui.configbool(
            b'experimental', b'extendedheader.similarity'
        )

        # need to inspect the ui object instead of using get() since we want to
        # test for an int
        hconf = ui.config(b'experimental', b'extendedheader.index')
        if hconf is not None:
            hlen = None
            try:
                # the hash config could be an integer (for length of hash) or a
                # word (e.g. short, full, none)
                hlen = int(hconf)
                if hlen < 0 or hlen > 40:
                    msg = _(b"invalid length for extendedheader.index: '%d'\n")
                    ui.warn(msg % hlen)
            except ValueError:
                # default value
                if hconf == b'short' or hconf == b'':
                    hlen = 12
                elif hconf == b'full':
                    hlen = 40
                elif hconf != b'none':
                    msg = _(b"invalid value for extendedheader.index: '%s'\n")
                    ui.warn(msg % hconf)
            finally:
                buildopts[b'index'] = hlen

    if whitespace:
        buildopts[b'ignorews'] = get(b'ignore_all_space', b'ignorews')
        buildopts[b'ignorewsamount'] = get(
            b'ignore_space_change', b'ignorewsamount'
        )
        buildopts[b'ignoreblanklines'] = get(
            b'ignore_blank_lines', b'ignoreblanklines'
        )
        buildopts[b'ignorewseol'] = get(b'ignore_space_at_eol', b'ignorewseol')
    if formatchanging:
        buildopts[b'text'] = None if opts is None else opts.get(b'text')
        binary = None if opts is None else opts.get(b'binary')
        buildopts[b'nobinary'] = (
            not binary
            if binary is not None
            else get(b'nobinary', forceplain=False)
        )
        buildopts[b'noprefix'] = get(b'noprefix', forceplain=False)
        buildopts[b'worddiff'] = get(
            b'word_diff', b'word-diff', forceplain=False
        )

    return mdiff.diffopts(**pycompat.strkwargs(buildopts))


def diff_parent(ctx):
    """get the context object to use as parent when diffing


    If diff.merge is enabled, an overlayworkingctx of the auto-merged parents will be returned.
    """
    repo = ctx.repo()
    if repo.ui.configbool(b"diff", b"merge") and ctx.p2().rev() != nullrev:
        # avoid circular import
        from . import (
            context,
            merge,
        )

        wctx = context.overlayworkingctx(repo)
        wctx.setbase(ctx.p1())
        with repo.ui.configoverride(
            {
                (
                    b"ui",
                    b"forcemerge",
                ): b"internal:merge3-lie-about-conflicts",
            },
            b"merge-diff",
        ):
            with repo.ui.silent():
                merge.merge(ctx.p2(), wc=wctx)
        return wctx
    else:
        return ctx.p1()
