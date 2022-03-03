# keyword.py - $Keyword$ expansion for Mercurial
#
# Copyright 2007-2015 Christian Ebert <blacktrash@gmx.net>
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
# <https://mercurial-scm.org/wiki/KeywordPlan>.
#
# Keyword expansion is based on Mercurial's changeset template mappings.
#
# Binary files are not touched.
#
# Files to act upon/ignore are specified in the [keyword] section.
# Customized keyword template mappings in the [keywordmaps] section.
#
# Run 'hg help keyword' and 'hg kwdemo' to get info on configuration.

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


from __future__ import absolute_import

import os
import re
import weakref

from mercurial.i18n import _
from mercurial.pycompat import getattr
from mercurial.hgweb import webcommands

from mercurial import (
    cmdutil,
    context,
    dispatch,
    error,
    extensions,
    filelog,
    localrepo,
    logcmdutil,
    match,
    patch,
    pathutil,
    pycompat,
    registrar,
    scmutil,
    templatefilters,
    templateutil,
    util,
)
from mercurial.utils import (
    dateutil,
    stringutil,
)

cmdtable = {}
command = registrar.command(cmdtable)
# Note for extension authors: ONLY specify testedwith = 'ships-with-hg-core' for
# extensions which SHIP WITH MERCURIAL. Non-mainline extensions should
# be specifying the version(s) of Mercurial they are tested with, or
# leave the attribute unspecified.
testedwith = b'ships-with-hg-core'

# hg commands that do not act on keywords
nokwcommands = (
    b'add addremove annotate bundle export grep incoming init log'
    b' outgoing push tip verify convert email glog'
)

# webcommands that do not act on keywords
nokwwebcommands = b'annotate changeset rev filediff diff comparison'

# hg commands that trigger expansion only when writing to working dir,
# not when reading filelog, and unexpand when reading from working dir
restricted = (
    b'merge kwexpand kwshrink record qrecord resolve transplant'
    b' unshelve rebase graft backout histedit fetch'
)

# names of extensions using dorecord
recordextensions = b'record'

colortable = {
    b'kwfiles.enabled': b'green bold',
    b'kwfiles.deleted': b'cyan bold underline',
    b'kwfiles.enabledunknown': b'green',
    b'kwfiles.ignored': b'bold',
    b'kwfiles.ignoredunknown': b'none',
}

templatefilter = registrar.templatefilter()

configtable = {}
configitem = registrar.configitem(configtable)

configitem(
    b'keywordset',
    b'svn',
    default=False,
)
# date like in cvs' $Date
@templatefilter(b'utcdate', intype=templateutil.date)
def utcdate(date):
    """Date. Returns a UTC-date in this format: "2009/08/18 11:00:13"."""
    dateformat = b'%Y/%m/%d %H:%M:%S'
    return dateutil.datestr((date[0], 0), dateformat)


# date like in svn's $Date
@templatefilter(b'svnisodate', intype=templateutil.date)
def svnisodate(date):
    """Date. Returns a date in this format: "2009-08-18 13:00:13
    +0200 (Tue, 18 Aug 2009)".
    """
    return dateutil.datestr(date, b'%Y-%m-%d %H:%M:%S %1%2 (%a, %d %b %Y)')


# date like in svn's $Id
@templatefilter(b'svnutcdate', intype=templateutil.date)
def svnutcdate(date):
    """Date. Returns a UTC-date in this format: "2009-08-18
    11:00:13Z".
    """
    dateformat = b'%Y-%m-%d %H:%M:%SZ'
    return dateutil.datestr((date[0], 0), dateformat)


# make keyword tools accessible
kwtools = {b'hgcmd': b''}


def _defaultkwmaps(ui):
    '''Returns default keywordmaps according to keywordset configuration.'''
    templates = {
        b'Revision': b'{node|short}',
        b'Author': b'{author|user}',
    }
    kwsets = (
        {
            b'Date': b'{date|utcdate}',
            b'RCSfile': b'{file|basename},v',
            b'RCSFile': b'{file|basename},v',  # kept for backwards compatibility
            # with hg-keyword
            b'Source': b'{root}/{file},v',
            b'Id': b'{file|basename},v {node|short} {date|utcdate} {author|user}',
            b'Header': b'{root}/{file},v {node|short} {date|utcdate} {author|user}',
        },
        {
            b'Date': b'{date|svnisodate}',
            b'Id': b'{file|basename},v {node|short} {date|svnutcdate} {author|user}',
            b'LastChangedRevision': b'{node|short}',
            b'LastChangedBy': b'{author|user}',
            b'LastChangedDate': b'{date|svnisodate}',
        },
    )
    templates.update(kwsets[ui.configbool(b'keywordset', b'svn')])
    return templates


