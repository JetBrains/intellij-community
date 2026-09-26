# fix - rewrite file content in changesets and working copy
#
# Copyright 2018 Google LLC.
#
# This software may be used and distributed according to the terms of the
# GNU General Public License version 2 or any later version.
"""rewrite file content in changesets or working copy (EXPERIMENTAL)

Provides a command that runs configured tools on the contents of modified files,
writing back any fixes to the working copy or replacing changesets.

Fixer tools are run in the repository's root directory. This allows them to read
configuration files from the working copy, or even write to the working copy.
The working copy is not updated to match the revision being fixed. In fact,
several revisions may be fixed in parallel. Writes to the working copy are not
amended into the revision being fixed; fixer tools MUST always read content to
be fixed from stdin, and write fixed file content back to stdout.

Here is an example configuration that causes :hg:`fix` to apply automatic
formatting fixes to modified lines in C++ code::

  [fix]
  clang-format:command=clang-format --assume-filename={rootpath}
  clang-format:linerange=--lines={first}:{last}
  clang-format:pattern=set:**.cpp or **.hpp

The :command suboption forms the first part of the shell command that will be
used to fix a file. The content of the file is passed on standard input, and the
fixed file content is expected on standard output. Any output on standard error
will be displayed as a warning. If the exit status is not zero, the file will
not be affected. A placeholder warning is displayed if there is a non-zero exit
status but no standard error output. Some values may be substituted into the
command::

  {rootpath}  The path of the file being fixed, relative to the repo root
  {basename}  The name of the file being fixed, without the directory path

If the :linerange suboption is set, the tool will only be run if there are
changed lines in a file. The value of this suboption is appended to the shell
command once for every range of changed lines in the file. Some values may be
substituted into the command::

  {first}   The 1-based line number of the first line in the modified range
  {last}    The 1-based line number of the last line in the modified range

Deleted sections of a file will be ignored by :linerange, because there is no
corresponding line range in the version being fixed.

By default, tools that set :linerange will only be executed if there is at least
one changed line range. This is meant to prevent accidents like running a code
formatter in such a way that it unexpectedly reformats the whole file. If such a
tool needs to operate on unchanged files, it should set the :skipclean suboption
to false.

The :pattern suboption determines which files will be passed through each
configured tool. See :hg:`help patterns` for possible values. However, all
patterns are relative to the repo root, even if that text says they are relative
to the current working directory. If there are file arguments to :hg:`fix`, the
intersection of these patterns is used.

There is also a configurable limit for the maximum size of file that will be
processed by :hg:`fix`::

  [fix]
  maxfilesize = 2MB

Normally, execution of configured tools will continue after a failure (indicated
by a non-zero exit status). It can also be configured to abort after the first
such failure, so that no files will be affected if any tool fails. This abort
will also cause :hg:`fix` to exit with a non-zero status::

  [fix]
  failure = abort

When multiple tools are configured to affect a file, they execute in an order
defined by the :priority suboption. The priority suboption has a default value
of zero for each tool. Tools are executed in order of descending priority. The
execution order of tools with equal priority is unspecified. For example, you
could use the 'sort' and 'head' utilities to keep only the 10 smallest numbers
in a text file by ensuring that 'sort' runs before 'head'::

  [fix]
  sort:command = sort -n
  head:command = head -n 10
  sort:pattern = numbers.txt
  head:pattern = numbers.txt
  sort:priority = 2
  head:priority = 1

To account for changes made by each tool, the line numbers used for incremental
formatting are recomputed before executing the next tool. So, each tool may see
different values for the arguments added by the :linerange suboption.

Each fixer tool is allowed to return some metadata in addition to the fixed file
content. The metadata must be placed before the file content on stdout,
separated from the file content by a zero byte. The metadata is parsed as a JSON
value (so, it should be UTF-8 encoded and contain no zero bytes). A fixer tool
is expected to produce this metadata encoding if and only if the :metadata
suboption is true::

  [fix]
  tool:command = tool --prepend-json-metadata
  tool:metadata = true

The metadata values are passed to hooks, which can be used to print summaries or
perform other post-fixing work. The supported hooks are::

  "postfixfile"
    Run once for each file in each revision where any fixer tools made changes
    to the file content. Provides "$HG_REV" and "$HG_PATH" to identify the file,
    and "$HG_METADATA" with a map of fixer names to metadata values from fixer
    tools that affected the file. Fixer tools that didn't affect the file have a
    value of None. Only fixer tools that executed are present in the metadata.

  "postfix"
    Run once after all files and revisions have been handled. Provides
    "$HG_REPLACEMENTS" with information about what revisions were created and
    made obsolete. Provides a boolean "$HG_WDIRWRITTEN" to indicate whether any
    files in the working copy were updated. Provides a list "$HG_METADATA"
    mapping fixer tool names to lists of metadata values returned from
    executions that modified a file. This aggregates the same metadata
    previously passed to the "postfixfile" hook.
"""


