# keyword.py - $Keyword$ expansion for Mercurial
#
# Copyright 2007-2009 Christian Ebert <blacktrash@gmx.net>
#
# This software may be used and distributed according to the terms of the
# GNU General Public License version 2 or any later version.
#
# $Id$
#
# Keyword expansion hack against the grain of a DSCM
#
# There are many good reasons why this is not needed in a distributed
# SCM, still it may be useful in very small projects based on single
# files (like LaTeX packages), that are mostly addressed to an
# audience not running a version control system.
#
# For in-depth discussion refer to
# <http://mercurial.selenic.com/wiki/KeywordPlan>.
#
# Keyword expansion is based on Mercurial's changeset template mappings.
#
# Binary files are not touched.
#
# Files to act upon/ignore are specified in the [keyword] section.
# Customized keyword template mappings in the [keywordmaps] section.
#
# Run "hg help keyword" and "hg kwdemo" to get info on configuration.

'''expand keywords in tracked files

This extension expands RCS/CVS-like or self-customized $Keywords$ in
tracked text files selected by your configuration.

Keywords are only expanded in local repositories and not stored in the
change history. The mechanism can be regarded as a convenience for the
current user or for archive distribution.

Configuration is done in the [keyword] and [keywordmaps] sections of
hgrc files.

Example::

    [keyword]
    # expand keywords in every python file except those matching "x*"
    **.py =
    x*    = ignore

NOTE: the more specific you are in your filename patterns the less you
lose speed in huge repositories.

For [keywordmaps] template mapping and expansion demonstration and
control run "hg kwdemo". See "hg help templates" for a list of
available templates and filters.

An additional date template filter {date|utcdate} is provided. It
returns a date like "2006/09/18 15:13:13".

The default template mappings (view with "hg kwdemo -d") can be
replaced with customized keywords and templates. Again, run "hg
kwdemo" to control the results of your config changes.

Before changing/disabling active keywords, run "hg kwshrink" to avoid
the risk of inadvertently storing expanded keywords in the change
history.

To force expansion after enabling it, or a configuration change, run
"hg kwexpand".

Also, when committing with the record extension or using mq's qrecord,
be aware that keywords cannot be updated. Again, run "hg kwexpand" on
the files in question to update keyword expansions after all changes
have been checked in.

Expansions spanning more than one line and incremental expansions,
like CVS' $Log$, are not supported. A keyword template map "Log =
{desc}" expands to the first line of the changeset description.
'''

from mercurial import commands, cmdutil, dispatch, filelog, revlog, extensions
from mercurial import patch, localrepo, templater, templatefilters, util, match
from mercurial.hgweb import webcommands
from mercurial.lock import release
from mercurial.node import nullid
from mercurial.i18n import _
import re, shutil, tempfile

commands.optionalrepo += ' kwdemo'

# hg commands that do not act on keywords
nokwcommands = ('add addremove annotate bundle copy export grep incoming init'
                ' log outgoing push rename rollback tip verify'
                ' convert email glog')

# hg commands that trigger expansion only when writing to working dir,
# not when reading filelog, and unexpand when reading from working dir
restricted = ('merge record resolve qfold qimport qnew qpush qrefresh qrecord'
              ' transplant')

# provide cvs-like UTC date filter
utcdate = lambda x: util.datestr((x[0], 0), '%Y/%m/%d %H:%M:%S')

# make keyword tools accessible
kwtools = {'templater': None, 'hgcmd': '', 'inc': [], 'exc': ['.hg*']}