def _shrinktext(text, subfunc):
    """Helper for keyword expansion removal in text.
    Depending on subfunc also returns number of substitutions."""
    return subfunc(br'$\1$', text)


def _preselect(wstatus, changed):
    """Retrieves modified and added files from a working directory state
    and returns the subset of each contained in given changed files
    retrieved from a change context."""
    modified = [f for f in wstatus.modified if f in changed]
    added = [f for f in wstatus.added if f in changed]
    return modified, added


class kwtemplater(object):
    """
    Sets up keyword templates, corresponding keyword regex, and
    provides keyword substitution functions.
    """

    def __init__(self, ui, repo, inc, exc):
        self.ui = ui
        self._repo = weakref.ref(repo)
        self.match = match.match(repo.root, b'', [], inc, exc)
        self.restrict = kwtools[b'hgcmd'] in restricted.split()
        self.postcommit = False

        kwmaps = self.ui.configitems(b'keywordmaps')
        if kwmaps:  # override default templates
            self.templates = dict(kwmaps)
        else:
            self.templates = _defaultkwmaps(self.ui)

    @property
    def repo(self):
        return self._repo()

    @util.propertycache
    def escape(self):
        '''Returns bar-separated and escaped keywords.'''
        return b'|'.join(map(stringutil.reescape, self.templates.keys()))

    @util.propertycache
    def rekw(self):
        '''Returns regex for unexpanded keywords.'''
        return re.compile(br'\$(%s)\$' % self.escape)

    @util.propertycache
    def rekwexp(self):
        '''Returns regex for expanded keywords.'''
        return re.compile(br'\$(%s): [^$\n\r]*? \$' % self.escape)

    def substitute(self, data, path, ctx, subfunc):
        '''Replaces keywords in data with expanded template.'''

        def kwsub(mobj):
            kw = mobj.group(1)
            ct = logcmdutil.maketemplater(
                self.ui, self.repo, self.templates[kw]
            )
            self.ui.pushbuffer()
            ct.show(ctx, root=self.repo.root, file=path)
            ekw = templatefilters.firstline(self.ui.popbuffer())
            return b'$%s: %s $' % (kw, ekw)

        return subfunc(kwsub, data)

    def linkctx(self, path, fileid):
        '''Similar to filelog.linkrev, but returns a changectx.'''
        return self.repo.filectx(path, fileid=fileid).changectx()

    def expand(self, path, node, data):
        '''Returns data with keywords expanded.'''
        if (
            not self.restrict
            and self.match(path)
            and not stringutil.binary(data)
        ):
            ctx = self.linkctx(path, node)
            return self.substitute(data, path, ctx, self.rekw.sub)
        return data

    def iskwfile(self, cand, ctx):
        """Returns subset of candidates which are configured for keyword
        expansion but are not symbolic links."""
        return [f for f in cand if self.match(f) and b'l' not in ctx.flags(f)]

    def overwrite(self, ctx, candidates, lookup, expand, rekw=False):
        '''Overwrites selected files expanding/shrinking keywords.'''
        if self.restrict or lookup or self.postcommit:  # exclude kw_copy
            candidates = self.iskwfile(candidates, ctx)
        if not candidates:
            return
        kwcmd = self.restrict and lookup  # kwexpand/kwshrink
        if self.restrict or expand and lookup:
            mf = ctx.manifest()
        if self.restrict or rekw:
            re_kw = self.rekw
        else:
            re_kw = self.rekwexp
        if expand:
            msg = _(b'overwriting %s expanding keywords\n')
        else:
            msg = _(b'overwriting %s shrinking keywords\n')
        for f in candidates:
            if self.restrict:
                data = self.repo.file(f).read(mf[f])
            else:
                data = self.repo.wread(f)
            if stringutil.binary(data):
                continue
            if expand:
                parents = ctx.parents()
                if lookup:
                    ctx = self.linkctx(f, mf[f])
                elif self.restrict and len(parents) > 1:
                    # merge commit
                    # in case of conflict f is in modified state during
                    # merge, even if f does not differ from f in parent
                    for p in parents:
                        if f in p and not p[f].cmp(ctx[f]):
                            ctx = p[f].changectx()
                            break
                data, found = self.substitute(data, f, ctx, re_kw.subn)
            elif self.restrict:
                found = re_kw.search(data)
            else:
                data, found = _shrinktext(data, re_kw.subn)
            if found:
                self.ui.note(msg % f)
                fp = self.repo.wvfs(f, b"wb", atomictemp=True)
                fp.write(data)
                fp.close()
                if kwcmd:
                    self.repo.dirstate.set_clean(f)
                elif self.postcommit:
                    self.repo.dirstate.update_file_p1(f, p1_tracked=True)

    def shrink(self, fname, text):
        '''Returns text with all keyword substitutions removed.'''
        if self.match(fname) and not stringutil.binary(text):
            return _shrinktext(text, self.rekwexp.sub)
        return text

    def shrinklines(self, fname, lines):
        '''Returns lines with keyword substitutions removed.'''
        if self.match(fname):
            text = b''.join(lines)
            if not stringutil.binary(text):
                return _shrinktext(text, self.rekwexp.sub).splitlines(True)
        return lines

    def wread(self, fname, data):
        """If in restricted mode returns data read from wdir with
        keyword substitutions removed."""
        if self.restrict:
            return self.shrink(fname, data)
        return data