import collections
import itertools
import os
import re
import subprocess

from mercurial.i18n import _
from mercurial.node import (
    nullid,
    nullrev,
    wdirrev,
)

from mercurial.utils import procutil

from mercurial import (
    cmdutil,
    context,
    copies,
    error,
    logcmdutil,
    match as matchmod,
    mdiff,
    merge,
    mergestate as mergestatemod,
    pycompat,
    registrar,
    rewriteutil,
    scmutil,
    util,
    worker,
)

# Note for extension authors: ONLY specify testedwith = 'ships-with-hg-core' for
# extensions which SHIP WITH MERCURIAL. Non-mainline extensions should
# be specifying the version(s) of Mercurial they are tested with, or
# leave the attribute unspecified.
testedwith = b'ships-with-hg-core'

cmdtable = {}
command = registrar.command(cmdtable)

configtable = {}
configitem = registrar.configitem(configtable)

# Register the suboptions allowed for each configured fixer, and default values.
FIXER_ATTRS = {
    b'command': None,
    b'linerange': None,
    b'pattern': None,
    b'priority': 0,
    b'metadata': False,
    b'skipclean': True,
    b'enabled': True,
}

for key, default in FIXER_ATTRS.items():
    configitem(b'fix', b'.*:%s$' % key, default=default, generic=True)

# A good default size allows most source code files to be fixed, but avoids
# letting fixer tools choke on huge inputs, which could be surprising to the
# user.
configitem(b'fix', b'maxfilesize', default=b'2MB')

# Allow fix commands to exit non-zero if an executed fixer tool exits non-zero.
# This helps users do shell scripts that stop when a fixer tool signals a
# problem.
configitem(b'fix', b'failure', default=b'continue')


def checktoolfailureaction(ui, message, hint=None):
    """Abort with 'message' if fix.failure=abort"""
    action = ui.config(b'fix', b'failure')
    if action not in (b'continue', b'abort'):
        raise error.Abort(
            _(b'unknown fix.failure action: %s') % (action,),
            hint=_(b'use "continue" or "abort"'),
        )
    if action == b'abort':
        raise error.Abort(message, hint=hint)


allopt = (b'', b'all', False, _(b'fix all non-public non-obsolete revisions'))
baseopt = (
    b'',
    b'base',
    [],
    _(
        b'revisions to diff against (overrides automatic '
        b'selection, and applies to every revision being '
        b'fixed)'
    ),
    _(b'REV'),
)
revopt = (b'r', b'rev', [], _(b'revisions to fix (ADVANCED)'), _(b'REV'))
sourceopt = (
    b's',
    b'source',
    [],
    _(b'fix the specified revisions and their descendants'),
    _(b'REV'),
)
wdiropt = (b'w', b'working-dir', False, _(b'fix the working directory'))
wholeopt = (b'', b'whole', False, _(b'always fix every line of a file'))
usage = _(b'[OPTION]... [FILE]...')