class kwtemplater(object):
    '''
    Sets up keyword templates, corresponding keyword regex, and
    provides keyword substitution functions.
    '''
    templates = {
        'Revision': '{node|short}',
        'Author': '{author|user}',
        'Date': '{date|utcdate}',
        'RCSfile': '{file|basename},v',
        'RCSFile': '{file|basename},v', # kept for backwards compatibility
                                        # with hg-keyword
        'Source': '{root}/{file},v',
        'Id': '{file|basename},v {node|short} {date|utcdate} {author|user}',
        'Header': '{root}/{file},v {node|short} {date|utcdate} {author|user}',
    }

    def __init__(self, ui, repo):
        self.ui = ui
        self.repo = repo
        self.match = match.match(repo.root, '', [],
                                 kwtools['inc'], kwtools['exc'])
        self.restrict = kwtools['hgcmd'] in restricted.split()

        kwmaps = self.ui.configitems('keywordmaps')
        if kwmaps: # override default templates
            self.templates = dict((k, templater.parsestring(v, False))
                                  for k, v in kwmaps)
        escaped = map(re.escape, self.templates.keys())
        kwpat = r'\$(%s)(: [^$\n\r]*? )??\$' % '|'.join(escaped)
        self.re_kw = re.compile(kwpat)

        templatefilters.filters['utcdate'] = utcdate
        self.ct = cmdutil.changeset_templater(self.ui, self.repo,
                                              False, None, '', False)

    def substitute(self, data, path, ctx, subfunc):
        '''Replaces keywords in data with expanded template.'''
        def kwsub(mobj):
            kw = mobj.group(1)
            self.ct.use_template(self.templates[kw])
            self.ui.pushbuffer()
            self.ct.show(ctx, root=self.repo.root, file=path)
            ekw = templatefilters.firstline(self.ui.popbuffer())
            return '$%s: %s $' % (kw, ekw)
        return subfunc(kwsub, data)

    def expand(self, path, node, data):
        '''Returns data with keywords expanded.'''
        if not self.restrict and self.match(path) and not util.binary(data):
            ctx = self.repo.filectx(path, fileid=node).changectx()
            return self.substitute(data, path, ctx, self.re_kw.sub)
        return data

    def iskwfile(self, path, flagfunc):
        '''Returns true if path matches [keyword] pattern
        and is not a symbolic link.
        Caveat: localrepository._link fails on Windows.'''
        return self.match(path) and not 'l' in flagfunc(path)

    def overwrite(self, node, expand, files):
        '''Overwrites selected files expanding/shrinking keywords.'''
        ctx = self.repo[node]
        mf = ctx.manifest()
        if node is not None:     # commit
            files = [f for f in ctx.files() if f in mf]
            notify = self.ui.debug
        else:                    # kwexpand/kwshrink
            notify = self.ui.note
        candidates = [f for f in files if self.iskwfile(f, ctx.flags)]
        if candidates:
            self.restrict = True # do not expand when reading
            msg = (expand and _('overwriting %s expanding keywords\n')
                   or _('overwriting %s shrinking keywords\n'))
            for f in candidates:
                fp = self.repo.file(f)
                data = fp.read(mf[f])
                if util.binary(data):
                    continue
                if expand:
                    if node is None:
                        ctx = self.repo.filectx(f, fileid=mf[f]).changectx()
                    data, found = self.substitute(data, f, ctx,
                                                  self.re_kw.subn)
                else:
                    found = self.re_kw.search(data)
                if found:
                    notify(msg % f)
                    self.repo.wwrite(f, data, mf.flags(f))
                    if node is None:
                        self.repo.dirstate.normal(f)
            self.restrict = False

    def shrinktext(self, text):
        '''Unconditionally removes all keyword substitutions from text.'''
        return self.re_kw.sub(r'$\1$', text)

    def shrink(self, fname, text):
        '''Returns text with all keyword substitutions removed.'''
        if self.match(fname) and not util.binary(text):
            return self.shrinktext(text)
        return text

    def shrinklines(self, fname, lines):
        '''Returns lines with keyword substitutions removed.'''
        if self.match(fname):
            text = ''.join(lines)
            if not util.binary(text):
                return self.shrinktext(text).splitlines(True)
        return lines

    def wread(self, fname, data):
        '''If in restricted mode returns data read from wdir with
        keyword substitutions removed.'''
        return self.restrict and self.shrink(fname, data) or data

