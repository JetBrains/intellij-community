# diffutil.py - utility functions related to diff and patch
#
# Copyright 2006 Brendan Cully <brendan@kublai.com>
# Copyright 2007 Chris Mason <chris.mason@oracle.com>
# Copyright 2018 Octobus <octobus@octobus.net>
#
# This software may be used and distributed according to the terms of the
# GNU General Public License version 2 or any later version.

from __future__ import absolute_import

from .i18n import _

from . import (
    mdiff,
    pycompat,
)


def diffallopts(
    ui, opts=None, untrusted=False, section=b'diff', configprefix=b''
):
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
    ui,
    opts=None,
    untrusted=False,
    section=b'diff',
    git=False,
    whitespace=False,
    formatchanging=False,
    configprefix=b'',
):
    """return diffopts with only opted-in features parsed

    Features:
    - git: git-style diffs
    - whitespace: whitespace options like ignoreblanklines and ignorews
    - formatchanging: options that will likely break or cause correctness issues
      with most diff parsers
    """

    def get(key, name=None, getter=ui.configbool, forceplain=None):
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
        buildopts[b'text'] = opts and opts.get(b'text')
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
