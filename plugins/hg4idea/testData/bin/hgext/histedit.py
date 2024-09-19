# histedit.py - interactive history editing for mercurial
#
# Copyright 2009 Augie Fackler <raf@durin42.com>
#
# This software may be used and distributed according to the terms of the
# GNU General Public License version 2 or any later version.
"""interactive history editing

With this extension installed, Mercurial gains one new command: histedit. Usage
is as follows, assuming the following history::

 @  3[tip]   7c2fd3b9020c   2009-04-27 18:04 -0500   durin42
 |    Add delta
 |
 o  2   030b686bedc4   2009-04-27 18:04 -0500   durin42
 |    Add gamma
 |
 o  1   c561b4e977df   2009-04-27 18:04 -0500   durin42
 |    Add beta
 |
 o  0   d8d2fcd0e319   2009-04-27 18:04 -0500   durin42
      Add alpha

If you were to run ``hg histedit c561b4e977df``, you would see the following
file open in your editor::

 pick c561b4e977df Add beta
 pick 030b686bedc4 Add gamma
 pick 7c2fd3b9020c Add delta

 # Edit history between c561b4e977df and 7c2fd3b9020c
 #
 # Commits are listed from least to most recent
 #
 # Commands:
 #  p, pick = use commit
 #  e, edit = use commit, but allow edits before making new commit
 #  f, fold = use commit, but combine it with the one above
 #  r, roll = like fold, but discard this commit's description and date
 #  d, drop = remove commit from history
 #  m, mess = edit commit message without changing commit content
 #  b, base = checkout changeset and apply further changesets from there
 #

In this file, lines beginning with ``#`` are ignored. You must specify a rule
for each revision in your history. For example, if you had meant to add gamma
before beta, and then wanted to add delta in the same revision as beta, you
would reorganize the file to look like this::

 pick 030b686bedc4 Add gamma
 pick c561b4e977df Add beta
 fold 7c2fd3b9020c Add delta

 # Edit history between c561b4e977df and 7c2fd3b9020c
 #
 # Commits are listed from least to most recent
 #
 # Commands:
 #  p, pick = use commit
 #  e, edit = use commit, but allow edits before making new commit
 #  f, fold = use commit, but combine it with the one above
 #  r, roll = like fold, but discard this commit's description and date
 #  d, drop = remove commit from history
 #  m, mess = edit commit message without changing commit content
 #  b, base = checkout changeset and apply further changesets from there
 #

At which point you close the editor and ``histedit`` starts working. When you
specify a ``fold`` operation, ``histedit`` will open an editor when it folds
those revisions together, offering you a chance to clean up the commit message::

 Add beta
 ***
 Add delta

Edit the commit message to your liking, then close the editor. The date used
for the commit will be the later of the two commits' dates. For this example,
let's assume that the commit message was changed to ``Add beta and delta.``
After histedit has run and had a chance to remove any old or temporary
revisions it needed, the history looks like this::

 @  2[tip]   989b4d060121   2009-04-27 18:04 -0500   durin42
 |    Add beta and delta.
 |
 o  1   081603921c3f   2009-04-27 18:04 -0500   durin42
 |    Add gamma
 |
 o  0   d8d2fcd0e319   2009-04-27 18:04 -0500   durin42
      Add alpha

Note that ``histedit`` does *not* remove any revisions (even its own temporary
ones) until after it has completed all the editing operations, so it will
probably perform several strip operations when it's done. For the above example,
it had to run strip twice. Strip can be slow depending on a variety of factors,
so you might need to be a little patient. You can choose to keep the original
revisions by passing the ``--keep`` flag.

The ``edit`` operation will drop you back to a command prompt,
allowing you to edit files freely, or even use ``hg record`` to commit
some changes as a separate commit. When you're done, any remaining
uncommitted changes will be committed as well. When done, run ``hg
histedit --continue`` to finish this step. If there are uncommitted
changes, you'll be prompted for a new commit message, but the default
commit message will be the original message for the ``edit`` ed
revision, and the date of the original commit will be preserved.

The ``message`` operation will give you a chance to revise a commit
message without changing the contents. It's a shortcut for doing
``edit`` immediately followed by `hg histedit --continue``.

If ``histedit`` encounters a conflict when moving a revision (while
handling ``pick`` or ``fold``), it'll stop in a similar manner to
``edit`` with the difference that it won't prompt you for a commit
message when done. If you decide at this point that you don't like how
much work it will be to rearrange history, or that you made a mistake,
you can use ``hg histedit --abort`` to abandon the new changes you
have made and return to the state before you attempted to edit your
history.

If we clone the histedit-ed example repository above and add four more
changes, such that we have the following history::

   @  6[tip]   038383181893   2009-04-27 18:04 -0500   stefan
   |    Add theta
   |
   o  5   140988835471   2009-04-27 18:04 -0500   stefan
   |    Add eta
   |
   o  4   122930637314   2009-04-27 18:04 -0500   stefan
   |    Add zeta
   |
   o  3   836302820282   2009-04-27 18:04 -0500   stefan
   |    Add epsilon
   |
   o  2   989b4d060121   2009-04-27 18:04 -0500   durin42
   |    Add beta and delta.
   |
   o  1   081603921c3f   2009-04-27 18:04 -0500   durin42
   |    Add gamma
   |
   o  0   d8d2fcd0e319   2009-04-27 18:04 -0500   durin42
        Add alpha

If you run ``hg histedit --outgoing`` on the clone then it is the same
as running ``hg histedit 836302820282``. If you need plan to push to a
repository that Mercurial does not detect to be related to the source
repo, you can add a ``--force`` option.

Config
------

Histedit rule lines are truncated to 80 characters by default. You
can customize this behavior by setting a different length in your
configuration file::

  [histedit]
  linelen = 120      # truncate rule lines at 120 characters

The summary of a change can be customized as well::

  [histedit]
  summary-template = '{rev} {bookmarks} {desc|firstline}'

The customized summary should be kept short enough that rule lines
will fit in the configured line length. See above if that requires
customization.

``hg histedit`` attempts to automatically choose an appropriate base
revision to use. To change which base revision is used, define a
revset in your configuration file::

  [histedit]
  defaultrev = only(.) & draft()

By default each edited revision needs to be present in histedit commands.
To remove revision you need to use ``drop`` operation. You can configure
the drop to be implicit for missing commits by adding::

  [histedit]
  dropmissing = True

By default, histedit will close the transaction after each action. For
performance purposes, you can configure histedit to use a single transaction
across the entire histedit. WARNING: This setting introduces a significant risk
of losing the work you've done in a histedit if the histedit aborts
unexpectedly::

  [histedit]
  singletransaction = True

"""

from __future__ import absolute_import

# chistedit dependencies that are not available everywhere
try:
    import fcntl
    import termios
except ImportError:
    fcntl = None
    termios = None

import functools
import os
import struct

from mercurial.i18n import _
from mercurial.pycompat import (
    getattr,
    open,
)
from mercurial.node import (
    bin,
    hex,
    short,
)
from mercurial import (
    bundle2,
    cmdutil,
    context,
    copies,
    destutil,
    discovery,
    encoding,
    error,
    exchange,
    extensions,
    hg,
    logcmdutil,
    merge as mergemod,
    mergestate as mergestatemod,
    mergeutil,
    obsolete,
    pycompat,
    registrar,
    repair,
    rewriteutil,
    scmutil,
    state as statemod,
    util,
)
from mercurial.utils import (
    dateutil,
    stringutil,
    urlutil,
)

pickle = util.pickle
cmdtable = {}
command = registrar.command(cmdtable)

configtable = {}
configitem = registrar.configitem(configtable)
configitem(
    b'experimental',
    b'histedit.autoverb',
    default=False,
)
configitem(
    b'histedit',
    b'defaultrev',
    default=None,
)
configitem(
    b'histedit',
    b'dropmissing',
    default=False,
)
configitem(
    b'histedit',
    b'linelen',
    default=80,
)
configitem(
    b'histedit',
    b'singletransaction',
    default=False,
)
configitem(
    b'ui',
    b'interface.histedit',
    default=None,
)
configitem(b'histedit', b'summary-template', default=b'{rev} {desc|firstline}')

# Note for extension authors: ONLY specify testedwith = 'ships-with-hg-core' for
# extensions which SHIP WITH MERCURIAL. Non-mainline extensions should
# be specifying the version(s) of Mercurial they are tested with, or
# leave the attribute unspecified.
testedwith = b'ships-with-hg-core'

actiontable = {}
primaryactions = set()
secondaryactions = set()
tertiaryactions = set()
internalactions = set()


def geteditcomment(ui, first, last):
    """construct the editor comment
    The comment includes::
     - an intro
     - sorted primary commands
     - sorted short commands
     - sorted long commands
     - additional hints

    Commands are only included once.
    """
    intro = _(
        b"""Edit history between %s and %s

Commits are listed from least to most recent

You can reorder changesets by reordering the lines

Commands:
"""
    )
    actions = []

    def addverb(v):
        a = actiontable[v]
        lines = a.message.split(b"\n")
        if len(a.verbs):
            v = b', '.join(sorted(a.verbs, key=lambda v: len(v)))
        actions.append(b" %s = %s" % (v, lines[0]))
        actions.extend([b'  %s'] * (len(lines) - 1))

    for v in (
        sorted(primaryactions)
        + sorted(secondaryactions)
        + sorted(tertiaryactions)
    ):
        addverb(v)
    actions.append(b'')

    hints = []
    if ui.configbool(b'histedit', b'dropmissing'):
        hints.append(
            b"Deleting a changeset from the list "
            b"will DISCARD it from the edited history!"
        )

    lines = (intro % (first, last)).split(b'\n') + actions + hints

    return b''.join([b'# %s\n' % l if l else b'#\n' for l in lines])


