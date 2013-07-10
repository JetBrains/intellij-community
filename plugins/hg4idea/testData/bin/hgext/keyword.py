# keyword.py - $Keyword$ expansion for Mercurial
#
# Copyright 2007-2012 Christian Ebert <blacktrash@gmx.net>
#
# This software may be used and distributed according to the terms of the
# GNU General Public License version 2 or any later version.
#
# $Id$
#
# Keyword expansion hack against the grain of a Distributed SCM
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

Keywords expand to the changeset data pertaining to the latest change
relative to the working directory parent of each file.

Configuration is done in the [keyword], [keywordset] and [keywordmaps]
sections of hgrc files.

Example::

    [keyword]
    # expand keywords in every python file except those matching "x*"
    **.py =
    x*    = ignore

    [keywordset]
    # prefer svn- over cvs-like default keywordmaps
    svn = True

.. note::
   The more specific you are in your filename patterns the less you
   lose speed in huge repositories.

For [keywordmaps] template mapping and expansion demonstration and
control run :hg:`kwdemo`. See :hg:`help templates` for a list of
available templates and filters.

Three additional date template filters are provided:

:``utcdate``:    "2006/09/18 15:13:13"
:``svnutcdate``: "2006-09-18 15:13:13Z"
:``svnisodate``: "2006-09-18 08:13:13 -700 (Mon, 18 Sep 2006)"

The default template mappings (view with :hg:`kwdemo -d`) can be
replaced with customized keywords and templates. Again, run
:hg:`kwdemo` to control the results of your configuration changes.

Before changing/disabling active keywords, you must run :hg:`kwshrink`
to avoid storing expanded keywords in the change history.

To force expansion after enabling it, or a configuration change, run
:hg:`kwexpand`.