@command(
    b'fix',
    [allopt, baseopt, revopt, sourceopt, wdiropt, wholeopt],
    usage,
    helpcategory=command.CATEGORY_FILE_CONTENTS,
)
def fix(ui, repo, *pats, **opts):
    """rewrite file content in changesets or working directory

    Runs any configured tools to fix the content of files. (See
    :hg:`help -e fix` for details about configuring tools.) Only affects files
    with changes, unless file arguments are provided. Only affects changed lines
    of files, unless the --whole flag is used. Some tools may always affect the
    whole file regardless of --whole.

    If --working-dir is used, files with uncommitted changes in the working copy
    will be fixed. Note that no backup are made.

    If revisions are specified with --source, those revisions and their
    descendants will be checked, and they may be replaced with new revisions
    that have fixed file content. By automatically including the descendants,
    no merging, rebasing, or evolution will be required. If an ancestor of the
    working copy is included, then the working copy itself will also be fixed,
    and the working copy will be updated to the fixed parent.

    When determining what lines of each file to fix at each revision, the whole
    set of revisions being fixed is considered, so that fixes to earlier
    revisions are not forgotten in later ones. The --base flag can be used to
    override this default behavior, though it is not usually desirable to do so.
    """
    opts = pycompat.byteskwargs(opts)
    cmdutil.check_at_most_one_arg(opts, b'all', b'source', b'rev')
    cmdutil.check_incompatible_arguments(
        opts, b'working_dir', [b'all', b'source']
    )

    with repo.wlock(), repo.lock(), repo.transaction(b'fix'):
        revstofix = getrevstofix(ui, repo, opts)
        basectxs = getbasectxs(repo, opts, revstofix)
        workqueue, numitems = getworkqueue(
            ui, repo, pats, opts, revstofix, basectxs
        )
        basepaths = getbasepaths(repo, opts, workqueue, basectxs)
        fixers = getfixers(ui)

        # Rather than letting each worker independently fetch the files
        # (which also would add complications for shared/keepalive
        # connections), prefetch them all first.
        _prefetchfiles(repo, workqueue, basepaths)

        # There are no data dependencies between the workers fixing each file
        # revision, so we can use all available parallelism.
        def getfixes(items):
            for srcrev, path, dstrevs in items:
                ctx = repo[srcrev]
                olddata = ctx[path].data()
                metadata, newdata = fixfile(
                    ui,
                    repo,
                    opts,
                    fixers,
                    ctx,
                    path,
                    basepaths,
                    basectxs[srcrev],
                )
                # We ungroup the work items now, because the code that consumes
                # these results has to handle each dstrev separately, and in
                # topological order. Because these are handled in topological
                # order, it's important that we pass around references to
                # "newdata" instead of copying it. Otherwise, we would be
                # keeping more copies of file content in memory at a time than
                # if we hadn't bothered to group/deduplicate the work items.
                data = newdata if newdata != olddata else None
                for dstrev in dstrevs:
                    yield (dstrev, path, metadata, data)

        results = worker.worker(
            ui, 1.0, getfixes, tuple(), workqueue, threadsafe=False
        )

        # We have to hold on to the data for each successor revision in memory
        # until all its parents are committed. We ensure this by committing and
        # freeing memory for the revisions in some topological order. This
        # leaves a little bit of memory efficiency on the table, but also makes
        # the tests deterministic. It might also be considered a feature since
        # it makes the results more easily reproducible.
        filedata = collections.defaultdict(dict)
        aggregatemetadata = collections.defaultdict(list)
        replacements = {}
        wdirwritten = False
        commitorder = sorted(revstofix, reverse=True)
        with ui.makeprogress(
            topic=_(b'fixing'), unit=_(b'files'), total=sum(numitems.values())
        ) as progress:
            for rev, path, filerevmetadata, newdata in results:
                progress.increment(item=path)
                for fixername, fixermetadata in filerevmetadata.items():
                    aggregatemetadata[fixername].append(fixermetadata)
                if newdata is not None:
                    filedata[rev][path] = newdata
                    hookargs = {
                        b'rev': rev,
                        b'path': path,
                        b'metadata': filerevmetadata,
                    }
                    repo.hook(
                        b'postfixfile',
                        throw=False,
                        **pycompat.strkwargs(hookargs)
                    )
                numitems[rev] -= 1
                # Apply the fixes for this and any other revisions that are
                # ready and sitting at the front of the queue. Using a loop here
                # prevents the queue from being blocked by the first revision to
                # be ready out of order.
                while commitorder and not numitems[commitorder[-1]]:
                    rev = commitorder.pop()
                    ctx = repo[rev]
                    if rev == wdirrev:
                        writeworkingdir(repo, ctx, filedata[rev], replacements)
                        wdirwritten = bool(filedata[rev])
                    else:
                        replacerev(ui, repo, ctx, filedata[rev], replacements)
                    del filedata[rev]

        cleanup(repo, replacements, wdirwritten)
        hookargs = {
            b'replacements': replacements,
            b'wdirwritten': wdirwritten,
            b'metadata': aggregatemetadata,
        }
        repo.hook(b'postfix', throw=True, **pycompat.strkwargs(hookargs))