class kwfilelog(filelog.filelog):
    '''
    Subclass of filelog to hook into its read, add, cmp methods.
    Keywords are "stored" unexpanded, and processed on reading.
    '''
    def __init__(self, opener, kwt, path):
        super(kwfilelog, self).__init__(opener, path)
        self.kwt = kwt
        self.path = path

    def read(self, node):
        '''Expands keywords when reading filelog.'''
        data = super(kwfilelog, self).read(node)
        return self.kwt.expand(self.path, node, data)

    def add(self, text, meta, tr, link, p1=None, p2=None):
        '''Removes keyword substitutions when adding to filelog.'''
        text = self.kwt.shrink(self.path, text)
        return super(kwfilelog, self).add(text, meta, tr, link, p1, p2)

    def cmp(self, node, text):
        '''Removes keyword substitutions for comparison.'''
        text = self.kwt.shrink(self.path, text)
        if self.renamed(node):
            t2 = super(kwfilelog, self).read(node)
            return t2 != text
        return revlog.revlog.cmp(self, node, text)

def _status(ui, repo, kwt, *pats, **opts):
    '''Bails out if [keyword] configuration is not active.
    Returns status of working directory.'''
    if kwt:
        unknown = (opts.get('unknown') or opts.get('all')
                   or opts.get('untracked'))
        return repo.status(match=cmdutil.match(repo, pats, opts), clean=True,
                           unknown=unknown)
    if ui.configitems('keyword'):
        raise util.Abort(_('[keyword] patterns cannot match'))
    raise util.Abort(_('no [keyword] patterns configured'))

def _kwfwrite(ui, repo, expand, *pats, **opts):
    '''Selects files and passes them to kwtemplater.overwrite.'''
    if repo.dirstate.parents()[1] != nullid:
        raise util.Abort(_('outstanding uncommitted merge'))
    kwt = kwtools['templater']
    status = _status(ui, repo, kwt, *pats, **opts)
    modified, added, removed, deleted = status[:4]
    if modified or added or removed or deleted:
        raise util.Abort(_('outstanding uncommitted changes'))
    wlock = lock = None
    try:
        wlock = repo.wlock()
        lock = repo.lock()
        kwt.overwrite(None, expand, status[6])
    finally:
        release(lock, wlock)

def demo(ui, repo, *args, **opts):
    '''print [keywordmaps] configuration and an expansion example

    Show current, custom, or default keyword template maps and their
    expansions.

    Extend the current configuration by specifying maps as arguments
    and using -f/--rcfile to source an external hgrc file.

    Use -d/--default to disable current configuration.

    See "hg help templates" for information on templates and filters.
    '''
    def demoitems(section, items):
        ui.write('[%s]\n' % section)
        for k, v in sorted(items):
            ui.write('%s = %s\n' % (k, v))

    fn = 'demo.txt'
    branchname = 'demobranch'
    tmpdir = tempfile.mkdtemp('', 'kwdemo.')
    ui.note(_('creating temporary repository at %s\n') % tmpdir)
    repo = localrepo.localrepository(ui, tmpdir, True)
    ui.setconfig('keyword', fn, '')

    uikwmaps = ui.configitems('keywordmaps')
    if args or opts.get('rcfile'):
        ui.status(_('\n\tconfiguration using custom keyword template maps\n'))
        if uikwmaps:
            ui.status(_('\textending current template maps\n'))
        if opts.get('default') or not uikwmaps:
            ui.status(_('\toverriding default template maps\n'))
        if opts.get('rcfile'):
            ui.readconfig(opts.get('rcfile'))
        if args:
            # simulate hgrc parsing
            rcmaps = ['[keywordmaps]\n'] + [a + '\n' for a in args]
            fp = repo.opener('hgrc', 'w')
            fp.writelines(rcmaps)
            fp.close()
            ui.readconfig(repo.join('hgrc'))
        kwmaps = dict(ui.configitems('keywordmaps'))
    elif opts.get('default'):
        ui.status(_('\n\tconfiguration using default keyword template maps\n'))
        kwmaps = kwtemplater.templates
        if uikwmaps:
            ui.status(_('\tdisabling current template maps\n'))
            for k, v in kwmaps.iteritems():
                ui.setconfig('keywordmaps', k, v)
    else:
        ui.status(_('\n\tconfiguration using current keyword template maps\n'))
        kwmaps = dict(uikwmaps) or kwtemplater.templates

    uisetup(ui)
    reposetup(ui, repo)
    for k, v in ui.configitems('extensions'):
        if k.endswith('keyword'):
            extension = '%s = %s' % (k, v)
            break
    ui.write('[extensions]\n%s\n' % extension)
    demoitems('keyword', ui.configitems('keyword'))
    demoitems('keywordmaps', kwmaps.iteritems())
    keywords = '$' + '$\n$'.join(sorted(kwmaps.keys())) + '$\n'
    repo.wopener(fn, 'w').write(keywords)
    repo.add([fn])
    path = repo.wjoin(fn)
    ui.note(_('\nkeywords written to %s:\n') % path)
    ui.note(keywords)
    ui.note('\nhg -R "%s" branch "%s"\n' % (tmpdir, branchname))
    # silence branch command if not verbose
    quiet = ui.quiet
    ui.quiet = not ui.verbose
    commands.branch(ui, repo, branchname)
    ui.quiet = quiet
    for name, cmd in ui.configitems('hooks'):
        if name.split('.', 1)[0].find('commit') > -1:
            repo.ui.setconfig('hooks', name, '')
    ui.note(_('unhooked all commit hooks\n'))
    msg = _('hg keyword configuration and expansion example')
    ui.note("hg -R '%s' ci -m '%s'\n" % (tmpdir, msg))
    repo.commit(text=msg)
    ui.status(_('\n\tkeywords expanded\n'))
    ui.write(repo.wread(fn))
    ui.debug('\nremoving temporary repository %s\n' % tmpdir)
    shutil.rmtree(tmpdir, ignore_errors=True)

