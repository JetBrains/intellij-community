# churn.py - create a graph of revisions count grouped by template
#
# Copyright 2006 Josef "Jeff" Sipek <jeffpc@josefsipek.net>
# Copyright 2008 Alexander Solovyov <piranha@piranha.org.ua>
#
# This software may be used and distributed according to the terms of the
# GNU General Public License version 2 or any later version.

'''command to display statistics about repository history'''

from mercurial.i18n import _
from mercurial import patch, cmdutil, util, templater
import sys, os
import time, datetime

def maketemplater(ui, repo, tmpl):
    tmpl = templater.parsestring(tmpl, quoted=False)
    try:
        t = cmdutil.changeset_templater(ui, repo, False, None, None, False)
    except SyntaxError, inst:
        raise util.Abort(inst.args[0])
    t.use_template(tmpl)
    return t

def changedlines(ui, repo, ctx1, ctx2, fns):
    added, removed = 0, 0
    fmatch = cmdutil.matchfiles(repo, fns)
    diff = ''.join(patch.diff(repo, ctx1.node(), ctx2.node(), fmatch))
    for l in diff.split('\n'):
        if l.startswith("+") and not l.startswith("+++ "):
            added += 1
        elif l.startswith("-") and not l.startswith("--- "):
            removed += 1
    return (added, removed)

def countrate(ui, repo, amap, *pats, **opts):
    """Calculate stats"""
    if opts.get('dateformat'):
        def getkey(ctx):
            t, tz = ctx.date()
            date = datetime.datetime(*time.gmtime(float(t) - tz)[:6])
            return date.strftime(opts['dateformat'])
    else:
        tmpl = opts.get('template', '{author|email}')
        tmpl = maketemplater(ui, repo, tmpl)
        def getkey(ctx):
            ui.pushbuffer()
            tmpl.show(ctx)
            return ui.popbuffer()

    state = {'count': 0, 'pct': 0}
    rate = {}
    df = False
    if opts.get('date'):
        df = util.matchdate(opts['date'])

    m = cmdutil.match(repo, pats, opts)
    def prep(ctx, fns):
        rev = ctx.rev()
        if df and not df(ctx.date()[0]): # doesn't match date format
            return

        key = getkey(ctx)
        key = amap.get(key, key) # alias remap
        if opts.get('changesets'):
            rate[key] = (rate.get(key, (0,))[0] + 1, 0)
        else:
            parents = ctx.parents()
            if len(parents) > 1:
                ui.note(_('Revision %d is a merge, ignoring...\n') % (rev,))
                return

            ctx1 = parents[0]
            lines = changedlines(ui, repo, ctx1, ctx, fns)
            rate[key] = [r + l for r, l in zip(rate.get(key, (0, 0)), lines)]

        if opts.get('progress'):
            state['count'] += 1
            newpct = int(100.0 * state['count'] / max(len(repo), 1))
            if state['pct'] < newpct:
                state['pct'] = newpct
                ui.write("\r" + _("generating stats: %d%%") % state['pct'])
                sys.stdout.flush()

    for ctx in cmdutil.walkchangerevs(repo, m, opts, prep):
        continue

    if opts.get('progress'):
        ui.write("\r")
        sys.stdout.flush()

    return rate


def churn(ui, repo, *pats, **opts):
    '''histogram of changes to the repository

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
      hg churn -t '{author|email}'

      # display daily activity graph
      hg churn -f '%H' -s -c

      # display activity of developers by month
      hg churn -f '%Y-%m' -s -c

      # display count of lines changed in every year
      hg churn -f '%Y' -s

    It is possible to map alternate email addresses to a main address
    by providing a file using the following format::

      <alias email> <actual email>

    Such a file may be specified with the --aliases option, otherwise
    a .hgchurn file will be looked for in the working directory root.
    '''
    def pad(s, l):
        return (s + " " * l)[:l]

    amap = {}
    aliases = opts.get('aliases')
    if not aliases and os.path.exists(repo.wjoin('.hgchurn')):
        aliases = repo.wjoin('.hgchurn')
    if aliases:
        for l in open(aliases, "r"):
            l = l.strip()
            alias, actual = l.split()
            amap[alias] = actual

    rate = countrate(ui, repo, amap, *pats, **opts).items()
    if not rate:
        return

    sortkey = ((not opts.get('sort')) and (lambda x: -sum(x[1])) or None)
    rate.sort(key=sortkey)

    # Be careful not to have a zero maxcount (issue833)
    maxcount = float(max(sum(v) for k, v in rate)) or 1.0
    maxname = max(len(k) for k, v in rate)

    ttywidth = util.termwidth()
    ui.debug("assuming %i character terminal\n" % ttywidth)
    width = ttywidth - maxname - 2 - 2 - 2

    if opts.get('diffstat'):
        width -= 15
        def format(name, (added, removed)):
            return "%s %15s %s%s\n" % (pad(name, maxname),
                                       '+%d/-%d' % (added, removed),
                                       '+' * charnum(added),
                                       '-' * charnum(removed))
    else:
        width -= 6
        def format(name, count):
            return "%s %6d %s\n" % (pad(name, maxname), sum(count),
                                    '*' * charnum(sum(count)))

    def charnum(count):
        return int(round(count * width / maxcount))

    for name, count in rate:
        ui.write(format(name, count))


cmdtable = {
    "churn":
        (churn,
         [('r', 'rev', [], _('count rate for the specified revision or range')),
          ('d', 'date', '', _('count rate for revisions matching date spec')),
          ('t', 'template', '{author|email}',
           _('template to group changesets')),
          ('f', 'dateformat', '',
              _('strftime-compatible format for grouping by date')),
          ('c', 'changesets', False, _('count rate by number of changesets')),
          ('s', 'sort', False, _('sort by key (default: sort by count)')),
          ('', 'diffstat', False, _('display added/removed lines separately')),
          ('', 'aliases', '', _('file with email aliases')),
          ('', 'progress', None, _('show progress'))],
         _("hg churn [-d DATE] [-r REV] [--aliases FILE] [--progress] [FILE]")),
}