class histeditstate(object):
    def __init__(self, repo):
        self.repo = repo
        self.actions = None
        self.keep = None
        self.topmost = None
        self.parentctxnode = None
        self.lock = None
        self.wlock = None
        self.backupfile = None
        self.stateobj = statemod.cmdstate(repo, b'histedit-state')
        self.replacements = []

    def read(self):
        """Load histedit state from disk and set fields appropriately."""
        if not self.stateobj.exists():
            cmdutil.wrongtooltocontinue(self.repo, _(b'histedit'))

        data = self._read()

        self.parentctxnode = data[b'parentctxnode']
        actions = parserules(data[b'rules'], self)
        self.actions = actions
        self.keep = data[b'keep']
        self.topmost = data[b'topmost']
        self.replacements = data[b'replacements']
        self.backupfile = data[b'backupfile']

    def _read(self):
        fp = self.repo.vfs.read(b'histedit-state')
        if fp.startswith(b'v1\n'):
            data = self._load()
            parentctxnode, rules, keep, topmost, replacements, backupfile = data
        else:
            data = pickle.loads(fp)
            parentctxnode, rules, keep, topmost, replacements = data
            backupfile = None
        rules = b"\n".join([b"%s %s" % (verb, rest) for [verb, rest] in rules])

        return {
            b'parentctxnode': parentctxnode,
            b"rules": rules,
            b"keep": keep,
            b"topmost": topmost,
            b"replacements": replacements,
            b"backupfile": backupfile,
        }

    def write(self, tr=None):
        if tr:
            tr.addfilegenerator(
                b'histedit-state',
                (b'histedit-state',),
                self._write,
                location=b'plain',
            )
        else:
            with self.repo.vfs(b"histedit-state", b"w") as f:
                self._write(f)

    def _write(self, fp):
        fp.write(b'v1\n')
        fp.write(b'%s\n' % hex(self.parentctxnode))
        fp.write(b'%s\n' % hex(self.topmost))
        fp.write(b'%s\n' % (b'True' if self.keep else b'False'))
        fp.write(b'%d\n' % len(self.actions))
        for action in self.actions:
            fp.write(b'%s\n' % action.tostate())
        fp.write(b'%d\n' % len(self.replacements))
        for replacement in self.replacements:
            fp.write(
                b'%s%s\n'
                % (
                    hex(replacement[0]),
                    b''.join(hex(r) for r in replacement[1]),
                )
            )
        backupfile = self.backupfile
        if not backupfile:
            backupfile = b''
        fp.write(b'%s\n' % backupfile)

    def _load(self):
        fp = self.repo.vfs(b'histedit-state', b'r')
        lines = [l[:-1] for l in fp.readlines()]

        index = 0
        lines[index]  # version number
        index += 1

        parentctxnode = bin(lines[index])
        index += 1

        topmost = bin(lines[index])
        index += 1

        keep = lines[index] == b'True'
        index += 1

        # Rules
        rules = []
        rulelen = int(lines[index])
        index += 1
        for i in pycompat.xrange(rulelen):
            ruleaction = lines[index]
            index += 1
            rule = lines[index]
            index += 1
            rules.append((ruleaction, rule))

        # Replacements
        replacements = []
        replacementlen = int(lines[index])
        index += 1
        for i in pycompat.xrange(replacementlen):
            replacement = lines[index]
            original = bin(replacement[:40])
            succ = [
                bin(replacement[i : i + 40])
                for i in range(40, len(replacement), 40)
            ]
            replacements.append((original, succ))
            index += 1

        backupfile = lines[index]
        index += 1

        fp.close()

        return parentctxnode, rules, keep, topmost, replacements, backupfile

    def clear(self):
        if self.inprogress():
            self.repo.vfs.unlink(b'histedit-state')

    def inprogress(self):
        return self.repo.vfs.exists(b'histedit-state')


class histeditaction(object):
    def __init__(self, state, node):
        self.state = state
        self.repo = state.repo
        self.node = node

    @classmethod
    def fromrule(cls, state, rule):
        """Parses the given rule, returning an instance of the histeditaction."""
        ruleid = rule.strip().split(b' ', 1)[0]
        # ruleid can be anything from rev numbers, hashes, "bookmarks" etc
        # Check for validation of rule ids and get the rulehash
        try:
            rev = bin(ruleid)
        except TypeError:
            try:
                _ctx = scmutil.revsingle(state.repo, ruleid)
                rulehash = _ctx.hex()
                rev = bin(rulehash)
            except error.RepoLookupError:
                raise error.ParseError(_(b"invalid changeset %s") % ruleid)
        return cls(state, rev)

    def verify(self, prev, expected, seen):
        """Verifies semantic correctness of the rule"""
        repo = self.repo
        ha = hex(self.node)
        self.node = scmutil.resolvehexnodeidprefix(repo, ha)
        if self.node is None:
            raise error.ParseError(_(b'unknown changeset %s listed') % ha[:12])
        self._verifynodeconstraints(prev, expected, seen)

    def _verifynodeconstraints(self, prev, expected, seen):
        # by default command need a node in the edited list
        if self.node not in expected:
            raise error.ParseError(
                _(b'%s "%s" changeset was not a candidate')
                % (self.verb, short(self.node)),
                hint=_(b'only use listed changesets'),
            )
        # and only one command per node
        if self.node in seen:
            raise error.ParseError(
                _(b'duplicated command for changeset %s') % short(self.node)
            )

    def torule(self):
        """build a histedit rule line for an action

        by default lines are in the form:
        <hash> <rev> <summary>
        """
        ctx = self.repo[self.node]
        ui = self.repo.ui
        # We don't want color codes in the commit message template, so
        # disable the label() template function while we render it.
        with ui.configoverride(
            {(b'templatealias', b'label(l,x)'): b"x"}, b'histedit'
        ):
            summary = cmdutil.rendertemplate(
                ctx, ui.config(b'histedit', b'summary-template')
            )
        # Handle the fact that `''.splitlines() => []`
        summary = summary.splitlines()[0] if summary else b''
        line = b'%s %s %s' % (self.verb, ctx, summary)
        # trim to 75 columns by default so it's not stupidly wide in my editor
        # (the 5 more are left for verb)
        maxlen = self.repo.ui.configint(b'histedit', b'linelen')
        maxlen = max(maxlen, 22)  # avoid truncating hash
        return stringutil.ellipsis(line, maxlen)

    def tostate(self):
        """Print an action in format used by histedit state files
        (the first line is a verb, the remainder is the second)
        """
        return b"%s\n%s" % (self.verb, hex(self.node))

    def run(self):
        """Runs the action. The default behavior is simply apply the action's
        rulectx onto the current parentctx."""
        self.applychange()
        self.continuedirty()
        return self.continueclean()

    def applychange(self):
        """Applies the changes from this action's rulectx onto the current
        parentctx, but does not commit them."""
        repo = self.repo
        rulectx = repo[self.node]
        with repo.ui.silent():
            hg.update(repo, self.state.parentctxnode, quietempty=True)
        stats = applychanges(repo.ui, repo, rulectx, {})
        repo.dirstate.setbranch(rulectx.branch())
        if stats.unresolvedcount:
            raise error.InterventionRequired(
                _(b'Fix up the change (%s %s)') % (self.verb, short(self.node)),
                hint=_(b'hg histedit --continue to resume'),
            )

    def continuedirty(self):
        """Continues the action when changes have been applied to the working
        copy. The default behavior is to commit the dirty changes."""
        repo = self.repo
        rulectx = repo[self.node]

        editor = self.commiteditor()
        commit = commitfuncfor(repo, rulectx)
        if repo.ui.configbool(b'rewrite', b'update-timestamp'):
            date = dateutil.makedate()
        else:
            date = rulectx.date()
        commit(
            text=rulectx.description(),
            user=rulectx.user(),
            date=date,
            extra=rulectx.extra(),
            editor=editor,
        )

    def commiteditor(self):
        """The editor to be used to edit the commit message."""
        return False

    def continueclean(self):
        """Continues the action when the working copy is clean. The default
        behavior is to accept the current commit as the new version of the
        rulectx."""
        ctx = self.repo[b'.']
        if ctx.node() == self.state.parentctxnode:
            self.repo.ui.warn(
                _(b'%s: skipping changeset (no changes)\n') % short(self.node)
            )
            return ctx, [(self.node, tuple())]
        if ctx.node() == self.node:
            # Nothing changed
            return ctx, []
        return ctx, [(self.node, (ctx.node(),))]


def commitfuncfor(repo, src):
    """Build a commit function for the replacement of <src>

    This function ensure we apply the same treatment to all changesets.

    - Add a 'histedit_source' entry in extra.

    Note that fold has its own separated logic because its handling is a bit
    different and not easily factored out of the fold method.
    """
    phasemin = src.phase()

    def commitfunc(**kwargs):
        overrides = {(b'phases', b'new-commit'): phasemin}
        with repo.ui.configoverride(overrides, b'histedit'):
            extra = kwargs.get('extra', {}).copy()
            extra[b'histedit_source'] = src.hex()
            kwargs['extra'] = extra
            return repo.commit(**kwargs)

    return commitfunc


def applychanges(ui, repo, ctx, opts):
    """Merge changeset from ctx (only) in the current working directory"""
    if ctx.p1().node() == repo.dirstate.p1():
        # edits are "in place" we do not need to make any merge,
        # just applies changes on parent for editing
        with ui.silent():
            cmdutil.revert(ui, repo, ctx, all=True)
            stats = mergemod.updateresult(0, 0, 0, 0)
    else:
        try:
            # ui.forcemerge is an internal variable, do not document
            repo.ui.setconfig(
                b'ui', b'forcemerge', opts.get(b'tool', b''), b'histedit'
            )
            stats = mergemod.graft(repo, ctx, labels=[b'local', b'histedit'])
        finally:
            repo.ui.setconfig(b'ui', b'forcemerge', b'', b'histedit')
    return stats