def expand(ui, repo, *pats, **opts):
    '''expand keywords in the working directory

    Run after (re)enabling keyword expansion.

    kwexpand refuses to run if given files contain local changes.
    '''
    # 3rd argument sets expansion to True
    _kwfwrite(ui, repo, True, *pats, **opts)

def files(ui, repo, *pats, **opts):
    '''show files configured for keyword expansion

    List which files in the working directory are matched by the
    [keyword] configuration patterns.

    Useful to prevent inadvertent keyword expansion and to speed up
    execution by including only files that are actual candidates for
    expansion.

    See "hg help keyword" on how to construct patterns both for
    inclusion and exclusion of files.

    With -A/--all and -v/--verbose the codes used to show the status
    of files are::

      K = keyword expansion candidate
      k = keyword expansion candidate (not tracked)
      I = ignored
      i = ignored (not tracked)
    '''
    kwt = kwtools['templater']
    status = _status(ui, repo, kwt, *pats, **opts)
    cwd = pats and repo.getcwd() or ''
    modified, added, removed, deleted, unknown, ignored, clean = status
    files = []
    if not (opts.get('unknown') or opts.get('untracked')) or opts.get('all'):
        files = sorted(modified + added + clean)
    wctx = repo[None]
    kwfiles = [f for f in files if kwt.iskwfile(f, wctx.flags)]
    kwunknown = [f for f in unknown if kwt.iskwfile(f, wctx.flags)]
    if not opts.get('ignore') or opts.get('all'):
        showfiles = kwfiles, kwunknown
    else:
        showfiles = [], []
    if opts.get('all') or opts.get('ignore'):
        showfiles += ([f for f in files if f not in kwfiles],
                      [f for f in unknown if f not in kwunknown])
    for char, filenames in zip('KkIi', showfiles):
        fmt = (opts.get('all') or ui.verbose) and '%s %%s\n' % char or '%s\n'
        for f in filenames:
            ui.write(fmt % repo.pathto(f, cwd))

def shrink(ui, repo, *pats, **opts):
    '''revert expanded keywords in the working directory

    Run before changing/disabling active keywords or if you experience
    problems with "hg import" or "hg merge".

    kwshrink refuses to run if given files contain local changes.
    '''
    # 3rd argument sets expansion to False
    _kwfwrite(ui, repo, False, *pats, **opts)


def uisetup(ui):
    '''Collects [keyword] config in kwtools.
    Monkeypatches dispatch._parse if needed.'''

    for pat, opt in ui.configitems('keyword'):
        if opt != 'ignore':
            kwtools['inc'].append(pat)
        else:
            kwtools['exc'].append(pat)

    if kwtools['inc']:
        def kwdispatch_parse(orig, ui, args):
            '''Monkeypatch dispatch._parse to obtain running hg command.'''
            cmd, func, args, options, cmdoptions = orig(ui, args)
            kwtools['hgcmd'] = cmd
            return cmd, func, args, options, cmdoptions

        extensions.wrapfunction(dispatch, '_parse', kwdispatch_parse)