class kwfilelog(filelog.filelog):
    """
    Subclass of filelog to hook into its read, add, cmp methods.
    Keywords are "stored" unexpanded, and processed on reading.
    """

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
    """Bails out if [keyword] configuration is not active.
    Returns status of working directory."""
    if kwt:
        opts = pycompat.byteskwargs(opts)
        return repo.status(
            match=scmutil.match(wctx, pats, opts),
            clean=True,
            unknown=opts.get(b'unknown') or opts.get(b'all'),
        )
    if ui.configitems(b'keyword'):
        raise error.Abort(_(b'[keyword] patterns cannot match'))
    raise error.Abort(_(b'no [keyword] patterns configured'))


def _kwfwrite(ui, repo, expand, *pats, **opts):
    '''Selects files and passes them to kwtemplater.overwrite.'''
    wctx = repo[None]
    if len(wctx.parents()) > 1:
        raise error.Abort(_(b'outstanding uncommitted merge'))
    kwt = getattr(repo, '_keywordkwt', None)
    with repo.wlock():
        status = _status(ui, repo, wctx, kwt, *pats, **opts)
        if status.modified or status.added or status.removed or status.deleted:
            raise error.Abort(_(b'outstanding uncommitted changes'))
        kwt.overwrite(wctx, status.clean, True, expand)