def collapse(repo, firstctx, lastctx, commitopts, skipprompt=False):
    """collapse the set of revisions from first to last as new one.

    Expected commit options are:
        - message
        - date
        - username
    Commit message is edited in all cases.

    This function works in memory."""
    ctxs = list(repo.set(b'%d::%d', firstctx.rev(), lastctx.rev()))
    if not ctxs:
        return None
    for c in ctxs:
        if not c.mutable():
            raise error.ParseError(
                _(b"cannot fold into public change %s") % short(c.node())
            )
    base = firstctx.p1()

    # commit a new version of the old changeset, including the update
    # collect all files which might be affected
    files = set()
    for ctx in ctxs:
        files.update(ctx.files())

    # Recompute copies (avoid recording a -> b -> a)
    copied = copies.pathcopies(base, lastctx)

    # prune files which were reverted by the updates
    files = [f for f in files if not cmdutil.samefile(f, lastctx, base)]
    # commit version of these files as defined by head
    headmf = lastctx.manifest()

    def filectxfn(repo, ctx, path):
        if path in headmf:
            fctx = lastctx[path]
            flags = fctx.flags()
            mctx = context.memfilectx(
                repo,
                ctx,
                fctx.path(),
                fctx.data(),
                islink=b'l' in flags,
                isexec=b'x' in flags,
                copysource=copied.get(path),
            )
            return mctx
        return None

    if commitopts.get(b'message'):
        message = commitopts[b'message']
    else:
        message = firstctx.description()
    user = commitopts.get(b'user')
    date = commitopts.get(b'date')
    extra = commitopts.get(b'extra')

    parents = (firstctx.p1().node(), firstctx.p2().node())
    editor = None
    if not skipprompt:
        editor = cmdutil.getcommiteditor(edit=True, editform=b'histedit.fold')
    new = context.memctx(
        repo,
        parents=parents,
        text=message,
        files=files,
        filectxfn=filectxfn,
        user=user,
        date=date,
        extra=extra,
        editor=editor,
    )
    return repo.commitctx(new)


def _isdirtywc(repo):
    return repo[None].dirty(missing=True)


def abortdirty():
    raise error.Abort(
        _(b'working copy has pending changes'),
        hint=_(
            b'amend, commit, or revert them and run histedit '
            b'--continue, or abort with histedit --abort'
        ),
    )


def action(verbs, message, priority=False, internal=False):
    def wrap(cls):
        assert not priority or not internal
        verb = verbs[0]
        if priority:
            primaryactions.add(verb)
        elif internal:
            internalactions.add(verb)
        elif len(verbs) > 1:
            secondaryactions.add(verb)
        else:
            tertiaryactions.add(verb)

        cls.verb = verb
        cls.verbs = verbs
        cls.message = message
        for verb in verbs:
            actiontable[verb] = cls
        return cls

    return wrap


@action([b'pick', b'p'], _(b'use commit'), priority=True)
class pick(histeditaction):
    def run(self):
        rulectx = self.repo[self.node]
        if rulectx.p1().node() == self.state.parentctxnode:
            self.repo.ui.debug(b'node %s unchanged\n' % short(self.node))
            return rulectx, []

        return super(pick, self).run()


@action(
    [b'edit', b'e'],
    _(b'use commit, but allow edits before making new commit'),
    priority=True,
)
class edit(histeditaction):
    def run(self):
        repo = self.repo
        rulectx = repo[self.node]
        hg.update(repo, self.state.parentctxnode, quietempty=True)
        applychanges(repo.ui, repo, rulectx, {})
        hint = _(b'to edit %s, `hg histedit --continue` after making changes')
        raise error.InterventionRequired(
            _(b'Editing (%s), commit as needed now to split the change')
            % short(self.node),
            hint=hint % short(self.node),
        )

    def commiteditor(self):
        return cmdutil.getcommiteditor(edit=True, editform=b'histedit.edit')


@action([b'fold', b'f'], _(b'use commit, but combine it with the one above'))
class fold(histeditaction):
    def verify(self, prev, expected, seen):
        """Verifies semantic correctness of the fold rule"""
        super(fold, self).verify(prev, expected, seen)
        repo = self.repo
        if not prev:
            c = repo[self.node].p1()
        elif not prev.verb in (b'pick', b'base'):
            return
        else:
            c = repo[prev.node]
        if not c.mutable():
            raise error.ParseError(
                _(b"cannot fold into public change %s") % short(c.node())
            )

    def continuedirty(self):
        repo = self.repo
        rulectx = repo[self.node]

        commit = commitfuncfor(repo, rulectx)
        commit(
            text=b'fold-temp-revision %s' % short(self.node),
            user=rulectx.user(),
            date=rulectx.date(),
            extra=rulectx.extra(),
        )

    def continueclean(self):
        repo = self.repo
        ctx = repo[b'.']
        rulectx = repo[self.node]
        parentctxnode = self.state.parentctxnode
        if ctx.node() == parentctxnode:
            repo.ui.warn(_(b'%s: empty changeset\n') % short(self.node))
            return ctx, [(self.node, (parentctxnode,))]

        parentctx = repo[parentctxnode]
        newcommits = {
            c.node()
            for c in repo.set(b'(%d::. - %d)', parentctx.rev(), parentctx.rev())
        }
        if not newcommits:
            repo.ui.warn(
                _(
                    b'%s: cannot fold - working copy is not a '
                    b'descendant of previous commit %s\n'
                )
                % (short(self.node), short(parentctxnode))
            )
            return ctx, [(self.node, (ctx.node(),))]

        middlecommits = newcommits.copy()
        middlecommits.discard(ctx.node())

        return self.finishfold(
            repo.ui, repo, parentctx, rulectx, ctx.node(), middlecommits
        )

    def skipprompt(self):
        """Returns true if the rule should skip the message editor.

        For example, 'fold' wants to show an editor, but 'rollup'
        doesn't want to.
        """
        return False

    def mergedescs(self):
        """Returns true if the rule should merge messages of multiple changes.

        This exists mainly so that 'rollup' rules can be a subclass of
        'fold'.
        """
        return True

    def firstdate(self):
        """Returns true if the rule should preserve the date of the first
        change.

        This exists mainly so that 'rollup' rules can be a subclass of
        'fold'.
        """
        return False

    def finishfold(self, ui, repo, ctx, oldctx, newnode, internalchanges):
        mergemod.update(ctx.p1())
        ### prepare new commit data
        commitopts = {}
        commitopts[b'user'] = ctx.user()
        # commit message
        if not self.mergedescs():
            newmessage = ctx.description()
        else:
            newmessage = (
                b'\n***\n'.join(
                    [ctx.description()]
                    + [repo[r].description() for r in internalchanges]
                    + [oldctx.description()]
                )
                + b'\n'
            )
        commitopts[b'message'] = newmessage
        # date
        if self.firstdate():
            commitopts[b'date'] = ctx.date()
        else:
            commitopts[b'date'] = max(ctx.date(), oldctx.date())
        # if date is to be updated to current
        if ui.configbool(b'rewrite', b'update-timestamp'):
            commitopts[b'date'] = dateutil.makedate()

        extra = ctx.extra().copy()
        # histedit_source
        # note: ctx is likely a temporary commit but that the best we can do
        #       here. This is sufficient to solve issue3681 anyway.
        extra[b'histedit_source'] = b'%s,%s' % (ctx.hex(), oldctx.hex())
        commitopts[b'extra'] = extra
        phasemin = max(ctx.phase(), oldctx.phase())
        overrides = {(b'phases', b'new-commit'): phasemin}
        with repo.ui.configoverride(overrides, b'histedit'):
            n = collapse(
                repo,
                ctx,
                repo[newnode],
                commitopts,
                skipprompt=self.skipprompt(),
            )
        if n is None:
            return ctx, []
        mergemod.update(repo[n])
        replacements = [
            (oldctx.node(), (newnode,)),
            (ctx.node(), (n,)),
            (newnode, (n,)),
        ]
        for ich in internalchanges:
            replacements.append((ich, (n,)))
        return repo[n], replacements


@action(
    [b'base', b'b'],
    _(b'checkout changeset and apply further changesets from there'),
)
class base(histeditaction):
    def run(self):
        if self.repo[b'.'].node() != self.node:
            mergemod.clean_update(self.repo[self.node])
        return self.continueclean()

    def continuedirty(self):
        abortdirty()

    def continueclean(self):
        basectx = self.repo[b'.']
        return basectx, []

    def _verifynodeconstraints(self, prev, expected, seen):
        # base can only be use with a node not in the edited set
        if self.node in expected:
            msg = _(b'%s "%s" changeset was an edited list candidate')
            raise error.ParseError(
                msg % (self.verb, short(self.node)),
                hint=_(b'base must only use unlisted changesets'),
            )


@action(
    [b'_multifold'],
    _(
        """fold subclass used for when multiple folds happen in a row

    We only want to fire the editor for the folded message once when
    (say) four changes are folded down into a single change. This is
    similar to rollup, but we should preserve both messages so that
    when the last fold operation runs we can show the user all the
    commit messages in their editor.
    """
    ),
    internal=True,
)
class _multifold(fold):
    def skipprompt(self):
        return True


@action(
    [b"roll", b"r"],
    _(b"like fold, but discard this commit's description and date"),
)
class rollup(fold):
    def mergedescs(self):
        return False

    def skipprompt(self):
        return True

    def firstdate(self):
        return True


@action([b"drop", b"d"], _(b'remove commit from history'))
class drop(histeditaction):
    def run(self):
        parentctx = self.repo[self.state.parentctxnode]
        return parentctx, [(self.node, tuple())]


@action(
    [b"mess", b"m"],
    _(b'edit commit message without changing commit content'),
    priority=True,
)
class message(histeditaction):
    def commiteditor(self):
        return cmdutil.getcommiteditor(edit=True, editform=b'histedit.mess')


def findoutgoing(ui, repo, remote=None, force=False, opts=None):
    """utility function to find the first outgoing changeset

    Used by initialization code"""
    if opts is None:
        opts = {}
    path = urlutil.get_unique_push_path(b'histedit', repo, ui, remote)
    dest = path.pushloc or path.loc

    ui.status(_(b'comparing with %s\n') % urlutil.hidepassword(dest))

    revs, checkout = hg.addbranchrevs(repo, repo, (path.branch, []), None)
    other = hg.peer(repo, opts, dest)

    if revs:
        revs = [repo.lookup(rev) for rev in revs]

    outgoing = discovery.findcommonoutgoing(repo, other, revs, force=force)
    if not outgoing.missing:
        raise error.Abort(_(b'no outgoing ancestors'))
    roots = list(repo.revs(b"roots(%ln)", outgoing.missing))
    if len(roots) > 1:
        msg = _(b'there are ambiguous outgoing revisions')
        hint = _(b"see 'hg help histedit' for more detail")
        raise error.Abort(msg, hint=hint)
    return repo[roots[0]].node()