def reposetup(ui, repo):
    '''Sets up repo as kwrepo for keyword substitution.
    Overrides file method to return kwfilelog instead of filelog
    if file matches user configuration.
    Wraps commit to overwrite configured files with updated
    keyword substitutions.
    Monkeypatches patch and webcommands.'''

    try:
        if (not repo.local() or not kwtools['inc']
            or kwtools['hgcmd'] in nokwcommands.split()
            or '.hg' in util.splitpath(repo.root)
            or repo._url.startswith('bundle:')):
            return
    except AttributeError:
        pass

    kwtools['templater'] = kwt = kwtemplater(ui, repo)

    class kwrepo(repo.__class__):
        def file(self, f):
            if f[0] == '/':
                f = f[1:]
            return kwfilelog(self.sopener, kwt, f)

        def wread(self, filename):
            data = super(kwrepo, self).wread(filename)
            return kwt.wread(filename, data)

        def commit(self, *args, **opts):
            # use custom commitctx for user commands
            # other extensions can still wrap repo.commitctx directly
            self.commitctx = self.kwcommitctx
            try:
                return super(kwrepo, self).commit(*args, **opts)
            finally:
                del self.commitctx

        def kwcommitctx(self, ctx, error=False):
            wlock = lock = None
            try:
                wlock = self.wlock()
                lock = self.lock()
                n = super(kwrepo, self).commitctx(ctx, error)
                kwt.overwrite(n, True, None)
                return n
            finally:
                release(lock, wlock)

    # monkeypatches
    def kwpatchfile_init(orig, self, ui, fname, opener,
                         missing=False, eol=None):
        '''Monkeypatch/wrap patch.patchfile.__init__ to avoid
        rejects or conflicts due to expanded keywords in working dir.'''
        orig(self, ui, fname, opener, missing, eol)
        # shrink keywords read from working dir
        self.lines = kwt.shrinklines(self.fname, self.lines)

    def kw_diff(orig, repo, node1=None, node2=None, match=None, changes=None,
                opts=None):
        '''Monkeypatch patch.diff to avoid expansion except when
        comparing against working dir.'''
        if node2 is not None:
            kwt.match = util.never
        elif node1 is not None and node1 != repo['.'].node():
            kwt.restrict = True
        return orig(repo, node1, node2, match, changes, opts)

    def kwweb_skip(orig, web, req, tmpl):
        '''Wraps webcommands.x turning off keyword expansion.'''
        kwt.match = util.never
        return orig(web, req, tmpl)

    repo.__class__ = kwrepo

    extensions.wrapfunction(patch.patchfile, '__init__', kwpatchfile_init)
    if not kwt.restrict:
        extensions.wrapfunction(patch, 'diff', kw_diff)
    for c in 'annotate changeset rev filediff diff'.split():
        extensions.wrapfunction(webcommands, c, kwweb_skip)

cmdtable = {
    'kwdemo':
        (demo,
         [('d', 'default', None, _('show default keyword template maps')),
          ('f', 'rcfile', '', _('read maps from rcfile'))],
         _('hg kwdemo [-d] [-f RCFILE] [TEMPLATEMAP]...')),
    'kwexpand': (expand, commands.walkopts,
                 _('hg kwexpand [OPTION]... [FILE]...')),
    'kwfiles':
        (files,
         [('A', 'all', None, _('show keyword status flags of all files')),
          ('i', 'ignore', None, _('show files excluded from expansion')),
          ('u', 'unknown', None, _('only show unknown (not tracked) files')),
          ('a', 'all', None,
           _('show keyword status flags of all files (DEPRECATED)')),
          ('u', 'untracked', None, _('only show untracked files (DEPRECATED)')),
         ] + commands.walkopts,
         _('hg kwfiles [OPTION]... [FILE]...')),
    'kwshrink': (shrink, commands.walkopts,
                 _('hg kwshrink [OPTION]... [FILE]...')),
}
