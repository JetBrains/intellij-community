# churn.py - create a graph of revisions count grouped by template
#
# Copyright 2006 Josef "Jeff" Sipek <jeffpc@josefsipek.net>
# Copyright 2008 Alexander Solovyov <piranha@piranha.org.ua>
#
# This software may be used and distributed according to the terms of the
# GNU General Public License version 2 or any later version.

'''command to display statistics about repository history'''


import datetime
import os
import time

from mercurial.i18n import _
from mercurial.pycompat import open
from mercurial import (
    cmdutil,
    encoding,
    logcmdutil,
    patch,
    pycompat,
    registrar,
    scmutil,
)

cmdtable = {}
command = registrar.command(cmdtable)
# Note for extension authors: ONLY specify testedwith = 'ships-with-hg-core' for
# extensions which SHIP WITH MERCURIAL. Non-mainline extensions should
# be specifying the version(s) of Mercurial they are tested with, or
# leave the attribute unspecified.
testedwith = b'ships-with-hg-core'


def changedlines(ui, repo, ctx1, ctx2, fmatch):
    added, removed = 0, 0
    diff = b''.join(patch.diff(repo, ctx1.node(), ctx2.node(), fmatch))
    inhunk = False
    for l in diff.split(b'\n'):
        if inhunk and l.startswith(b"+"):
            added += 1
        elif inhunk and l.startswith(b"-"):
            removed += 1
        elif l.startswith(b"@"):
            inhunk = True
        elif l.startswith(b"d"):
            inhunk = False
    return (added, removed)


def countrate(ui, repo, amap, *pats, **opts):
    """Calculate stats"""
    if opts.get('dateformat'):

        def getkey(ctx):
            t, tz = ctx.date()
            date = datetime.datetime(*time.gmtime(float(t) - tz)[:6])
            return encoding.strtolocal(
                date.strftime(encoding.strfromlocal(opts['dateformat']))
            )

    else:
        tmpl = opts.get('oldtemplate') or opts.get('template')
        tmpl = logcmdutil.maketemplater(ui, repo, tmpl)

        def getkey(ctx):
            ui.pushbuffer()
            tmpl.show(ctx)
            return ui.popbuffer()

    progress = ui.makeprogress(
        _(b'analyzing'), unit=_(b'revisions'), total=len(repo)
    )
    rate = {}

    def prep(ctx, fmatch):
        rev = ctx.rev()
        key = getkey(ctx).strip()
        key = amap.get(key, key)  # alias remap
        if opts.get('changesets'):
            rate[key] = (rate.get(key, (0,))[0] + 1, 0)
        else:
            parents = ctx.parents()
            if len(parents) > 1:
                ui.note(_(b'revision %d is a merge, ignoring...\n') % (rev,))
                return

            ctx1 = parents[0]
            lines = changedlines(ui, repo, ctx1, ctx, fmatch)
            rate[key] = [r + l for r, l in zip(rate.get(key, (0, 0)), lines)]

        progress.increment()

    wopts = logcmdutil.walkopts(
        pats=pats,
        opts=pycompat.byteskwargs(opts),
        revspec=opts['rev'],
        date=opts['date'],
        include_pats=opts['include'],
        exclude_pats=opts['exclude'],
    )
    revs, makefilematcher = logcmdutil.makewalker(repo, wopts)
    for ctx in scmutil.walkchangerevs(repo, revs, makefilematcher, prep):
        continue

    progress.complete()

    return rate


@command(
    b'churn',
    [
        (
            b'r',
            b'rev',
            [],
            _(b'count rate for the specified revision or revset'),
            _(b'REV'),
        ),
        (
            b'd',
            b'date',
            b'',
            _(b'count rate for revisions matching date spec'),
            _(b'DATE'),
        ),
        (
            b't',
            b'oldtemplate',
            b'',
            _(b'template to group changesets (DEPRECATED)'),
            _(b'TEMPLATE'),
        ),
        (
            b'T',
            b'template',
            b'{author|email}',
            _(b'template to group changesets'),
            _(b'TEMPLATE'),
        ),
        (
            b'f',
            b'dateformat',
            b'',
            _(b'strftime-compatible format for grouping by date'),
            _(b'FORMAT'),
        ),
        (b'c', b'changesets', False, _(b'count rate by number of changesets')),
        (b's', b'sort', False, _(b'sort by key (default: sort by count)')),
        (b'', b'diffstat', False, _(b'display added/removed lines separately')),
        (b'', b'aliases', b'', _(b'file with email aliases'), _(b'FILE')),
    ]
    + cmdutil.walkopts,
    _(b"hg churn [-d DATE] [-r REV] [--aliases FILE] [FILE]"),
    helpcategory=command.CATEGORY_MAINTENANCE,
    inferrepo=True,
)
def churn(ui, repo, *pats, **opts):
    """histogram of changes to the repository

    This command will display a histogram representing the number
    of changed lines or revisions, grouped according to the given
    template. The default template will group changes by author.
    The --dateformat option may be used to group the results by
    date instead.

    Statistics are based on the number of changed lines, or
    alternatively the number of matching revisions if the
    --changesets option is specified.

    Examples::

      # display count of changed lines for every committer
      hg churn -T "{author|email}"

      # display daily activity graph
      hg churn -f "%H" -s -c

      # display activity of developers by month
      hg churn -f "%Y-%m" -s -c

      # display count of lines changed in every year
      hg churn -f "%Y" -s

      # display count of lines changed in a time range
      hg churn -d "2020-04 to 2020-09"

    It is possible to map alternate email addresses to a main address
    by providing a file using the following format::

      <alias email> = <actual email>

    Such a file may be specified with the --aliases option, otherwise
    a .hgchurn file will be looked for in the working directory root.
    Aliases will be split from the rightmost "=".
    """

    def pad(s, l):
        return s + b" " * (l - encoding.colwidth(s))

    amap = {}
    aliases = opts.get('aliases')
    if not aliases and os.path.exists(repo.wjoin(b'.hgchurn')):
        aliases = repo.wjoin(b'.hgchurn')
    if aliases:
        for l in open(aliases, b"rb"):
            try:
                alias, actual = l.rsplit(b'=' in l and b'=' or None, 1)
                amap[alias.strip()] = actual.strip()
            except ValueError:
                l = l.strip()
                if l:
                    ui.warn(_(b"skipping malformed alias: %s\n") % l)
                continue

    rate = list(countrate(ui, repo, amap, *pats, **opts).items())
    if not rate:
        return

    if opts.get('sort'):
        rate.sort()
    else:
        rate.sort(key=lambda x: (-sum(x[1]), x))

    # Be careful not to have a zero maxcount (issue833)
    maxcount = float(max(sum(v) for k, v in rate)) or 1.0
    maxname = max(len(k) for k, v in rate)

    ttywidth = ui.termwidth()
    ui.debug(b"assuming %i character terminal\n" % ttywidth)
    width = ttywidth - maxname - 2 - 2 - 2

    if opts.get('diffstat'):
        width -= 15

        def format(name, diffstat):
            added, removed = diffstat
            return b"%s %15s %s%s\n" % (
                pad(name, maxname),
                b'+%d/-%d' % (added, removed),
                ui.label(b'+' * charnum(added), b'diffstat.inserted'),
                ui.label(b'-' * charnum(removed), b'diffstat.deleted'),
            )

    else:
        width -= 6

        def format(name, count):
            return b"%s %6d %s\n" % (
                pad(name, maxname),
                sum(count),
                b'*' * charnum(sum(count)),
            )

    def charnum(count):
        return int(count * width // maxcount)

    for name, count in rate:
        ui.write(format(name, count))