# Curses Support
try:
    import curses
except ImportError:
    curses = None

KEY_LIST = [b'pick', b'edit', b'fold', b'drop', b'mess', b'roll']
ACTION_LABELS = {
    b'fold': b'^fold',
    b'roll': b'^roll',
}

COLOR_HELP, COLOR_SELECTED, COLOR_OK, COLOR_WARN, COLOR_CURRENT = 1, 2, 3, 4, 5
COLOR_DIFF_ADD_LINE, COLOR_DIFF_DEL_LINE, COLOR_DIFF_OFFSET = 6, 7, 8
COLOR_ROLL, COLOR_ROLL_CURRENT, COLOR_ROLL_SELECTED = 9, 10, 11

E_QUIT, E_HISTEDIT = 1, 2
E_PAGEDOWN, E_PAGEUP, E_LINEUP, E_LINEDOWN, E_RESIZE = 3, 4, 5, 6, 7
MODE_INIT, MODE_PATCH, MODE_RULES, MODE_HELP = 0, 1, 2, 3

KEYTABLE = {
    b'global': {
        b'h': b'next-action',
        b'KEY_RIGHT': b'next-action',
        b'l': b'prev-action',
        b'KEY_LEFT': b'prev-action',
        b'q': b'quit',
        b'c': b'histedit',
        b'C': b'histedit',
        b'v': b'showpatch',
        b'?': b'help',
    },
    MODE_RULES: {
        b'd': b'action-drop',
        b'e': b'action-edit',
        b'f': b'action-fold',
        b'm': b'action-mess',
        b'p': b'action-pick',
        b'r': b'action-roll',
        b' ': b'select',
        b'j': b'down',
        b'k': b'up',
        b'KEY_DOWN': b'down',
        b'KEY_UP': b'up',
        b'J': b'move-down',
        b'K': b'move-up',
        b'KEY_NPAGE': b'move-down',
        b'KEY_PPAGE': b'move-up',
        b'0': b'goto',  # Used for 0..9
    },
    MODE_PATCH: {
        b' ': b'page-down',
        b'KEY_NPAGE': b'page-down',
        b'KEY_PPAGE': b'page-up',
        b'j': b'line-down',
        b'k': b'line-up',
        b'KEY_DOWN': b'line-down',
        b'KEY_UP': b'line-up',
        b'J': b'down',
        b'K': b'up',
    },
    MODE_HELP: {},
}


def screen_size():
    return struct.unpack(b'hh', fcntl.ioctl(1, termios.TIOCGWINSZ, b'    '))


class histeditrule(object):
    def __init__(self, ui, ctx, pos, action=b'pick'):
        self.ui = ui
        self.ctx = ctx
        self.action = action
        self.origpos = pos
        self.pos = pos
        self.conflicts = []

    def __bytes__(self):
        # Example display of several histeditrules:
        #
        #  #10 pick   316392:06a16c25c053   add option to skip tests
        #  #11 ^roll  316393:71313c964cc5   <RED>oops a fixup commit</RED>
        #  #12 pick   316394:ab31f3973b0d   include mfbt for mozilla-config.h
        #  #13 ^fold  316395:14ce5803f4c3   fix warnings
        #
        # The carets point to the changeset being folded into ("roll this
        # changeset into the changeset above").
        return b'%s%s' % (self.prefix, self.desc)

    __str__ = encoding.strmethod(__bytes__)

    @property
    def prefix(self):
        # Some actions ('fold' and 'roll') combine a patch with a
        # previous one. Add a marker showing which patch they apply
        # to.
        action = ACTION_LABELS.get(self.action, self.action)

        h = self.ctx.hex()[0:12]
        r = self.ctx.rev()

        return b"#%s %s %d:%s   " % (
            (b'%d' % self.origpos).ljust(2),
            action.ljust(6),
            r,
            h,
        )

    @util.propertycache
    def desc(self):
        summary = cmdutil.rendertemplate(
            self.ctx, self.ui.config(b'histedit', b'summary-template')
        )
        if summary:
            return summary
        # This is split off from the prefix property so that we can
        # separately make the description for 'roll' red (since it
        # will get discarded).
        return self.ctx.description().splitlines()[0].strip()

    def checkconflicts(self, other):
        if other.pos > self.pos and other.origpos <= self.origpos:
            if set(other.ctx.files()) & set(self.ctx.files()) != set():
                self.conflicts.append(other)
                return self.conflicts

        if other in self.conflicts:
            self.conflicts.remove(other)
        return self.conflicts


# ============ EVENTS ===============
def movecursor(state, oldpos, newpos):
    """Change the rule/changeset that the cursor is pointing to, regardless of
    current mode (you can switch between patches from the view patch window)."""
    state[b'pos'] = newpos

    mode, _ = state[b'mode']
    if mode == MODE_RULES:
        # Scroll through the list by updating the view for MODE_RULES, so that
        # even if we are not currently viewing the rules, switching back will
        # result in the cursor's rule being visible.
        modestate = state[b'modes'][MODE_RULES]
        if newpos < modestate[b'line_offset']:
            modestate[b'line_offset'] = newpos
        elif newpos > modestate[b'line_offset'] + state[b'page_height'] - 1:
            modestate[b'line_offset'] = newpos - state[b'page_height'] + 1

    # Reset the patch view region to the top of the new patch.
    state[b'modes'][MODE_PATCH][b'line_offset'] = 0


def changemode(state, mode):
    curmode, _ = state[b'mode']
    state[b'mode'] = (mode, curmode)
    if mode == MODE_PATCH:
        state[b'modes'][MODE_PATCH][b'patchcontents'] = patchcontents(state)


def makeselection(state, pos):
    state[b'selected'] = pos


def swap(state, oldpos, newpos):
    """Swap two positions and calculate necessary conflicts in
    O(|newpos-oldpos|) time"""

    rules = state[b'rules']
    assert 0 <= oldpos < len(rules) and 0 <= newpos < len(rules)

    rules[oldpos], rules[newpos] = rules[newpos], rules[oldpos]

    # TODO: swap should not know about histeditrule's internals
    rules[newpos].pos = newpos
    rules[oldpos].pos = oldpos

    start = min(oldpos, newpos)
    end = max(oldpos, newpos)
    for r in pycompat.xrange(start, end + 1):
        rules[newpos].checkconflicts(rules[r])
        rules[oldpos].checkconflicts(rules[r])

    if state[b'selected']:
        makeselection(state, newpos)


def changeaction(state, pos, action):
    """Change the action state on the given position to the new action"""
    rules = state[b'rules']
    assert 0 <= pos < len(rules)
    rules[pos].action = action


def cycleaction(state, pos, next=False):
    """Changes the action state the next or the previous action from
    the action list"""
    rules = state[b'rules']
    assert 0 <= pos < len(rules)
    current = rules[pos].action

    assert current in KEY_LIST

    index = KEY_LIST.index(current)
    if next:
        index += 1
    else:
        index -= 1
    changeaction(state, pos, KEY_LIST[index % len(KEY_LIST)])


def changeview(state, delta, unit):
    """Change the region of whatever is being viewed (a patch or the list of
    changesets). 'delta' is an amount (+/- 1) and 'unit' is 'page' or 'line'."""
    mode, _ = state[b'mode']
    if mode != MODE_PATCH:
        return
    mode_state = state[b'modes'][mode]
    num_lines = len(mode_state[b'patchcontents'])
    page_height = state[b'page_height']
    unit = page_height if unit == b'page' else 1
    num_pages = 1 + (num_lines - 1) // page_height
    max_offset = (num_pages - 1) * page_height
    newline = mode_state[b'line_offset'] + delta * unit
    mode_state[b'line_offset'] = max(0, min(max_offset, newline))


def event(state, ch):
    """Change state based on the current character input

    This takes the current state and based on the current character input from
    the user we change the state.
    """
    selected = state[b'selected']
    oldpos = state[b'pos']
    rules = state[b'rules']

    if ch in (curses.KEY_RESIZE, b"KEY_RESIZE"):
        return E_RESIZE

    lookup_ch = ch
    if ch is not None and b'0' <= ch <= b'9':
        lookup_ch = b'0'

    curmode, prevmode = state[b'mode']
    action = KEYTABLE[curmode].get(
        lookup_ch, KEYTABLE[b'global'].get(lookup_ch)
    )
    if action is None:
        return
    if action in (b'down', b'move-down'):
        newpos = min(oldpos + 1, len(rules) - 1)
        movecursor(state, oldpos, newpos)
        if selected is not None or action == b'move-down':
            swap(state, oldpos, newpos)
    elif action in (b'up', b'move-up'):
        newpos = max(0, oldpos - 1)
        movecursor(state, oldpos, newpos)
        if selected is not None or action == b'move-up':
            swap(state, oldpos, newpos)
    elif action == b'next-action':
        cycleaction(state, oldpos, next=True)
    elif action == b'prev-action':
        cycleaction(state, oldpos, next=False)
    elif action == b'select':
        selected = oldpos if selected is None else None
        makeselection(state, selected)
    elif action == b'goto' and int(ch) < len(rules) and len(rules) <= 10:
        newrule = next((r for r in rules if r.origpos == int(ch)))
        movecursor(state, oldpos, newrule.pos)
        if selected is not None:
            swap(state, oldpos, newrule.pos)
    elif action.startswith(b'action-'):
        changeaction(state, oldpos, action[7:])
    elif action == b'showpatch':
        changemode(state, MODE_PATCH if curmode != MODE_PATCH else prevmode)
    elif action == b'help':
        changemode(state, MODE_HELP if curmode != MODE_HELP else prevmode)
    elif action == b'quit':
        return E_QUIT
    elif action == b'histedit':
        return E_HISTEDIT
    elif action == b'page-down':
        return E_PAGEDOWN
    elif action == b'page-up':
        return E_PAGEUP
    elif action == b'line-down':
        return E_LINEDOWN
    elif action == b'line-up':
        return E_LINEUP


def makecommands(rules):
    """Returns a list of commands consumable by histedit --commands based on
    our list of rules"""
    commands = []
    for rules in rules:
        commands.append(b'%s %s\n' % (rules.action, rules.ctx))
    return commands