def cleanup(repo, replacements, wdirwritten):
    """Calls scmutil.cleanupnodes() with the given replacements.

    "replacements" is a dict from nodeid to nodeid, with one key and one value
    for every revision that was affected by fixing. This is slightly different
    from cleanupnodes().

    "wdirwritten" is a bool which tells whether the working copy was affected by
    fixing, since it has no entry in "replacements".

    Useful as a hook point for extending "hg fix" with output summarizing the
    effects of the command, though we choose not to output anything here.
    """
    replacements = {prec: [succ] for prec, succ in replacements.items()}
    scmutil.cleanupnodes(repo, replacements, b'fix', fixphase=True)


def getworkqueue(ui, repo, pats, opts, revstofix, basectxs):
    """Constructs a list of files to fix and which revisions each fix applies to

    To avoid duplicating work, there is usually only one work item for each file
    revision that might need to be fixed. There can be multiple work items per
    file revision if the same file needs to be fixed in multiple changesets with
    different baserevs. Each work item also contains a list of changesets where
    the file's data should be replaced with the fixed data. The work items for
    earlier changesets come earlier in the work queue, to improve pipelining by
    allowing the first changeset to be replaced while fixes are still being
    computed for later changesets.

    Also returned is a map from changesets to the count of work items that might
    affect each changeset. This is used later to count when all of a changeset's
    work items have been finished, without having to inspect the remaining work
    queue in each worker subprocess.

    The example work item (1, "foo/bar.txt", (1, 2, 3)) means that the data of
    bar.txt should be read from revision 1, then fixed, and written back to
    revisions 1, 2 and 3. Revision 1 is called the "srcrev" and the list of
    revisions is called the "dstrevs". In practice the srcrev is always one of
    the dstrevs, and we make that choice when constructing the work item so that
    the choice can't be made inconsistently later on. The dstrevs should all
    have the same file revision for the given path, so the choice of srcrev is
    arbitrary. The wdirrev can be a dstrev and a srcrev.
    """
    dstrevmap = collections.defaultdict(list)
    numitems = collections.defaultdict(int)
    maxfilesize = ui.configbytes(b'fix', b'maxfilesize')
    for rev in sorted(revstofix):
        fixctx = repo[rev]
        match = scmutil.match(fixctx, pats, opts)
        for path in sorted(
            pathstofix(ui, repo, pats, opts, match, basectxs[rev], fixctx)
        ):
            fctx = fixctx[path]
            if fctx.islink():
                continue
            if fctx.size() > maxfilesize:
                ui.warn(
                    _(b'ignoring file larger than %s: %s\n')
                    % (util.bytecount(maxfilesize), path)
                )
                continue
            baserevs = tuple(ctx.rev() for ctx in basectxs[rev])
            dstrevmap[(fctx.filerev(), baserevs, path)].append(rev)
            numitems[rev] += 1
    workqueue = [
        (min(dstrevs), path, dstrevs)
        for (_filerev, _baserevs, path), dstrevs in dstrevmap.items()
    ]
    # Move work items for earlier changesets to the front of the queue, so we
    # might be able to replace those changesets (in topological order) while
    # we're still processing later work items. Note the min() in the previous
    # expression, which means we don't need a custom comparator here. The path
    # is also important in the sort order to make the output order stable. There
    # are some situations where this doesn't help much, but some situations
    # where it lets us buffer O(1) files instead of O(n) files.
    workqueue.sort()
    return workqueue, numitems