@command(
    b'kwdemo',
    [
        (b'd', b'default', None, _(b'show default keyword template maps')),
        (b'f', b'rcfile', b'', _(b'read maps from rcfile'), _(b'FILE')),
    ],
    _(b'hg kwdemo [-d] [-f RCFILE] [TEMPLATEMAP]...'),
    optionalrepo=True,
)
def demo(ui, repo, *args, **opts):
    """print [keywordmaps] configuration and an expansion example

    Show current, custom, or default keyword template maps and their
    expansions.

    Extend the current configuration by specifying maps as arguments
    and using -f/--rcfile to source an external hgrc file.

    Use -d/--default to disable current configuration.

    See :hg:`help templates` for information on templates and filters.
    """

    def demoitems(section, items):
        ui.write(b'[%s]\n' % section)
        for k, v in sorted(items):
            if isinstance(v, bool):
                v = stringutil.pprint(v)
            ui.write(b'%s = %s\n' % (k, v))

    fn = b'demo.txt'
    tmpdir = pycompat.mkdtemp(b'', b'kwdemo.')
    ui.note(_(b'creating temporary repository at %s\n') % tmpdir)
    if repo is None:
        baseui = ui
    else:
        baseui = repo.baseui
    repo = localrepo.instance(baseui, tmpdir, create=True)
    ui.setconfig(b'keyword', fn, b'', b'keyword')
    svn = ui.configbool(b'keywordset', b'svn')
    # explicitly set keywordset for demo output
    ui.setconfig(b'keywordset', b'svn', svn, b'keyword')

    uikwmaps = ui.configitems(b'keywordmaps')
    if args or opts.get('rcfile'):
        ui.status(_(b'\n\tconfiguration using custom keyword template maps\n'))
        if uikwmaps:
            ui.status(_(b'\textending current template maps\n'))
        if opts.get('default') or not uikwmaps:
            if svn:
                ui.status(_(b'\toverriding default svn keywordset\n'))
            else:
                ui.status(_(b'\toverriding default cvs keywordset\n'))
        if opts.get('rcfile'):
            ui.readconfig(opts.get(b'rcfile'))
        if args:
            # simulate hgrc parsing
            rcmaps = b'[keywordmaps]\n%s\n' % b'\n'.join(args)
            repo.vfs.write(b'hgrc', rcmaps)
            ui.readconfig(repo.vfs.join(b'hgrc'))
        kwmaps = dict(ui.configitems(b'keywordmaps'))
    elif opts.get('default'):
        if svn:
            ui.status(_(b'\n\tconfiguration using default svn keywordset\n'))
        else:
            ui.status(_(b'\n\tconfiguration using default cvs keywordset\n'))
        kwmaps = _defaultkwmaps(ui)
        if uikwmaps:
            ui.status(_(b'\tdisabling current template maps\n'))
            for k, v in pycompat.iteritems(kwmaps):
                ui.setconfig(b'keywordmaps', k, v, b'keyword')
    else:
        ui.status(_(b'\n\tconfiguration using current keyword template maps\n'))
        if uikwmaps:
            kwmaps = dict(uikwmaps)
        else:
            kwmaps = _defaultkwmaps(ui)

    uisetup(ui)
    reposetup(ui, repo)
    ui.writenoi18n(b'[extensions]\nkeyword =\n')
    demoitems(b'keyword', ui.configitems(b'keyword'))
    demoitems(b'keywordset', ui.configitems(b'keywordset'))
    demoitems(b'keywordmaps', pycompat.iteritems(kwmaps))
    keywords = b'$' + b'$\n$'.join(sorted(kwmaps.keys())) + b'$\n'
    repo.wvfs.write(fn, keywords)
    repo[None].add([fn])
    ui.note(_(b'\nkeywords written to %s:\n') % fn)
    ui.note(keywords)
    with repo.wlock():
        repo.dirstate.setbranch(b'demobranch')
    for name, cmd in ui.configitems(b'hooks'):
        if name.split(b'.', 1)[0].find(b'commit') > -1:
            repo.ui.setconfig(b'hooks', name, b'', b'keyword')
    msg = _(b'hg keyword configuration and expansion example')
    ui.note((b"hg ci -m '%s'\n" % msg))
    repo.commit(text=msg)
    ui.status(_(b'\n\tkeywords expanded\n'))
    ui.write(repo.wread(fn))
    repo.wvfs.rmtree(repo.root)


@command(
    b'kwexpand',
    cmdutil.walkopts,
    _(b'hg kwexpand [OPTION]... [FILE]...'),
    inferrepo=True,
)
def expand(ui, repo, *pats, **opts):
    """expand keywords in the working directory

    Run after (re)enabling keyword expansion.

    kwexpand refuses to run if given files contain local changes.
    """
    # 3rd argument sets expansion to True
    _kwfwrite(ui, repo, True, *pats, **opts)


@command(
    b'kwfiles',
    [
        (b'A', b'all', None, _(b'show keyword status flags of all files')),
        (b'i', b'ignore', None, _(b'show files excluded from expansion')),
        (b'u', b'unknown', None, _(b'only show unknown (not tracked) files')),
    ]
    + cmdutil.walkopts,
    _(b'hg kwfiles [OPTION]... [FILE]...'),
    inferrepo=True,
)
def files(ui, repo, *pats, **opts):
    """show files configured for keyword expansion

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
    """
    kwt = getattr(repo, '_keywordkwt', None)
    wctx = repo[None]
    status = _status(ui, repo, wctx, kwt, *pats, **opts)
    if pats:
        cwd = repo.getcwd()
    else:
        cwd = b''
    files = []
    opts = pycompat.byteskwargs(opts)
    if not opts.get(b'unknown') or opts.get(b'all'):
        files = sorted(status.modified + status.added + status.clean)
    kwfiles = kwt.iskwfile(files, wctx)
    kwdeleted = kwt.iskwfile(status.deleted, wctx)
    kwunknown = kwt.iskwfile(status.unknown, wctx)
    if not opts.get(b'ignore') or opts.get(b'all'):
        showfiles = kwfiles, kwdeleted, kwunknown
    else:
        showfiles = [], [], []
    if opts.get(b'all') or opts.get(b'ignore'):
        showfiles += (
            [f for f in files if f not in kwfiles],
            [f for f in status.unknown if f not in kwunknown],
        )
    kwlabels = b'enabled deleted enabledunknown ignored ignoredunknown'.split()
    kwstates = zip(kwlabels, pycompat.bytestr(b'K!kIi'), showfiles)
    fm = ui.formatter(b'kwfiles', opts)
    fmt = b'%.0s%s\n'
    if opts.get(b'all') or ui.verbose:
        fmt = b'%s %s\n'
    for kwstate, char, filenames in kwstates:
        label = b'kwfiles.' + kwstate
        for f in filenames:
            fm.startitem()
            fm.data(kwstatus=char, path=f)
            fm.plain(fmt % (char, repo.pathto(f, cwd)), label=label)
    fm.end()