def addln(win, y, x, line, color=None):
    """Add a line to the given window left padding but 100% filled with
    whitespace characters, so that the color appears on the whole line"""
    maxy, maxx = win.getmaxyx()
    length = maxx - 1 - x
    line = bytes(line).ljust(length)[:length]
    if y < 0:
        y = maxy + y
    if x < 0:
        x = maxx + x
    if color:
        win.addstr(y, x, line, color)
    else:
        win.addstr(y, x, line)


def _trunc_head(line, n):
    if len(line) <= n:
        return line
    return b'> ' + line[-(n - 2) :]


def _trunc_tail(line, n):
    if len(line) <= n:
        return line
    return line[: n - 2] + b' >'


def patchcontents(state):
    repo = state[b'repo']
    rule = state[b'rules'][state[b'pos']]
    displayer = logcmdutil.changesetdisplayer(
        repo.ui, repo, {b"patch": True, b"template": b"status"}, buffered=True
    )
    overrides = {(b'ui', b'verbose'): True}
    with repo.ui.configoverride(overrides, source=b'histedit'):
        displayer.show(rule.ctx)
        displayer.close()
    return displayer.hunk[rule.ctx.rev()].splitlines()


def _chisteditmain(repo, rules, stdscr):
    try:
        curses.use_default_colors()
    except curses.error:
        pass

    # initialize color pattern
    curses.init_pair(COLOR_HELP, curses.COLOR_WHITE, curses.COLOR_BLUE)
    curses.init_pair(COLOR_SELECTED, curses.COLOR_BLACK, curses.COLOR_WHITE)
    curses.init_pair(COLOR_WARN, curses.COLOR_BLACK, curses.COLOR_YELLOW)
    curses.init_pair(COLOR_OK, curses.COLOR_BLACK, curses.COLOR_GREEN)
    curses.init_pair(COLOR_CURRENT, curses.COLOR_WHITE, curses.COLOR_MAGENTA)
    curses.init_pair(COLOR_DIFF_ADD_LINE, curses.COLOR_GREEN, -1)
    curses.init_pair(COLOR_DIFF_DEL_LINE, curses.COLOR_RED, -1)
    curses.init_pair(COLOR_DIFF_OFFSET, curses.COLOR_MAGENTA, -1)
    curses.init_pair(COLOR_ROLL, curses.COLOR_RED, -1)
    curses.init_pair(
        COLOR_ROLL_CURRENT, curses.COLOR_BLACK, curses.COLOR_MAGENTA
    )
    curses.init_pair(COLOR_ROLL_SELECTED, curses.COLOR_RED, curses.COLOR_WHITE)

    # don't display the cursor
    try:
        curses.curs_set(0)
    except curses.error:
        pass

    def rendercommit(win, state):
        """Renders the commit window that shows the log of the current selected
        commit"""
        pos = state[b'pos']
        rules = state[b'rules']
        rule = rules[pos]

        ctx = rule.ctx
        win.box()

        maxy, maxx = win.getmaxyx()
        length = maxx - 3

        line = b"changeset: %d:%s" % (ctx.rev(), ctx.hex()[:12])
        win.addstr(1, 1, line[:length])

        line = b"user:      %s" % ctx.user()
        win.addstr(2, 1, line[:length])

        bms = repo.nodebookmarks(ctx.node())
        line = b"bookmark:  %s" % b' '.join(bms)
        win.addstr(3, 1, line[:length])

        line = b"summary:   %s" % (ctx.description().splitlines()[0])
        win.addstr(4, 1, line[:length])

        line = b"files:     "
        win.addstr(5, 1, line)
        fnx = 1 + len(line)
        fnmaxx = length - fnx + 1
        y = 5
        fnmaxn = maxy - (1 + y) - 1
        files = ctx.files()
        for i, line1 in enumerate(files):
            if len(files) > fnmaxn and i == fnmaxn - 1:
                win.addstr(y, fnx, _trunc_tail(b','.join(files[i:]), fnmaxx))
                y = y + 1
                break
            win.addstr(y, fnx, _trunc_head(line1, fnmaxx))
            y = y + 1

        conflicts = rule.conflicts
        if len(conflicts) > 0:
            conflictstr = b','.join(map(lambda r: r.ctx.hex()[:12], conflicts))
            conflictstr = b"changed files overlap with %s" % conflictstr
        else:
            conflictstr = b'no overlap'

        win.addstr(y, 1, conflictstr[:length])
        win.noutrefresh()

    def helplines(mode):
        if mode == MODE_PATCH:
            help = b"""\
?: help, k/up: line up, j/down: line down, v: stop viewing patch
pgup: prev page, space/pgdn: next page, c: commit, q: abort
"""
        else:
            help = b"""\
?: help, k/up: move up, j/down: move down, space: select, v: view patch
d: drop, e: edit, f: fold, m: mess, p: pick, r: roll
pgup/K: move patch up, pgdn/J: move patch down, c: commit, q: abort
"""
        return help.splitlines()

    def renderhelp(win, state):
        maxy, maxx = win.getmaxyx()
        mode, _ = state[b'mode']
        for y, line in enumerate(helplines(mode)):
            if y >= maxy:
                break
            addln(win, y, 0, line, curses.color_pair(COLOR_HELP))
        win.noutrefresh()

    def renderrules(rulesscr, state):
        rules = state[b'rules']
        pos = state[b'pos']
        selected = state[b'selected']
        start = state[b'modes'][MODE_RULES][b'line_offset']

        conflicts = [r.ctx for r in rules if r.conflicts]
        if len(conflicts) > 0:
            line = b"potential conflict in %s" % b','.join(
                map(pycompat.bytestr, conflicts)
            )
            addln(rulesscr, -1, 0, line, curses.color_pair(COLOR_WARN))

        for y, rule in enumerate(rules[start:]):
            if y >= state[b'page_height']:
                break
            if len(rule.conflicts) > 0:
                rulesscr.addstr(y, 0, b" ", curses.color_pair(COLOR_WARN))
            else:
                rulesscr.addstr(y, 0, b" ", curses.COLOR_BLACK)

            if y + start == selected:
                rollcolor = COLOR_ROLL_SELECTED
                addln(rulesscr, y, 2, rule, curses.color_pair(COLOR_SELECTED))
            elif y + start == pos:
                rollcolor = COLOR_ROLL_CURRENT
                addln(
                    rulesscr,
                    y,
                    2,
                    rule,
                    curses.color_pair(COLOR_CURRENT) | curses.A_BOLD,
                )
            else:
                rollcolor = COLOR_ROLL
                addln(rulesscr, y, 2, rule)

            if rule.action == b'roll':
                rulesscr.addstr(
                    y,
                    2 + len(rule.prefix),
                    rule.desc,
                    curses.color_pair(rollcolor),
                )

        rulesscr.noutrefresh()

    def renderstring(win, state, output, diffcolors=False):
        maxy, maxx = win.getmaxyx()
        length = min(maxy - 1, len(output))
        for y in range(0, length):
            line = output[y]
            if diffcolors:
                if line and line[0] == b'+':
                    win.addstr(
                        y, 0, line, curses.color_pair(COLOR_DIFF_ADD_LINE)
                    )
                elif line and line[0] == b'-':
                    win.addstr(
                        y, 0, line, curses.color_pair(COLOR_DIFF_DEL_LINE)
                    )
                elif line.startswith(b'@@ '):
                    win.addstr(y, 0, line, curses.color_pair(COLOR_DIFF_OFFSET))
                else:
                    win.addstr(y, 0, line)
            else:
                win.addstr(y, 0, line)
        win.noutrefresh()

    def renderpatch(win, state):
        start = state[b'modes'][MODE_PATCH][b'line_offset']
        content = state[b'modes'][MODE_PATCH][b'patchcontents']
        renderstring(win, state, content[start:], diffcolors=True)

    def layout(mode):
        maxy, maxx = stdscr.getmaxyx()
        helplen = len(helplines(mode))
        mainlen = maxy - helplen - 12
        if mainlen < 1:
            raise error.Abort(
                _(b"terminal dimensions %d by %d too small for curses histedit")
                % (maxy, maxx),
                hint=_(
                    b"enlarge your terminal or use --config ui.interface=text"
                ),
            )
        return {
            b'commit': (12, maxx),
            b'help': (helplen, maxx),
            b'main': (mainlen, maxx),
        }

    def drawvertwin(size, y, x):
        win = curses.newwin(size[0], size[1], y, x)
        y += size[0]
        return win, y, x

    state = {
        b'pos': 0,
        b'rules': rules,
        b'selected': None,
        b'mode': (MODE_INIT, MODE_INIT),
        b'page_height': None,
        b'modes': {
            MODE_RULES: {
                b'line_offset': 0,
            },
            MODE_PATCH: {
                b'line_offset': 0,
            },
        },
        b'repo': repo,
    }

    # eventloop
    ch = None
    stdscr.clear()
    stdscr.refresh()
    while True:
        oldmode, unused = state[b'mode']
        if oldmode == MODE_INIT:
            changemode(state, MODE_RULES)
        e = event(state, ch)

        if e == E_QUIT:
            return False
        if e == E_HISTEDIT:
            return state[b'rules']
        else:
            if e == E_RESIZE:
                size = screen_size()
                if size != stdscr.getmaxyx():
                    curses.resizeterm(*size)

            curmode, unused = state[b'mode']
            sizes = layout(curmode)
            if curmode != oldmode:
                state[b'page_height'] = sizes[b'main'][0]
                # Adjust the view to fit the current screen size.
                movecursor(state, state[b'pos'], state[b'pos'])

            # Pack the windows against the top, each pane spread across the
            # full width of the screen.
            y, x = (0, 0)
            helpwin, y, x = drawvertwin(sizes[b'help'], y, x)
            mainwin, y, x = drawvertwin(sizes[b'main'], y, x)
            commitwin, y, x = drawvertwin(sizes[b'commit'], y, x)

            if e in (E_PAGEDOWN, E_PAGEUP, E_LINEDOWN, E_LINEUP):
                if e == E_PAGEDOWN:
                    changeview(state, +1, b'page')
                elif e == E_PAGEUP:
                    changeview(state, -1, b'page')
                elif e == E_LINEDOWN:
                    changeview(state, +1, b'line')
                elif e == E_LINEUP:
                    changeview(state, -1, b'line')

            # start rendering
            commitwin.erase()
            helpwin.erase()
            mainwin.erase()
            if curmode == MODE_PATCH:
                renderpatch(mainwin, state)
            elif curmode == MODE_HELP:
                renderstring(mainwin, state, __doc__.strip().splitlines())
            else:
                renderrules(mainwin, state)
                rendercommit(commitwin, state)
            renderhelp(helpwin, state)
            curses.doupdate()
            # done rendering
            ch = encoding.strtolocal(stdscr.getkey())