def getrevstofix(ui, repo, opts):
    """Returns the set of revision numbers that should be fixed"""
    if opts[b'all']:
        revs = repo.revs(b'(not public() and not obsolete()) or wdir()')
    elif opts[b'source']:
        source_revs = logcmdutil.revrange(repo, opts[b'source'])
        revs = set(repo.revs(b'(%ld::) - obsolete()', source_revs))
        if wdirrev in source_revs:
            # `wdir()::` is currently empty, so manually add wdir
            revs.add(wdirrev)
        if repo[b'.'].rev() in revs:
            revs.add(wdirrev)
    else:
        revs = set(logcmdutil.revrange(repo, opts[b'rev']))
        if opts.get(b'working_dir'):
            revs.add(wdirrev)
    # Allow fixing only wdir() even if there's an unfinished operation
    if not (len(revs) == 1 and wdirrev in revs):
        cmdutil.checkunfinished(repo)
        rewriteutil.precheck(repo, revs, b'fix')
    if (
        wdirrev in revs
        and mergestatemod.mergestate.read(repo).unresolvedcount()
    ):
        raise error.Abort(b'unresolved conflicts', hint=b"use 'hg resolve'")
    if not revs:
        raise error.Abort(
            b'no changesets specified', hint=b'use --source or --working-dir'
        )
    return revs


def pathstofix(ui, repo, pats, opts, match, basectxs, fixctx):
    """Returns the set of files that should be fixed in a context

    The result depends on the base contexts; we include any file that has
    changed relative to any of the base contexts. Base contexts should be
    ancestors of the context being fixed.
    """
    files = set()
    for basectx in basectxs:
        stat = basectx.status(
            fixctx, match=match, listclean=bool(pats), listunknown=bool(pats)
        )
        files.update(
            set(
                itertools.chain(
                    stat.added, stat.modified, stat.clean, stat.unknown
                )
            )
        )
    return files


def lineranges(opts, path, basepaths, basectxs, fixctx, content2):
    """Returns the set of line ranges that should be fixed in a file

    Of the form [(10, 20), (30, 40)].

    This depends on the given base contexts; we must consider lines that have
    changed versus any of the base contexts, and whether the file has been
    renamed versus any of them.

    Another way to understand this is that we exclude line ranges that are
    common to the file in all base contexts.
    """
    if opts.get(b'whole'):
        # Return a range containing all lines. Rely on the diff implementation's
        # idea of how many lines are in the file, instead of reimplementing it.
        return difflineranges(b'', content2)

    rangeslist = []
    for basectx in basectxs:
        basepath = basepaths.get((basectx.rev(), fixctx.rev(), path), path)

        if basepath in basectx:
            content1 = basectx[basepath].data()
        else:
            content1 = b''
        rangeslist.extend(difflineranges(content1, content2))
    return unionranges(rangeslist)


def getbasepaths(repo, opts, workqueue, basectxs):
    if opts.get(b'whole'):
        # Base paths will never be fetched for line range determination.
        return {}

    basepaths = {}
    for srcrev, path, _dstrevs in workqueue:
        fixctx = repo[srcrev]
        for basectx in basectxs[srcrev]:
            basepath = copies.pathcopies(basectx, fixctx).get(path, path)
            if basepath in basectx:
                basepaths[(basectx.rev(), fixctx.rev(), path)] = basepath
    return basepaths


def unionranges(rangeslist):
    """Return the union of some closed intervals

    >>> unionranges([])
    []
    >>> unionranges([(1, 100)])
    [(1, 100)]
    >>> unionranges([(1, 100), (1, 100)])
    [(1, 100)]
    >>> unionranges([(1, 100), (2, 100)])
    [(1, 100)]
    >>> unionranges([(1, 99), (1, 100)])
    [(1, 100)]
    >>> unionranges([(1, 100), (40, 60)])
    [(1, 100)]
    >>> unionranges([(1, 49), (50, 100)])
    [(1, 100)]
    >>> unionranges([(1, 48), (50, 100)])
    [(1, 48), (50, 100)]
    >>> unionranges([(1, 2), (3, 4), (5, 6)])
    [(1, 6)]
    """
    rangeslist = sorted(set(rangeslist))
    unioned = []
    if rangeslist:
        unioned, rangeslist = [rangeslist[0]], rangeslist[1:]
    for a, b in rangeslist:
        c, d = unioned[-1]
        if a > d + 1:
            unioned.append((a, b))
        else:
            unioned[-1] = (c, max(b, d))
    return unioned