@command(
    b'kwshrink',
    cmdutil.walkopts,
    _(b'hg kwshrink [OPTION]... [FILE]...'),
    inferrepo=True,
)
def shrink(ui, repo, *pats, **opts):
    """revert expanded keywords in the working directory

    Must be run before changing/disabling active keywords.

    kwshrink refuses to run if given files contain local changes.
    """
    # 3rd argument sets expansion to False
    _kwfwrite(ui, repo, False, *pats, **opts)


# monkeypatches


def kwpatchfile_init(orig, self, ui, gp, backend, store, eolmode=None):
    """Monkeypatch/wrap patch.patchfile.__init__ to avoid
    rejects or conflicts due to expanded keywords in working dir."""
    orig(self, ui, gp, backend, store, eolmode)
    kwt = getattr(getattr(backend, 'repo', None), '_keywordkwt', None)
    if kwt:
        # shrink keywords read from working dir
        self.lines = kwt.shrinklines(self.fname, self.lines)


def kwdiff(orig, repo, *args, **kwargs):
    '''Monkeypatch patch.diff to avoid expansion.'''
    kwt = getattr(repo, '_keywordkwt', None)
    if kwt:
        restrict = kwt.restrict
        kwt.restrict = True
    try:
        for chunk in orig(repo, *args, **kwargs):
            yield chunk
    finally:
        if kwt:
            kwt.restrict = restrict


def kwweb_skip(orig, web):
    '''Wraps webcommands.x turning off keyword expansion.'''
    kwt = getattr(web.repo, '_keywordkwt', None)
    if kwt:
        origmatch = kwt.match
        kwt.match = util.never
    try:
        for chunk in orig(web):
            yield chunk
    finally:
        if kwt:
            kwt.match = origmatch


def kw_amend(orig, ui, repo, old, extra, pats, opts):
    '''Wraps cmdutil.amend expanding keywords after amend.'''
    kwt = getattr(repo, '_keywordkwt', None)
    if kwt is None:
        return orig(ui, repo, old, extra, pats, opts)
    with repo.wlock(), repo.dirstate.parentchange():
        kwt.postcommit = True
        newid = orig(ui, repo, old, extra, pats, opts)
        if newid != old.node():
            ctx = repo[newid]
            kwt.restrict = True
            kwt.overwrite(ctx, ctx.files(), False, True)
            kwt.restrict = False
        return newid


def kw_copy(orig, ui, repo, pats, opts, rename=False):
    """Wraps cmdutil.copy so that copy/rename destinations do not
    contain expanded keywords.
    Note that the source of a regular file destination may also be a
    symlink:
    hg cp sym x                -> x is symlink
    cp sym x; hg cp -A sym x   -> x is file (maybe expanded keywords)
    For the latter we have to follow the symlink to find out whether its
    target is configured for expansion and we therefore must unexpand the
    keywords in the destination."""
    kwt = getattr(repo, '_keywordkwt', None)
    if kwt is None:
        return orig(ui, repo, pats, opts, rename)
    with repo.wlock():
        orig(ui, repo, pats, opts, rename)
        if opts.get(b'dry_run'):
            return
        wctx = repo[None]
        cwd = repo.getcwd()

        def haskwsource(dest):
            """Returns true if dest is a regular file and configured for
            expansion or a symlink which points to a file configured for
            expansion."""
            source = repo.dirstate.copied(dest)
            if b'l' in wctx.flags(source):
                source = pathutil.canonpath(
                    repo.root, cwd, os.path.realpath(source)
                )
            return kwt.match(source)

        candidates = [
            f
            for f in repo.dirstate.copies()
            if b'l' not in wctx.flags(f) and haskwsource(f)
        ]
        kwt.overwrite(wctx, candidates, False, False)