def _chistedit(ui, repo, freeargs, opts):
    """interactively edit changeset history via a curses interface

    Provides a ncurses interface to histedit. Press ? in chistedit mode
    to see an extensive help. Requires python-curses to be installed."""

    if curses is None:
        raise error.Abort(_(b"Python curses library required"))

    # disable color
    ui._colormode = None

    try:
        keep = opts.get(b'keep')
        revs = opts.get(b'rev', [])[:]
        cmdutil.checkunfinished(repo)
        cmdutil.bailifchanged(repo)

        if os.path.exists(os.path.join(repo.path, b'histedit-state')):
            raise error.Abort(
                _(
                    b'history edit already in progress, try '
                    b'--continue or --abort'
                )
            )
        revs.extend(freeargs)
        if not revs:
            defaultrev = destutil.desthistedit(ui, repo)
            if defaultrev is not None:
                revs.append(defaultrev)
        if len(revs) != 1:
            raise error.Abort(
                _(b'histedit requires exactly one ancestor revision')
            )

        rr = list(repo.set(b'roots(%ld)', scmutil.revrange(repo, revs)))
        if len(rr) != 1:
            raise error.Abort(
                _(
                    b'The specified revisions must have '
                    b'exactly one common root'
                )
            )
        root = rr[0].node()

        topmost = repo.dirstate.p1()
        revs = between(repo, root, topmost, keep)
        if not revs:
            raise error.Abort(
                _(b'%s is not an ancestor of working directory') % short(root)
            )

        ctxs = []
        for i, r in enumerate(revs):
            ctxs.append(histeditrule(ui, repo[r], i))
        with util.with_lc_ctype():
            rc = curses.wrapper(functools.partial(_chisteditmain, repo, ctxs))
        curses.echo()
        curses.endwin()
        if rc is False:
            ui.write(_(b"histedit aborted\n"))
            return 0
        if type(rc) is list:
            ui.status(_(b"performing changes\n"))
            rules = makecommands(rc)
            with repo.vfs(b'chistedit', b'w+') as fp:
                for r in rules:
                    fp.write(r)
                opts[b'commands'] = fp.name
            return _texthistedit(ui, repo, freeargs, opts)
    except KeyboardInterrupt:
        pass
    return -1


@command(
    b'histedit',
    [
        (
            b'',
            b'commands',
            b'',
            _(b'read history edits from the specified file'),
            _(b'FILE'),
        ),
        (b'c', b'continue', False, _(b'continue an edit already in progress')),
        (b'', b'edit-plan', False, _(b'edit remaining actions list')),
        (
            b'k',
            b'keep',
            False,
            _(b"don't strip old nodes after edit is complete"),
        ),
        (b'', b'abort', False, _(b'abort an edit in progress')),
        (b'o', b'outgoing', False, _(b'changesets not found in destination')),
        (
            b'f',
            b'force',
            False,
            _(b'force outgoing even for unrelated repositories'),
        ),
        (b'r', b'rev', [], _(b'first revision to be edited'), _(b'REV')),
    ]
    + cmdutil.formatteropts,
    _(b"[OPTIONS] ([ANCESTOR] | --outgoing [URL])"),
    helpcategory=command.CATEGORY_CHANGE_MANAGEMENT,
)
def histedit(ui, repo, *freeargs, **opts):
    """interactively edit changeset history

    This command lets you edit a linear series of changesets (up to
    and including the working directory, which should be clean).
    You can:

    - `pick` to [re]order a changeset

    - `drop` to omit changeset

    - `mess` to reword the changeset commit message

    - `fold` to combine it with the preceding changeset (using the later date)

    - `roll` like fold, but discarding this commit's description and date

    - `edit` to edit this changeset (preserving date)

    - `base` to checkout changeset and apply further changesets from there

    There are a number of ways to select the root changeset:

    - Specify ANCESTOR directly

    - Use --outgoing -- it will be the first linear changeset not
      included in destination. (See :hg:`help config.paths.default-push`)

    - Otherwise, the value from the "histedit.defaultrev" config option
      is used as a revset to select the base revision when ANCESTOR is not
      specified. The first revision returned by the revset is used. By
      default, this selects the editable history that is unique to the
      ancestry of the working directory.

    .. container:: verbose

       If you use --outgoing, this command will abort if there are ambiguous
       outgoing revisions. For example, if there are multiple branches
       containing outgoing revisions.

       Use "min(outgoing() and ::.)" or similar revset specification
       instead of --outgoing to specify edit target revision exactly in
       such ambiguous situation. See :hg:`help revsets` for detail about
       selecting revisions.

    .. container:: verbose

       Examples:

         - A number of changes have been made.
           Revision 3 is no longer needed.

           Start history editing from revision 3::

             hg histedit -r 3

           An editor opens, containing the list of revisions,
           with specific actions specified::

             pick 5339bf82f0ca 3 Zworgle the foobar
             pick 8ef592ce7cc4 4 Bedazzle the zerlog
             pick 0a9639fcda9d 5 Morgify the cromulancy

           Additional information about the possible actions
           to take appears below the list of revisions.

           To remove revision 3 from the history,
           its action (at the beginning of the relevant line)
           is changed to 'drop'::

             drop 5339bf82f0ca 3 Zworgle the foobar
             pick 8ef592ce7cc4 4 Bedazzle the zerlog
             pick 0a9639fcda9d 5 Morgify the cromulancy

         - A number of changes have been made.
           Revision 2 and 4 need to be swapped.

           Start history editing from revision 2::

             hg histedit -r 2

           An editor opens, containing the list of revisions,
           with specific actions specified::

             pick 252a1af424ad 2 Blorb a morgwazzle
             pick 5339bf82f0ca 3 Zworgle the foobar
             pick 8ef592ce7cc4 4 Bedazzle the zerlog

           To swap revision 2 and 4, its lines are swapped
           in the editor::

             pick 8ef592ce7cc4 4 Bedazzle the zerlog
             pick 5339bf82f0ca 3 Zworgle the foobar
             pick 252a1af424ad 2 Blorb a morgwazzle

    Returns 0 on success, 1 if user intervention is required (not only
    for intentional "edit" command, but also for resolving unexpected
    conflicts).
    """
    opts = pycompat.byteskwargs(opts)

    # kludge: _chistedit only works for starting an edit, not aborting
    # or continuing, so fall back to regular _texthistedit for those
    # operations.
    if ui.interface(b'histedit') == b'curses' and _getgoal(opts) == goalnew:
        return _chistedit(ui, repo, freeargs, opts)
    return _texthistedit(ui, repo, freeargs, opts)


def _texthistedit(ui, repo, freeargs, opts):
    state = histeditstate(repo)
    with repo.wlock() as wlock, repo.lock() as lock:
        state.wlock = wlock
        state.lock = lock
        _histedit(ui, repo, state, freeargs, opts)


goalcontinue = b'continue'
goalabort = b'abort'
goaleditplan = b'edit-plan'
goalnew = b'new'


def _getgoal(opts):
    if opts.get(b'continue'):
        return goalcontinue
    if opts.get(b'abort'):
        return goalabort
    if opts.get(b'edit_plan'):
        return goaleditplan
    return goalnew


def _readfile(ui, path):
    if path == b'-':
        with ui.timeblockedsection(b'histedit'):
            return ui.fin.read()
    else:
        with open(path, b'rb') as f:
            return f.read()


def _validateargs(ui, repo, state, freeargs, opts, goal, rules, revs):
    # TODO only abort if we try to histedit mq patches, not just
    # blanket if mq patches are applied somewhere
    mq = getattr(repo, 'mq', None)
    if mq and mq.applied:
        raise error.Abort(_(b'source has mq patches applied'))

    # basic argument incompatibility processing
    outg = opts.get(b'outgoing')
    editplan = opts.get(b'edit_plan')
    abort = opts.get(b'abort')
    force = opts.get(b'force')
    if force and not outg:
        raise error.Abort(_(b'--force only allowed with --outgoing'))
    if goal == b'continue':
        if any((outg, abort, revs, freeargs, rules, editplan)):
            raise error.Abort(_(b'no arguments allowed with --continue'))
    elif goal == b'abort':
        if any((outg, revs, freeargs, rules, editplan)):
            raise error.Abort(_(b'no arguments allowed with --abort'))
    elif goal == b'edit-plan':
        if any((outg, revs, freeargs)):
            raise error.Abort(
                _(b'only --commands argument allowed with --edit-plan')
            )
    else:
        if state.inprogress():
            raise error.Abort(
                _(
                    b'history edit already in progress, try '
                    b'--continue or --abort'
                )
            )
        if outg:
            if revs:
                raise error.Abort(_(b'no revisions allowed with --outgoing'))
            if len(freeargs) > 1:
                raise error.Abort(
                    _(b'only one repo argument allowed with --outgoing')
                )
        else:
            revs.extend(freeargs)
            if len(revs) == 0:
                defaultrev = destutil.desthistedit(ui, repo)
                if defaultrev is not None:
                    revs.append(defaultrev)

            if len(revs) != 1:
                raise error.Abort(
                    _(b'histedit requires exactly one ancestor revision')
                )