def difflineranges(content1, content2):
    """Return list of line number ranges in content2 that differ from content1.

    Line numbers are 1-based. The numbers are the first and last line contained
    in the range. Single-line ranges have the same line number for the first and
    last line. Excludes any empty ranges that result from lines that are only
    present in content1. Relies on mdiff's idea of where the line endings are in
    the string.

    >>> from mercurial import pycompat
    >>> lines = lambda s: b'\\n'.join([c for c in pycompat.iterbytestr(s)])
    >>> difflineranges2 = lambda a, b: difflineranges(lines(a), lines(b))
    >>> difflineranges2(b'', b'')
    []
    >>> difflineranges2(b'a', b'')
    []
    >>> difflineranges2(b'', b'A')
    [(1, 1)]
    >>> difflineranges2(b'a', b'a')
    []
    >>> difflineranges2(b'a', b'A')
    [(1, 1)]
    >>> difflineranges2(b'ab', b'')
    []
    >>> difflineranges2(b'', b'AB')
    [(1, 2)]
    >>> difflineranges2(b'abc', b'ac')
    []
    >>> difflineranges2(b'ab', b'aCb')
    [(2, 2)]
    >>> difflineranges2(b'abc', b'aBc')
    [(2, 2)]
    >>> difflineranges2(b'ab', b'AB')
    [(1, 2)]
    >>> difflineranges2(b'abcde', b'aBcDe')
    [(2, 2), (4, 4)]
    >>> difflineranges2(b'abcde', b'aBCDe')
    [(2, 4)]
    """
    ranges = []
    for lines, kind in mdiff.allblocks(content1, content2):
        firstline, lastline = lines[2:4]
        if kind == b'!' and firstline != lastline:
            ranges.append((firstline + 1, lastline))
    return ranges


def getbasectxs(repo, opts, revstofix):
    """Returns a map of the base contexts for each revision

    The base contexts determine which lines are considered modified when we
    attempt to fix just the modified lines in a file. It also determines which
    files we attempt to fix, so it is important to compute this even when
    --whole is used.
    """
    # The --base flag overrides the usual logic, and we give every revision
    # exactly the set of baserevs that the user specified.
    if opts.get(b'base'):
        baserevs = set(logcmdutil.revrange(repo, opts.get(b'base')))
        if not baserevs:
            baserevs = {nullrev}
        basectxs = {repo[rev] for rev in baserevs}
        return {rev: basectxs for rev in revstofix}

    # Proceed in topological order so that we can easily determine each
    # revision's baserevs by looking at its parents and their baserevs.
    basectxs = collections.defaultdict(set)
    for rev in sorted(revstofix):
        ctx = repo[rev]
        for pctx in ctx.parents():
            if pctx.rev() in basectxs:
                basectxs[rev].update(basectxs[pctx.rev()])
            else:
                basectxs[rev].add(pctx)
    return basectxs


def _prefetchfiles(repo, workqueue, basepaths):
    toprefetch = set()

    # Prefetch the files that will be fixed.
    for srcrev, path, _dstrevs in workqueue:
        if srcrev == wdirrev:
            continue
        toprefetch.add((srcrev, path))

    # Prefetch the base contents for lineranges().
    for (baserev, fixrev, path), basepath in basepaths.items():
        toprefetch.add((baserev, basepath))

    if toprefetch:
        scmutil.prefetchfiles(
            repo,
            [
                (rev, scmutil.matchfiles(repo, [path]))
                for rev, path in toprefetch
            ],
        )