def kw_dorecord(orig, ui, repo, commitfunc, *pats, **opts):
    '''Wraps record.dorecord expanding keywords after recording.'''
    kwt = getattr(repo, '_keywordkwt', None)
    if kwt is None:
        return orig(ui, repo, commitfunc, *pats, **opts)
    with repo.wlock():
        # record returns 0 even when nothing has changed
        # therefore compare nodes before and after
        kwt.postcommit = True
        ctx = repo[b'.']
        wstatus = ctx.status()
        ret = orig(ui, repo, commitfunc, *pats, **opts)
        recctx = repo[b'.']
        if ctx != recctx:
            modified, added = _preselect(wstatus, recctx.files())
            kwt.restrict = False
            with repo.dirstate.parentchange():
                kwt.overwrite(recctx, modified, False, True)
                kwt.overwrite(recctx, added, False, True, True)
            kwt.restrict = True
        return ret


def kwfilectx_cmp(orig, self, fctx):
    if fctx._customcmp:
        return fctx.cmp(self)
    kwt = getattr(self._repo, '_keywordkwt', None)
    if kwt is None:
        return orig(self, fctx)
    # keyword affects data size, comparing wdir and filelog size does
    # not make sense
    if (
        fctx._filenode is None
        and (
            self._repo._encodefilterpats
            or kwt.match(fctx.path())
            and b'l' not in fctx.flags()
            or self.size() - 4 == fctx.size()
        )
        or self.size() == fctx.size()
    ):
        return self._filelog.cmp(self._filenode, fctx.data())
    return True


def uisetup(ui):
    """Monkeypatches dispatch._parse to retrieve user command.
    Overrides file method to return kwfilelog instead of filelog
    if file matches user configuration.
    Wraps commit to overwrite configured files with updated
    keyword substitutions.
    Monkeypatches patch and webcommands."""

    def kwdispatch_parse(orig, ui, args):
        '''Monkeypatch dispatch._parse to obtain running hg command.'''
        cmd, func, args, options, cmdoptions = orig(ui, args)
        kwtools[b'hgcmd'] = cmd
        return cmd, func, args, options, cmdoptions

    extensions.wrapfunction(dispatch, b'_parse', kwdispatch_parse)

    extensions.wrapfunction(context.filectx, b'cmp', kwfilectx_cmp)
    extensions.wrapfunction(patch.patchfile, b'__init__', kwpatchfile_init)
    extensions.wrapfunction(patch, b'diff', kwdiff)
    extensions.wrapfunction(cmdutil, b'amend', kw_amend)
    extensions.wrapfunction(cmdutil, b'copy', kw_copy)
    extensions.wrapfunction(cmdutil, b'dorecord', kw_dorecord)
    for c in nokwwebcommands.split():
        extensions.wrapfunction(webcommands, c, kwweb_skip)


def reposetup(ui, repo):
    '''Sets up repo as kwrepo for keyword substitution.'''

    try:
        if (
            not repo.local()
            or kwtools[b'hgcmd'] in nokwcommands.split()
            or b'.hg' in util.splitpath(repo.root)
            or repo._url.startswith(b'bundle:')
        ):
            return
    except AttributeError:
        pass

    inc, exc = [], [b'.hg*']
    for pat, opt in ui.configitems(b'keyword'):
        if opt != b'ignore':
            inc.append(pat)
        else:
            exc.append(pat)
    if not inc:
        return

    kwt = kwtemplater(ui, repo, inc, exc)

    class kwrepo(repo.__class__):
        def file(self, f):
            if f[0] == b'/':
                f = f[1:]
            return kwfilelog(self.svfs, kwt, f)

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

        def kwcommitctx(self, ctx, error=False, origctx=None):
            n = super(kwrepo, self).commitctx(ctx, error, origctx)
            # no lock needed, only called from repo.commit() which already locks
            if not kwt.postcommit:
                restrict = kwt.restrict
                kwt.restrict = True
                kwt.overwrite(
                    self[n], sorted(ctx.added() + ctx.modified()), False, True
                )
                kwt.restrict = restrict
            return n

        def rollback(self, dryrun=False, force=False):
            with self.wlock():
                origrestrict = kwt.restrict
                try:
                    if not dryrun:
                        changed = self[b'.'].files()
                    ret = super(kwrepo, self).rollback(dryrun, force)
                    if not dryrun:
                        ctx = self[b'.']
                        modified, added = _preselect(ctx.status(), changed)
                        kwt.restrict = False
                        kwt.overwrite(ctx, modified, True, True)
                        kwt.overwrite(ctx, added, True, False)
                    return ret
                finally:
                    kwt.restrict = origrestrict

    repo.__class__ = kwrepo
    repo._keywordkwt = kwt