def _histedit(ui, repo, state, freeargs, opts):
    fm = ui.formatter(b'histedit', opts)
    fm.startitem()
    goal = _getgoal(opts)
    revs = opts.get(b'rev', [])
    nobackup = not ui.configbool(b'rewrite', b'backup-bundle')
    rules = opts.get(b'commands', b'')
    state.keep = opts.get(b'keep', False)

    _validateargs(ui, repo, state, freeargs, opts, goal, rules, revs)

    hastags = False
    if revs:
        revs = scmutil.revrange(repo, revs)
        ctxs = [repo[rev] for rev in revs]
        for ctx in ctxs:
            tags = [tag for tag in ctx.tags() if tag != b'tip']
            if not hastags:
                hastags = len(tags)
    if hastags:
        if ui.promptchoice(
            _(
                b'warning: tags associated with the given'
                b' changeset will be lost after histedit.\n'
                b'do you want to continue (yN)? $$ &Yes $$ &No'
            ),
            default=1,
        ):
            raise error.Abort(_(b'histedit cancelled\n'))
    # rebuild state
    if goal == goalcontinue:
        state.read()
        state = bootstrapcontinue(ui, state, opts)
    elif goal == goaleditplan:
        _edithisteditplan(ui, repo, state, rules)
        return
    elif goal == goalabort:
        _aborthistedit(ui, repo, state, nobackup=nobackup)
        return
    else:
        # goal == goalnew
        _newhistedit(ui, repo, state, revs, freeargs, opts)

    _continuehistedit(ui, repo, state)
    _finishhistedit(ui, repo, state, fm)
    fm.end()


def _continuehistedit(ui, repo, state):
    """This function runs after either:
    - bootstrapcontinue (if the goal is 'continue')
    - _newhistedit (if the goal is 'new')
    """
    # preprocess rules so that we can hide inner folds from the user
    # and only show one editor
    actions = state.actions[:]
    for idx, (action, nextact) in enumerate(zip(actions, actions[1:] + [None])):
        if action.verb == b'fold' and nextact and nextact.verb == b'fold':
            state.actions[idx].__class__ = _multifold

    # Force an initial state file write, so the user can run --abort/continue
    # even if there's an exception before the first transaction serialize.
    state.write()

    tr = None
    # Don't use singletransaction by default since it rolls the entire
    # transaction back if an unexpected exception happens (like a
    # pretxncommit hook throws, or the user aborts the commit msg editor).
    if ui.configbool(b"histedit", b"singletransaction"):
        # Don't use a 'with' for the transaction, since actions may close
        # and reopen a transaction. For example, if the action executes an
        # external process it may choose to commit the transaction first.
        tr = repo.transaction(b'histedit')
    progress = ui.makeprogress(
        _(b"editing"), unit=_(b'changes'), total=len(state.actions)
    )
    with progress, util.acceptintervention(tr):
        while state.actions:
            state.write(tr=tr)
            actobj = state.actions[0]
            progress.increment(item=actobj.torule())
            ui.debug(
                b'histedit: processing %s %s\n' % (actobj.verb, actobj.torule())
            )
            parentctx, replacement_ = actobj.run()
            state.parentctxnode = parentctx.node()
            state.replacements.extend(replacement_)
            state.actions.pop(0)

    state.write()


def _finishhistedit(ui, repo, state, fm):
    """This action runs when histedit is finishing its session"""
    mergemod.update(repo[state.parentctxnode])

    mapping, tmpnodes, created, ntm = processreplacement(state)
    if mapping:
        for prec, succs in pycompat.iteritems(mapping):
            if not succs:
                ui.debug(b'histedit: %s is dropped\n' % short(prec))
            else:
                ui.debug(
                    b'histedit: %s is replaced by %s\n'
                    % (short(prec), short(succs[0]))
                )
                if len(succs) > 1:
                    m = b'histedit:                            %s'
                    for n in succs[1:]:
                        ui.debug(m % short(n))

    if not state.keep:
        if mapping:
            movetopmostbookmarks(repo, state.topmost, ntm)
            # TODO update mq state
    else:
        mapping = {}

    for n in tmpnodes:
        if n in repo:
            mapping[n] = ()

    # remove entries about unknown nodes
    has_node = repo.unfiltered().changelog.index.has_node
    mapping = {
        k: v
        for k, v in mapping.items()
        if has_node(k) and all(has_node(n) for n in v)
    }
    scmutil.cleanupnodes(repo, mapping, b'histedit')
    hf = fm.hexfunc
    fl = fm.formatlist
    fd = fm.formatdict
    nodechanges = fd(
        {
            hf(oldn): fl([hf(n) for n in newn], name=b'node')
            for oldn, newn in pycompat.iteritems(mapping)
        },
        key=b"oldnode",
        value=b"newnodes",
    )
    fm.data(nodechanges=nodechanges)

    state.clear()
    if os.path.exists(repo.sjoin(b'undo')):
        os.unlink(repo.sjoin(b'undo'))
    if repo.vfs.exists(b'histedit-last-edit.txt'):
        repo.vfs.unlink(b'histedit-last-edit.txt')


def _aborthistedit(ui, repo, state, nobackup=False):
    try:
        state.read()
        __, leafs, tmpnodes, __ = processreplacement(state)
        ui.debug(b'restore wc to old parent %s\n' % short(state.topmost))

        # Recover our old commits if necessary
        if not state.topmost in repo and state.backupfile:
            backupfile = repo.vfs.join(state.backupfile)
            f = hg.openpath(ui, backupfile)
            gen = exchange.readbundle(ui, f, backupfile)
            with repo.transaction(b'histedit.abort') as tr:
                bundle2.applybundle(
                    repo,
                    gen,
                    tr,
                    source=b'histedit',
                    url=b'bundle:' + backupfile,
                )

            os.remove(backupfile)

        # check whether we should update away
        if repo.unfiltered().revs(
            b'parents() and (%n  or %ln::)',
            state.parentctxnode,
            leafs | tmpnodes,
        ):
            hg.clean(repo, state.topmost, show_stats=True, quietempty=True)
        cleanupnode(ui, repo, tmpnodes, nobackup=nobackup)
        cleanupnode(ui, repo, leafs, nobackup=nobackup)
    except Exception:
        if state.inprogress():
            ui.warn(
                _(
                    b'warning: encountered an exception during histedit '
                    b'--abort; the repository may not have been completely '
                    b'cleaned up\n'
                )
            )
        raise
    finally:
        state.clear()


def hgaborthistedit(ui, repo):
    state = histeditstate(repo)
    nobackup = not ui.configbool(b'rewrite', b'backup-bundle')
    with repo.wlock() as wlock, repo.lock() as lock:
        state.wlock = wlock
        state.lock = lock
        _aborthistedit(ui, repo, state, nobackup=nobackup)


def _edithisteditplan(ui, repo, state, rules):
    state.read()
    if not rules:
        comment = geteditcomment(
            ui, short(state.parentctxnode), short(state.topmost)
        )
        rules = ruleeditor(repo, ui, state.actions, comment)
    else:
        rules = _readfile(ui, rules)
    actions = parserules(rules, state)
    ctxs = [repo[act.node] for act in state.actions if act.node]
    warnverifyactions(ui, repo, actions, state, ctxs)
    state.actions = actions
    state.write()


def _newhistedit(ui, repo, state, revs, freeargs, opts):
    outg = opts.get(b'outgoing')
    rules = opts.get(b'commands', b'')
    force = opts.get(b'force')

    cmdutil.checkunfinished(repo)
    cmdutil.bailifchanged(repo)

    topmost = repo.dirstate.p1()
    if outg:
        if freeargs:
            remote = freeargs[0]
        else:
            remote = None
        root = findoutgoing(ui, repo, remote, force, opts)
    else:
        rr = list(repo.set(b'roots(%ld)', scmutil.revrange(repo, revs)))
        if len(rr) != 1:
            raise error.Abort(
                _(
                    b'The specified revisions must have '
                    b'exactly one common root'
                )
            )
        root = rr[0].node()

    revs = between(repo, root, topmost, state.keep)
    if not revs:
        raise error.Abort(
            _(b'%s is not an ancestor of working directory') % short(root)
        )

    ctxs = [repo[r] for r in revs]

    wctx = repo[None]
    # Please don't ask me why `ancestors` is this value. I figured it
    # out with print-debugging, not by actually understanding what the
    # merge code is doing. :(
    ancs = [repo[b'.']]
    # Sniff-test to make sure we won't collide with untracked files in
    # the working directory. If we don't do this, we can get a
    # collision after we've started histedit and backing out gets ugly
    # for everyone, especially the user.
    for c in [ctxs[0].p1()] + ctxs:
        try:
            mergemod.calculateupdates(
                repo,
                wctx,
                c,
                ancs,
                # These parameters were determined by print-debugging
                # what happens later on inside histedit.
                branchmerge=False,
                force=False,
                acceptremote=False,
                followcopies=False,
            )
        except error.Abort:
            raise error.Abort(
                _(
                    b"untracked files in working directory conflict with files in %s"
                )
                % c
            )

    if not rules:
        comment = geteditcomment(ui, short(root), short(topmost))
        actions = [pick(state, r) for r in revs]
        rules = ruleeditor(repo, ui, actions, comment)
    else:
        rules = _readfile(ui, rules)
    actions = parserules(rules, state)
    warnverifyactions(ui, repo, actions, state, ctxs)

    parentctxnode = repo[root].p1().node()

    state.parentctxnode = parentctxnode
    state.actions = actions
    state.topmost = topmost
    state.replacements = []

    ui.log(
        b"histedit",
        b"%d actions to histedit\n",
        len(actions),
        histedit_num_actions=len(actions),
    )

    # Create a backup so we can always abort completely.
    backupfile = None
    if not obsolete.isenabled(repo, obsolete.createmarkersopt):
        backupfile = repair.backupbundle(
            repo, [parentctxnode], [topmost], root, b'histedit'
        )
    state.backupfile = backupfile


def _getsummary(ctx):
    # a common pattern is to extract the summary but default to the empty
    # string
    summary = ctx.description() or b''
    if summary:
        summary = summary.splitlines()[0]
    return summary


def bootstrapcontinue(ui, state, opts):
    repo = state.repo

    ms = mergestatemod.mergestate.read(repo)
    mergeutil.checkunresolved(ms)

    if state.actions:
        actobj = state.actions.pop(0)

        if _isdirtywc(repo):
            actobj.continuedirty()
            if _isdirtywc(repo):
                abortdirty()

        parentctx, replacements = actobj.continueclean()

        state.parentctxnode = parentctx.node()
        state.replacements.extend(replacements)

    return state