def fixfile(ui, repo, opts, fixers, fixctx, path, basepaths, basectxs):
    """Run any configured fixers that should affect the file in this context

    Returns the file content that results from applying the fixers in some order
    starting with the file's content in the fixctx. Fixers that support line
    ranges will affect lines that have changed relative to any of the basectxs
    (i.e. they will only avoid lines that are common to all basectxs).

    A fixer tool's stdout will become the file's new content if and only if it
    exits with code zero. The fixer tool's working directory is the repository's
    root.
    """
    metadata = {}
    newdata = fixctx[path].data()
    for fixername, fixer in fixers.items():
        if fixer.affects(opts, fixctx, path):
            ranges = lineranges(
                opts, path, basepaths, basectxs, fixctx, newdata
            )
            command = fixer.command(ui, path, ranges)
            if command is None:
                continue
            msg = b'fixing: %s - %s - %s\n'
            msg %= (fixctx, fixername, path)
            ui.debug(msg)
            ui.debug(b'subprocess: %s\n' % (command,))
            proc = subprocess.Popen(
                procutil.tonativestr(command),
                shell=True,
                cwd=procutil.tonativestr(repo.root),
                stdin=subprocess.PIPE,
                stdout=subprocess.PIPE,
                stderr=subprocess.PIPE,
            )
            stdout, stderr = proc.communicate(newdata)
            if stderr:
                showstderr(ui, fixctx.rev(), fixername, stderr)
            newerdata = stdout
            if fixer.shouldoutputmetadata():
                try:
                    metadatajson, newerdata = stdout.split(b'\0', 1)
                    metadata[fixername] = pycompat.json_loads(metadatajson)
                except ValueError:
                    ui.warn(
                        _(b'ignored invalid output from fixer tool: %s\n')
                        % (fixername,)
                    )
                    continue
            else:
                metadata[fixername] = None
            if proc.returncode == 0:
                newdata = newerdata
            else:
                if not stderr:
                    message = _(b'exited with status %d\n') % (proc.returncode,)
                    showstderr(ui, fixctx.rev(), fixername, message)
                checktoolfailureaction(
                    ui,
                    _(b'no fixes will be applied'),
                    hint=_(
                        b'use --config fix.failure=continue to apply any '
                        b'successful fixes anyway'
                    ),
                )
    return metadata, newdata


def showstderr(ui, rev, fixername, stderr):
    """Writes the lines of the stderr string as warnings on the ui

    Uses the revision number and fixername to give more context to each line of
    the error message. Doesn't include file names, since those take up a lot of
    space and would tend to be included in the error message if they were
    relevant.
    """
    for line in re.split(b'[\r\n]+', stderr):
        if line:
            ui.warn(b'[')
            if rev is None:
                ui.warn(_(b'wdir'), label=b'evolve.rev')
            else:
                ui.warn(b'%d' % rev, label=b'evolve.rev')
            ui.warn(b'] %s: %s\n' % (fixername, line))


def writeworkingdir(repo, ctx, filedata, replacements):
    """Write new content to the working copy and check out the new p1 if any

    We check out a new revision if and only if we fixed something in both the
    working directory and its parent revision. This avoids the need for a full
    update/merge, and means that the working directory simply isn't affected
    unless the --working-dir flag is given.

    Directly updates the dirstate for the affected files.
    """
    for path, data in filedata.items():
        fctx = ctx[path]
        fctx.write(data, fctx.flags())

    oldp1 = repo.dirstate.p1()
    newp1 = replacements.get(oldp1, oldp1)
    if newp1 != oldp1:
        assert repo.dirstate.p2() == nullid
        with repo.dirstate.changing_parents(repo):
            scmutil.movedirstate(repo, repo[newp1])


def replacerev(ui, repo, ctx, filedata, replacements):
    """Commit a new revision like the given one, but with file content changes

    "ctx" is the original revision to be replaced by a modified one.

    "filedata" is a dict that maps paths to their new file content. All other
    paths will be recreated from the original revision without changes.
    "filedata" may contain paths that didn't exist in the original revision;
    they will be added.

    "replacements" is a dict that maps a single node to a single node, and it is
    updated to indicate the original revision is replaced by the newly created
    one. No entry is added if the replacement's node already exists.

    The new revision has the same parents as the old one, unless those parents
    have already been replaced, in which case those replacements are the parents
    of this new revision. Thus, if revisions are replaced in topological order,
    there is no need to rebase them into the original topology later.
    """

    p1rev, p2rev = repo.changelog.parentrevs(ctx.rev())
    p1ctx, p2ctx = repo[p1rev], repo[p2rev]
    newp1node = replacements.get(p1ctx.node(), p1ctx.node())
    newp2node = replacements.get(p2ctx.node(), p2ctx.node())

    # We don't want to create a revision that has no changes from the original,
    # but we should if the original revision's parent has been replaced.
    # Otherwise, we would produce an orphan that needs no actual human
    # intervention to evolve. We can't rely on commit() to avoid creating the
    # un-needed revision because the extra field added below produces a new hash
    # regardless of file content changes.
    if (
        not filedata
        and p1ctx.node() not in replacements
        and p2ctx.node() not in replacements
    ):
        return

    extra = ctx.extra().copy()
    extra[b'fix_source'] = ctx.hex()

    wctx = context.overlayworkingctx(repo)
    wctx.setbase(repo[newp1node])
    merge.revert_to(ctx, wc=wctx)
    copies.graftcopies(wctx, ctx, ctx.p1())

    for path in filedata.keys():
        fctx = ctx[path]
        copysource = fctx.copysource()
        wctx.write(path, filedata[path], flags=fctx.flags())
        if copysource:
            wctx.markcopied(path, copysource)

    desc = rewriteutil.update_hash_refs(
        repo,
        ctx.description(),
        {oldnode: [newnode] for oldnode, newnode in replacements.items()},
    )

    memctx = wctx.tomemctx(
        text=desc,
        branch=ctx.branch(),
        extra=extra,
        date=ctx.date(),
        parents=(newp1node, newp2node),
        user=ctx.user(),
    )

    sucnode = memctx.commit()
    prenode = ctx.node()
    if prenode == sucnode:
        ui.debug(b'node %s already existed\n' % (ctx.hex()))
    else:
        replacements[ctx.node()] = sucnode