Expansions spanning more than one line and incremental expansions,
like CVS' $Log$, are not supported. A keyword template map "Log =
{desc}" expands to the first line of the changeset description.
'''

from mercurial import commands, context, cmdutil, dispatch, filelog, extensions
from mercurial import localrepo, match, patch, templatefilters, templater, util
from mercurial import scmutil
from mercurial.hgweb import webcommands
from mercurial.i18n import _
import os, re, shutil, tempfile

commands.optionalrepo += ' kwdemo'
commands.inferrepo += ' kwexpand kwfiles kwshrink'

cmdtable = {}
command = cmdutil.command(cmdtable)
testedwith = 'internal'

# hg commands that do not act on keywords
nokwcommands = ('add addremove annotate bundle export grep incoming init log'
                ' outgoing push tip verify convert email glog')

# hg commands that trigger expansion only when writing to working dir,
# not when reading filelog, and unexpand when reading from working dir
restricted = 'merge kwexpand kwshrink record qrecord resolve transplant'

# names of extensions using dorecord
recordextensions = 'record'

colortable = {
    'kwfiles.enabled': 'green bold',
    'kwfiles.deleted': 'cyan bold underline',
    'kwfiles.enabledunknown': 'green',
    'kwfiles.ignored': 'bold',
    'kwfiles.ignoredunknown': 'none'
}

# date like in cvs' $Date
def utcdate(text):
    ''':utcdate: Date. Returns a UTC-date in this format: "2009/08/18 11:00:13".
    '''
    return util.datestr((util.parsedate(text)[0], 0), '%Y/%m/%d %H:%M:%S')
# date like in svn's $Date
def svnisodate(text):
    ''':svnisodate: Date. Returns a date in this format: "2009-08-18 13:00:13
    +0200 (Tue, 18 Aug 2009)".
    '''
    return util.datestr(text, '%Y-%m-%d %H:%M:%S %1%2 (%a, %d %b %Y)')
# date like in svn's $Id
def svnutcdate(text):
    ''':svnutcdate: Date. Returns a UTC-date in this format: "2009-08-18
    11:00:13Z".
    '''
    return util.datestr((util.parsedate(text)[0], 0), '%Y-%m-%d %H:%M:%SZ')

templatefilters.filters.update({'utcdate': utcdate,
                                'svnisodate': svnisodate,
                                'svnutcdate': svnutcdate})

# make keyword tools accessible
kwtools = {'templater': None, 'hgcmd': ''}

def _defaultkwmaps(ui):
    '''Returns default keywordmaps according to keywordset configuration.'''
    templates = {
        'Revision': '{node|short}',
        'Author': '{author|user}',
    }
    kwsets = ({
        'Date': '{date|utcdate}',
        'RCSfile': '{file|basename},v',
        'RCSFile': '{file|basename},v', # kept for backwards compatibility
                                        # with hg-keyword
        'Source': '{root}/{file},v',
        'Id': '{file|basename},v {node|short} {date|utcdate} {author|user}',
        'Header': '{root}/{file},v {node|short} {date|utcdate} {author|user}',
    }, {
        'Date': '{date|svnisodate}',
        'Id': '{file|basename},v {node|short} {date|svnutcdate} {author|user}',
        'LastChangedRevision': '{node|short}',
        'LastChangedBy': '{author|user}',
        'LastChangedDate': '{date|svnisodate}',
    })
    templates.update(kwsets[ui.configbool('keywordset', 'svn')])
    return templates

def _shrinktext(text, subfunc):
    '''Helper for keyword expansion removal in text.
    Depending on subfunc also returns number of substitutions.'''
    return subfunc(r'$\1$', text)

def _preselect(wstatus, changed):
    '''Retrieves modified and added files from a working directory state
    and returns the subset of each contained in given changed files
    retrieved from a change context.'''
    modified, added = wstatus[:2]
    modified = [f for f in modified if f in changed]
    added = [f for f in added if f in changed]
    return modified, added


class kwtemplater(object):
    '''
    Sets up keyword templates, corresponding keyword regex, and
    provides keyword substitution functions.
    '''

    def __init__(self, ui, repo, inc, exc):
        self.ui = ui
        self.repo = repo
        self.match = match.match(repo.root, '', [], inc, exc)
        self.restrict = kwtools['hgcmd'] in restricted.split()
        self.postcommit = False

        kwmaps = self.ui.configitems('keywordmaps')
        if kwmaps: # override default templates
            self.templates = dict((k, templater.parsestring(v, False))
                                  for k, v in kwmaps)
        else:
            self.templates = _defaultkwmaps(self.ui)

    @util.propertycache
    def escape(self):
        '''Returns bar-separated and escaped keywords.'''
        return '|'.join(map(re.escape, self.templates.keys()))

    @util.propertycache
    def rekw(self):
        '''Returns regex for unexpanded keywords.'''
        return re.compile(r'\$(%s)\$' % self.escape)

    @util.propertycache
    def rekwexp(self):
        '''Returns regex for expanded keywords.'''
        return re.compile(r'\$(%s): [^$\n\r]*? \$' % self.escape)

    def substitute(self, data, path, ctx, subfunc):
        '''Replaces keywords in data with expanded template.'''
        def kwsub(mobj):
            kw = mobj.group(1)
            ct = cmdutil.changeset_templater(self.ui, self.repo,
                                             False, None, '', False)
            ct.use_template(self.templates[kw])
            self.ui.pushbuffer()
            ct.show(ctx, root=self.repo.root, file=path)
            ekw = templatefilters.firstline(self.ui.popbuffer())
            return '$%s: %s $' % (kw, ekw)
        return subfunc(kwsub, data)

    def linkctx(self, path, fileid):
        '''Similar to filelog.linkrev, but returns a changectx.'''
        return self.repo.filectx(path, fileid=fileid).changectx()

    def expand(self, path, node, data):
        '''Returns data with keywords expanded.'''
        if not self.restrict and self.match(path) and not util.binary(data):
            ctx = self.linkctx(path, node)
            return self.substitute(data, path, ctx, self.rekw.sub)
        return data

    def iskwfile(self, cand, ctx):
        '''Returns subset of candidates which are configured for keyword
        expansion but are not symbolic links.'''
        return [f for f in cand if self.match(f) and 'l' not in ctx.flags(f)]

    def overwrite(self, ctx, candidates, lookup, expand, rekw=False):
        '''Overwrites selected files expanding/shrinking keywords.'''
        if self.restrict or lookup or self.postcommit: # exclude kw_copy
            candidates = self.iskwfile(candidates, ctx)
        if not candidates:
            return
        kwcmd = self.restrict and lookup # kwexpand/kwshrink
        if self.restrict or expand and lookup:
            mf = ctx.manifest()
        if self.restrict or rekw:
            re_kw = self.rekw
        else:
            re_kw = self.rekwexp
        if expand:
            msg = _('overwriting %s expanding keywords\n')
        else:
            msg = _('overwriting %s shrinking keywords\n')
        for f in candidates:
            if self.restrict:
                data = self.repo.file(f).read(mf[f])
            else:
                data = self.repo.wread(f)
            if util.binary(data):
                continue
            if expand:
                if lookup:
                    ctx = self.linkctx(f, mf[f])
                data, found = self.substitute(data, f, ctx, re_kw.subn)
            elif self.restrict:
                found = re_kw.search(data)
            else:
                data, found = _shrinktext(data, re_kw.subn)
            if found:
                self.ui.note(msg % f)
                fp = self.repo.wopener(f, "wb", atomictemp=True)
                fp.write(data)
                fp.close()
                if kwcmd:
                    self.repo.dirstate.normal(f)
                elif self.postcommit:
                    self.repo.dirstate.normallookup(f)

    def shrink(self, fname, text):
        '''Returns text with all keyword substitutions removed.'''
        if self.match(fname) and not util.binary(text):
            return _shrinktext(text, self.rekwexp.sub)
        return text

    def shrinklines(self, fname, lines):
        '''Returns lines with keyword substitutions removed.'''
        if self.match(fname):
            text = ''.join(lines)
            if not util.binary(text):
                return _shrinktext(text, self.rekwexp.sub).splitlines(True)
        return lines

    def wread(self, fname, data):
        '''If in restricted mode returns data read from wdir with
        keyword substitutions removed.'''
        if self.restrict:
            return self.shrink(fname, data)
        return data

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
        if self.renamed(node):
            return data
        return self.kwt.expand(self.path, node, data)

    def add(self, text, meta, tr, link, p1=None, p2=None):
        '''Removes keyword substitutions when adding to filelog.'''
        text = self.kwt.shrink(self.path, text)
        return super(kwfilelog, self).add(text, meta, tr, link, p1, p2)

    def cmp(self, node, text):
        '''Removes keyword substitutions for comparison.'''
        text = self.kwt.shrink(self.path, text)
        return super(kwfilelog, self).cmp(node, text)

def _status(ui, repo, wctx, kwt, *pats, **opts):
    '''Bails out if [keyword] configuration is not active.
    Returns status of working directory.'''
    if kwt:
        return repo.status(match=scmutil.match(wctx, pats, opts), clean=True,
                           unknown=opts.get('unknown') or opts.get('all'))
    if ui.configitems('keyword'):
        raise util.Abort(_('[keyword] patterns cannot match'))
    raise util.Abort(_('no [keyword] patterns configured'))

def _kwfwrite(ui, repo, expand, *pats, **opts):
    '''Selects files and passes them to kwtemplater.overwrite.'''
    wctx = repo[None]
    if len(wctx.parents()) > 1:
        raise util.Abort(_('outstanding uncommitted merge'))
    kwt = kwtools['templater']
    wlock = repo.wlock()
    try:
        status = _status(ui, repo, wctx, kwt, *pats, **opts)
        modified, added, removed, deleted, unknown, ignored, clean = status
        if modified or added or removed or deleted:
            raise util.Abort(_('outstanding uncommitted changes'))
        kwt.overwrite(wctx, clean, True, expand)
    finally:
        wlock.release()

@command('kwdemo',
         [('d', 'default', None, _('show default keyword template maps')),
          ('f', 'rcfile', '',
           _('read maps from rcfile'), _('FILE'))],
         _('hg kwdemo [-d] [-f RCFILE] [TEMPLATEMAP]...'))
def demo(ui, repo, *args, **opts):
    '''print [keywordmaps] configuration and an expansion example

    Show current, custom, or default keyword template maps and their
    expansions.

    Extend the current configuration by specifying maps as arguments
    and using -f/--rcfile to source an external hgrc file.

    Use -d/--default to disable current configuration.

    See :hg:`help templates` for information on templates and filters.
    '''
    def demoitems(section, items):
        ui.write('[%s]\n' % section)
        for k, v in sorted(items):
            ui.write('%s = %s\n' % (k, v))

    fn = 'demo.txt'
    tmpdir = tempfile.mkdtemp('', 'kwdemo.')
    ui.note(_('creating temporary repository at %s\n') % tmpdir)
    repo = localrepo.localrepository(repo.baseui, tmpdir, True)
    ui.setconfig('keyword', fn, '')
    svn = ui.configbool('keywordset', 'svn')
    # explicitly set keywordset for demo output
    ui.setconfig('keywordset', 'svn', svn)

    uikwmaps = ui.configitems('keywordmaps')
    if args or opts.get('rcfile'):
        ui.status(_('\n\tconfiguration using custom keyword template maps\n'))
        if uikwmaps:
            ui.status(_('\textending current template maps\n'))
        if opts.get('default') or not uikwmaps:
            if svn:
                ui.status(_('\toverriding default svn keywordset\n'))
            else:
                ui.status(_('\toverriding default cvs keywordset\n'))
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
        if svn:
            ui.status(_('\n\tconfiguration using default svn keywordset\n'))
        else:
            ui.status(_('\n\tconfiguration using default cvs keywordset\n'))
        kwmaps = _defaultkwmaps(ui)
        if uikwmaps:
            ui.status(_('\tdisabling current template maps\n'))
            for k, v in kwmaps.iteritems():
                ui.setconfig('keywordmaps', k, v)
    else:
        ui.status(_('\n\tconfiguration using current keyword template maps\n'))
        if uikwmaps:
            kwmaps = dict(uikwmaps)
        else:
            kwmaps = _defaultkwmaps(ui)

    uisetup(ui)
    reposetup(ui, repo)
    ui.write('[extensions]\nkeyword =\n')
    demoitems('keyword', ui.configitems('keyword'))
    demoitems('keywordset', ui.configitems('keywordset'))
    demoitems('keywordmaps', kwmaps.iteritems())
    keywords = '$' + '$\n$'.join(sorted(kwmaps.keys())) + '$\n'
    repo.wopener.write(fn, keywords)
    repo[None].add([fn])
    ui.note(_('\nkeywords written to %s:\n') % fn)
    ui.note(keywords)
    repo.dirstate.setbranch('demobranch')
    for name, cmd in ui.configitems('hooks'):
        if name.split('.', 1)[0].find('commit') > -1:
            repo.ui.setconfig('hooks', name, '')
    msg = _('hg keyword configuration and expansion example')
    ui.note("hg ci -m '%s'\n" % msg) # check-code-ignore
    repo.commit(text=msg)
    ui.status(_('\n\tkeywords expanded\n'))
    ui.write(repo.wread(fn))
    shutil.rmtree(tmpdir, ignore_errors=True)

@command('kwexpand', commands.walkopts, _('hg kwexpand [OPTION]... [FILE]...'))
def expand(ui, repo, *pats, **opts):
    '''expand keywords in the working directory

    Run after (re)enabling keyword expansion.

    kwexpand refuses to run if given files contain local changes.
    '''
    # 3rd argument sets expansion to True
    _kwfwrite(ui, repo, True, *pats, **opts)

@command('kwfiles',
         [('A', 'all', None, _('show keyword status flags of all files')),
          ('i', 'ignore', None, _('show files excluded from expansion')),
          ('u', 'unknown', None, _('only show unknown (not tracked) files')),
         ] + commands.walkopts,
         _('hg kwfiles [OPTION]... [FILE]...'))
def files(ui, repo, *pats, **opts):
    '''show files configured for keyword expansion

    List which files in the working directory are matched by the
    [keyword] configuration patterns.

    Useful to prevent inadvertent keyword expansion and to speed up
    execution by including only files that are actual candidates for
    expansion.

    See :hg:`help keyword` on how to construct patterns both for
    inclusion and exclusion of files.

    With -A/--all and -v/--verbose the codes used to show the status
    of files are::

      K = keyword expansion candidate
      k = keyword expansion candidate (not tracked)
      I = ignored
      i = ignored (not tracked)
    '''
    kwt = kwtools['templater']
    wctx = repo[None]
    status = _status(ui, repo, wctx, kwt, *pats, **opts)
    cwd = pats and repo.getcwd() or ''
    modified, added, removed, deleted, unknown, ignored, clean = status
    files = []
    if not opts.get('unknown') or opts.get('all'):
        files = sorted(modified + added + clean)
    kwfiles = kwt.iskwfile(files, wctx)
    kwdeleted = kwt.iskwfile(deleted, wctx)
    kwunknown = kwt.iskwfile(unknown, wctx)
    if not opts.get('ignore') or opts.get('all'):
        showfiles = kwfiles, kwdeleted, kwunknown
    else:
        showfiles = [], [], []
    if opts.get('all') or opts.get('ignore'):
        showfiles += ([f for f in files if f not in kwfiles],
                      [f for f in unknown if f not in kwunknown])
    kwlabels = 'enabled deleted enabledunknown ignored ignoredunknown'.split()
    kwstates = zip(kwlabels, 'K!kIi', showfiles)
    fm = ui.formatter('kwfiles', opts)
    fmt = '%.0s%s\n'
    if opts.get('all') or ui.verbose:
        fmt = '%s %s\n'
    for kwstate, char, filenames in kwstates:
        label = 'kwfiles.' + kwstate
        for f in filenames:
            fm.startitem()
            fm.write('kwstatus path', fmt, char,
                     repo.pathto(f, cwd), label=label)
    fm.end()

@command('kwshrink', commands.walkopts, _('hg kwshrink [OPTION]... [FILE]...'))
def shrink(ui, repo, *pats, **opts):
    '''revert expanded keywords in the working directory

    Must be run before changing/disabling active keywords.

    kwshrink refuses to run if given files contain local changes.
    '''
    # 3rd argument sets expansion to False
    _kwfwrite(ui, repo, False, *pats, **opts)


def uisetup(ui):
    ''' Monkeypatches dispatch._parse to retrieve user command.'''

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
        if (not repo.local() or kwtools['hgcmd'] in nokwcommands.split()
            or '.hg' in util.splitpath(repo.root)
            or repo._url.startswith('bundle:')):
            return
    except AttributeError:
        pass

    inc, exc = [], ['.hg*']
    for pat, opt in ui.configitems('keyword'):
        if opt != 'ignore':
            inc.append(pat)
        else:
            exc.append(pat)
    if not inc:
        return

    kwtools['templater'] = kwt = kwtemplater(ui, repo, inc, exc)

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
            n = super(kwrepo, self).commitctx(ctx, error)
            # no lock needed, only called from repo.commit() which already locks
            if not kwt.postcommit:
                restrict = kwt.restrict
                kwt.restrict = True
                kwt.overwrite(self[n], sorted(ctx.added() + ctx.modified()),
                              False, True)
                kwt.restrict = restrict
            return n

        def rollback(self, dryrun=False, force=False):
            wlock = self.wlock()
            try:
                if not dryrun:
                    changed = self['.'].files()
                ret = super(kwrepo, self).rollback(dryrun, force)
                if not dryrun:
                    ctx = self['.']
                    modified, added = _preselect(self[None].status(), changed)
                    kwt.overwrite(ctx, modified, True, True)
                    kwt.overwrite(ctx, added, True, False)
                return ret
            finally:
                wlock.release()

    # monkeypatches
    def kwpatchfile_init(orig, self, ui, gp, backend, store, eolmode=None):
        '''Monkeypatch/wrap patch.patchfile.__init__ to avoid
        rejects or conflicts due to expanded keywords in working dir.'''
        orig(self, ui, gp, backend, store, eolmode)
        # shrink keywords read from working dir
        self.lines = kwt.shrinklines(self.fname, self.lines)

    def kw_diff(orig, repo, node1=None, node2=None, match=None, changes=None,
                opts=None, prefix=''):
        '''Monkeypatch patch.diff to avoid expansion.'''
        kwt.restrict = True
        return orig(repo, node1, node2, match, changes, opts, prefix)

    def kwweb_skip(orig, web, req, tmpl):
        '''Wraps webcommands.x turning off keyword expansion.'''
        kwt.match = util.never
        return orig(web, req, tmpl)

    def kw_amend(orig, ui, repo, commitfunc, old, extra, pats, opts):
        '''Wraps cmdutil.amend expanding keywords after amend.'''
        wlock = repo.wlock()
        try:
            kwt.postcommit = True
            newid = orig(ui, repo, commitfunc, old, extra, pats, opts)
            if newid != old.node():
                ctx = repo[newid]
                kwt.restrict = True
                kwt.overwrite(ctx, ctx.files(), False, True)
                kwt.restrict = False
            return newid
        finally:
            wlock.release()

    def kw_copy(orig, ui, repo, pats, opts, rename=False):
        '''Wraps cmdutil.copy so that copy/rename destinations do not
        contain expanded keywords.
        Note that the source of a regular file destination may also be a
        symlink:
        hg cp sym x                -> x is symlink
        cp sym x; hg cp -A sym x   -> x is file (maybe expanded keywords)
        For the latter we have to follow the symlink to find out whether its
        target is configured for expansion and we therefore must unexpand the
        keywords in the destination.'''
        wlock = repo.wlock()
        try:
            orig(ui, repo, pats, opts, rename)
            if opts.get('dry_run'):
                return
            wctx = repo[None]
            cwd = repo.getcwd()

            def haskwsource(dest):
                '''Returns true if dest is a regular file and configured for
                expansion or a symlink which points to a file configured for
                expansion. '''
                source = repo.dirstate.copied(dest)
                if 'l' in wctx.flags(source):
                    source = scmutil.canonpath(repo.root, cwd,
                                               os.path.realpath(source))
                return kwt.match(source)

            candidates = [f for f in repo.dirstate.copies() if
                          'l' not in wctx.flags(f) and haskwsource(f)]
            kwt.overwrite(wctx, candidates, False, False)
        finally:
            wlock.release()

    def kw_dorecord(orig, ui, repo, commitfunc, *pats, **opts):
        '''Wraps record.dorecord expanding keywords after recording.'''
        wlock = repo.wlock()
        try:
            # record returns 0 even when nothing has changed
            # therefore compare nodes before and after
            kwt.postcommit = True
            ctx = repo['.']
            wstatus = repo[None].status()
            ret = orig(ui, repo, commitfunc, *pats, **opts)
            recctx = repo['.']
            if ctx != recctx:
                modified, added = _preselect(wstatus, recctx.files())
                kwt.restrict = False
                kwt.overwrite(recctx, modified, False, True)
                kwt.overwrite(recctx, added, False, True, True)
                kwt.restrict = True
            return ret
        finally:
            wlock.release()

    def kwfilectx_cmp(orig, self, fctx):
        # keyword affects data size, comparing wdir and filelog size does
        # not make sense
        if (fctx._filerev is None and
            (self._repo._encodefilterpats or
             kwt.match(fctx.path()) and 'l' not in fctx.flags() or
             self.size() - 4 == fctx.size()) or
            self.size() == fctx.size()):
            return self._filelog.cmp(self._filenode, fctx.data())
        return True

    extensions.wrapfunction(context.filectx, 'cmp', kwfilectx_cmp)
    extensions.wrapfunction(patch.patchfile, '__init__', kwpatchfile_init)
    extensions.wrapfunction(patch, 'diff', kw_diff)
    extensions.wrapfunction(cmdutil, 'amend', kw_amend)
    extensions.wrapfunction(cmdutil, 'copy', kw_copy)
    for c in 'annotate changeset rev filediff diff'.split():
        extensions.wrapfunction(webcommands, c, kwweb_skip)
    for name in recordextensions.split():
        try:
            record = extensions.find(name)
            extensions.wrapfunction(record, 'dorecord', kw_dorecord)
        except KeyError:
            pass

    repo.__class__ = kwrepo