def between(repo, old, new, keep):
    """select and validate the set of revision to edit

    When keep is false, the specified set can't have children."""
    revs = repo.revs(b'%n::%n', old, new)
    if revs and not keep:
        rewriteutil.precheck(repo, revs, b'edit')
        if repo.revs(b'(%ld) and merge()', revs):
            raise error.Abort(_(b'cannot edit history that contains merges'))
    return pycompat.maplist(repo.changelog.node, revs)


def ruleeditor(repo, ui, actions, editcomment=b""):
    """open an editor to edit rules

    rules are in the format [ [act, ctx], ...] like in state.rules
    """
    if repo.ui.configbool(b"experimental", b"histedit.autoverb"):
        newact = util.sortdict()
        for act in actions:
            ctx = repo[act.node]
            summary = _getsummary(ctx)
            fword = summary.split(b' ', 1)[0].lower()
            added = False

            # if it doesn't end with the special character '!' just skip this
            if fword.endswith(b'!'):
                fword = fword[:-1]
                if fword in primaryactions | secondaryactions | tertiaryactions:
                    act.verb = fword
                    # get the target summary
                    tsum = summary[len(fword) + 1 :].lstrip()
                    # safe but slow: reverse iterate over the actions so we
                    # don't clash on two commits having the same summary
                    for na, l in reversed(list(pycompat.iteritems(newact))):
                        actx = repo[na.node]
                        asum = _getsummary(actx)
                        if asum == tsum:
                            added = True
                            l.append(act)
                            break

            if not added:
                newact[act] = []

        # copy over and flatten the new list
        actions = []
        for na, l in pycompat.iteritems(newact):
            actions.append(na)
            actions += l

    rules = b'\n'.join([act.torule() for act in actions])
    rules += b'\n\n'
    rules += editcomment
    rules = ui.edit(
        rules,
        ui.username(),
        {b'prefix': b'histedit'},
        repopath=repo.path,
        action=b'histedit',
    )

    # Save edit rules in .hg/histedit-last-edit.txt in case
    # the user needs to ask for help after something
    # surprising happens.
    with repo.vfs(b'histedit-last-edit.txt', b'wb') as f:
        f.write(rules)

    return rules


def parserules(rules, state):
    """Read the histedit rules string and return list of action objects"""
    rules = [
        l
        for l in (r.strip() for r in rules.splitlines())
        if l and not l.startswith(b'#')
    ]
    actions = []
    for r in rules:
        if b' ' not in r:
            raise error.ParseError(_(b'malformed line "%s"') % r)
        verb, rest = r.split(b' ', 1)

        if verb not in actiontable:
            raise error.ParseError(_(b'unknown action "%s"') % verb)

        action = actiontable[verb].fromrule(state, rest)
        actions.append(action)
    return actions


def warnverifyactions(ui, repo, actions, state, ctxs):
    try:
        verifyactions(actions, state, ctxs)
    except error.ParseError:
        if repo.vfs.exists(b'histedit-last-edit.txt'):
            ui.warn(
                _(
                    b'warning: histedit rules saved '
                    b'to: .hg/histedit-last-edit.txt\n'
                )
            )
        raise


def verifyactions(actions, state, ctxs):
    """Verify that there exists exactly one action per given changeset and
    other constraints.

    Will abort if there are to many or too few rules, a malformed rule,
    or a rule on a changeset outside of the user-given range.
    """
    expected = {c.node() for c in ctxs}
    seen = set()
    prev = None

    if actions and actions[0].verb in [b'roll', b'fold']:
        raise error.ParseError(
            _(b'first changeset cannot use verb "%s"') % actions[0].verb
        )

    for action in actions:
        action.verify(prev, expected, seen)
        prev = action
        if action.node is not None:
            seen.add(action.node)
    missing = sorted(expected - seen)  # sort to stabilize output

    if state.repo.ui.configbool(b'histedit', b'dropmissing'):
        if len(actions) == 0:
            raise error.ParseError(
                _(b'no rules provided'),
                hint=_(b'use strip extension to remove commits'),
            )

        drops = [drop(state, n) for n in missing]
        # put the in the beginning so they execute immediately and
        # don't show in the edit-plan in the future
        actions[:0] = drops
    elif missing:
        raise error.ParseError(
            _(b'missing rules for changeset %s') % short(missing[0]),
            hint=_(
                b'use "drop %s" to discard, see also: '
                b"'hg help -e histedit.config'"
            )
            % short(missing[0]),
        )


def adjustreplacementsfrommarkers(repo, oldreplacements):
    """Adjust replacements from obsolescence markers

    Replacements structure is originally generated based on
    histedit's state and does not account for changes that are
    not recorded there. This function fixes that by adding
    data read from obsolescence markers"""
    if not obsolete.isenabled(repo, obsolete.createmarkersopt):
        return oldreplacements

    unfi = repo.unfiltered()
    get_rev = unfi.changelog.index.get_rev
    obsstore = repo.obsstore
    newreplacements = list(oldreplacements)
    oldsuccs = [r[1] for r in oldreplacements]
    # successors that have already been added to succstocheck once
    seensuccs = set().union(
        *oldsuccs
    )  # create a set from an iterable of tuples
    succstocheck = list(seensuccs)
    while succstocheck:
        n = succstocheck.pop()
        missing = get_rev(n) is None
        markers = obsstore.successors.get(n, ())
        if missing and not markers:
            # dead end, mark it as such
            newreplacements.append((n, ()))
        for marker in markers:
            nsuccs = marker[1]
            newreplacements.append((n, nsuccs))
            for nsucc in nsuccs:
                if nsucc not in seensuccs:
                    seensuccs.add(nsucc)
                    succstocheck.append(nsucc)

    return newreplacements


def processreplacement(state):
    """process the list of replacements to return

    1) the final mapping between original and created nodes
    2) the list of temporary node created by histedit
    3) the list of new commit created by histedit"""
    replacements = adjustreplacementsfrommarkers(state.repo, state.replacements)
    allsuccs = set()
    replaced = set()
    fullmapping = {}
    # initialize basic set
    # fullmapping records all operations recorded in replacement
    for rep in replacements:
        allsuccs.update(rep[1])
        replaced.add(rep[0])
        fullmapping.setdefault(rep[0], set()).update(rep[1])
    new = allsuccs - replaced
    tmpnodes = allsuccs & replaced
    # Reduce content fullmapping into direct relation between original nodes
    # and final node created during history edition
    # Dropped changeset are replaced by an empty list
    toproceed = set(fullmapping)
    final = {}
    while toproceed:
        for x in list(toproceed):
            succs = fullmapping[x]
            for s in list(succs):
                if s in toproceed:
                    # non final node with unknown closure
                    # We can't process this now
                    break
                elif s in final:
                    # non final node, replace with closure
                    succs.remove(s)
                    succs.update(final[s])
            else:
                final[x] = succs
                toproceed.remove(x)
    # remove tmpnodes from final mapping
    for n in tmpnodes:
        del final[n]
    # we expect all changes involved in final to exist in the repo
    # turn `final` into list (topologically sorted)
    get_rev = state.repo.changelog.index.get_rev
    for prec, succs in final.items():
        final[prec] = sorted(succs, key=get_rev)

    # computed topmost element (necessary for bookmark)
    if new:
        newtopmost = sorted(new, key=state.repo.changelog.rev)[-1]
    elif not final:
        # Nothing rewritten at all. we won't need `newtopmost`
        # It is the same as `oldtopmost` and `processreplacement` know it
        newtopmost = None
    else:
        # every body died. The newtopmost is the parent of the root.
        r = state.repo.changelog.rev
        newtopmost = state.repo[sorted(final, key=r)[0]].p1().node()

    return final, tmpnodes, new, newtopmost


def movetopmostbookmarks(repo, oldtopmost, newtopmost):
    """Move bookmark from oldtopmost to newly created topmost

    This is arguably a feature and we may only want that for the active
    bookmark. But the behavior is kept compatible with the old version for now.
    """
    if not oldtopmost or not newtopmost:
        return
    oldbmarks = repo.nodebookmarks(oldtopmost)
    if oldbmarks:
        with repo.lock(), repo.transaction(b'histedit') as tr:
            marks = repo._bookmarks
            changes = []
            for name in oldbmarks:
                changes.append((name, newtopmost))
            marks.applychanges(repo, tr, changes)


def cleanupnode(ui, repo, nodes, nobackup=False):
    """strip a group of nodes from the repository

    The set of node to strip may contains unknown nodes."""
    with repo.lock():
        # do not let filtering get in the way of the cleanse
        # we should probably get rid of obsolescence marker created during the
        # histedit, but we currently do not have such information.
        repo = repo.unfiltered()
        # Find all nodes that need to be stripped
        # (we use %lr instead of %ln to silently ignore unknown items)
        has_node = repo.changelog.index.has_node
        nodes = sorted(n for n in nodes if has_node(n))
        roots = [c.node() for c in repo.set(b"roots(%ln)", nodes)]
        if roots:
            backup = not nobackup
            repair.strip(ui, repo, roots, backup=backup)


def stripwrapper(orig, ui, repo, nodelist, *args, **kwargs):
    if isinstance(nodelist, bytes):
        nodelist = [nodelist]
    state = histeditstate(repo)
    if state.inprogress():
        state.read()
        histedit_nodes = {
            action.node for action in state.actions if action.node
        }
        common_nodes = histedit_nodes & set(nodelist)
        if common_nodes:
            raise error.Abort(
                _(b"histedit in progress, can't strip %s")
                % b', '.join(short(x) for x in common_nodes)
            )
    return orig(ui, repo, nodelist, *args, **kwargs)


extensions.wrapfunction(repair, b'strip', stripwrapper)


def summaryhook(ui, repo):
    state = histeditstate(repo)
    if not state.inprogress():
        return
    state.read()
    if state.actions:
        # i18n: column positioning for "hg summary"
        ui.write(
            _(b'hist:   %s (histedit --continue)\n')
            % (
                ui.label(_(b'%d remaining'), b'histedit.remaining')
                % len(state.actions)
            )
        )


def extsetup(ui):
    cmdutil.summaryhooks.add(b'histedit', summaryhook)
    statemod.addunfinished(
        b'histedit',
        fname=b'histedit-state',
        allowcommit=True,
        continueflag=True,
        abortfunc=hgaborthistedit,
    )