def getfixers(ui):
    """Returns a map of configured fixer tools indexed by their names

    Each value is a Fixer object with methods that implement the behavior of the
    fixer's config suboptions. Does not validate the config values.
    """
    fixers = {}
    for name in fixernames(ui):
        enabled = ui.configbool(b'fix', name + b':enabled')
        command = ui.config(b'fix', name + b':command')
        pattern = ui.config(b'fix', name + b':pattern')
        linerange = ui.config(b'fix', name + b':linerange')
        priority = ui.configint(b'fix', name + b':priority')
        metadata = ui.configbool(b'fix', name + b':metadata')
        skipclean = ui.configbool(b'fix', name + b':skipclean')
        # Don't use a fixer if it has no pattern configured. It would be
        # dangerous to let it affect all files. It would be pointless to let it
        # affect no files. There is no reasonable subset of files to use as the
        # default.
        if command is None:
            ui.warn(
                _(b'fixer tool has no command configuration: %s\n') % (name,)
            )
        elif pattern is None:
            ui.warn(
                _(b'fixer tool has no pattern configuration: %s\n') % (name,)
            )
        elif not enabled:
            ui.debug(b'ignoring disabled fixer tool: %s\n' % (name,))
        else:
            fixers[name] = Fixer(
                command, pattern, linerange, priority, metadata, skipclean
            )
    return collections.OrderedDict(
        sorted(fixers.items(), key=lambda item: item[1]._priority, reverse=True)
    )


def fixernames(ui):
    """Returns the names of [fix] config options that have suboptions"""
    names = set()
    for k, v in ui.configitems(b'fix'):
        if b':' in k:
            names.add(k.split(b':', 1)[0])
    return names


class Fixer:
    """Wraps the raw config values for a fixer with methods"""

    def __init__(
        self, command, pattern, linerange, priority, metadata, skipclean
    ):
        self._command = command
        self._pattern = pattern
        self._linerange = linerange
        self._priority = priority
        self._metadata = metadata
        self._skipclean = skipclean

    def affects(self, opts, fixctx, path):
        """Should this fixer run on the file at the given path and context?"""
        repo = fixctx.repo()
        matcher = matchmod.match(
            repo.root, repo.root, [self._pattern], ctx=fixctx
        )
        return matcher(path)

    def shouldoutputmetadata(self):
        """Should the stdout of this fixer start with JSON and a null byte?"""
        return self._metadata

    def command(self, ui, path, ranges):
        """A shell command to use to invoke this fixer on the given file/lines

        May return None if there is no appropriate command to run for the given
        parameters.
        """
        expand = cmdutil.rendercommandtemplate
        parts = [
            expand(
                ui,
                self._command,
                {b'rootpath': path, b'basename': os.path.basename(path)},
            )
        ]
        if self._linerange:
            if self._skipclean and not ranges:
                # No line ranges to fix, so don't run the fixer.
                return None
            for first, last in ranges:
                parts.append(
                    expand(
                        ui, self._linerange, {b'first': first, b'last': last}
                    )
                )
        return b' '.join(parts)
