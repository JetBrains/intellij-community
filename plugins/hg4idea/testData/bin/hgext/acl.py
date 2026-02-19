# acl.py - changeset access control for mercurial
#
# Copyright 2006 Vadim Gelfer <vadim.gelfer@gmail.com>
#
# This software may be used and distributed according to the terms of the
# GNU General Public License version 2 or any later version.

'''hooks for controlling repository access

This hook makes it possible to allow or deny write access to given
branches and paths of a repository when receiving incoming changesets
via pretxnchangegroup and pretxncommit.

The authorization is matched based on the local user name on the
system where the hook runs, and not the committer of the original
changeset (since the latter is merely informative).

The acl hook is best used along with a restricted shell like hgsh,
preventing authenticating users from doing anything other than pushing
or pulling. The hook is not safe to use if users have interactive
shell access, as they can then disable the hook. Nor is it safe if
remote users share an account, because then there is no way to
distinguish them.

The order in which access checks are performed is:

1) Deny  list for branches (section ``acl.deny.branches``)
2) Allow list for branches (section ``acl.allow.branches``)
3) Deny  list for paths    (section ``acl.deny``)
4) Allow list for paths    (section ``acl.allow``)

The allow and deny sections take key-value pairs.

Branch-based Access Control
---------------------------

Use the ``acl.deny.branches`` and ``acl.allow.branches`` sections to
have branch-based access control. Keys in these sections can be
either:

- a branch name, or
- an asterisk, to match any branch;

The corresponding values can be either:

- a comma-separated list containing users and groups, or
- an asterisk, to match anyone;

You can add the "!" prefix to a user or group name to invert the sense
of the match.

Path-based Access Control
-------------------------

Use the ``acl.deny`` and ``acl.allow`` sections to have path-based
access control. Keys in these sections accept a subtree pattern (with
a glob syntax by default). The corresponding values follow the same
syntax as the other sections above.

Bookmark-based Access Control
-----------------------------
Use the ``acl.deny.bookmarks`` and ``acl.allow.bookmarks`` sections to
have bookmark-based access control. Keys in these sections can be
either:

- a bookmark name, or
- an asterisk, to match any bookmark;

The corresponding values can be either:

- a comma-separated list containing users and groups, or
- an asterisk, to match anyone;

You can add the "!" prefix to a user or group name to invert the sense
of the match.

Note: for interactions between clients and servers using Mercurial 3.6+
a rejection will generally reject the entire push, for interactions
involving older clients, the commit transactions will already be accepted,
and only the bookmark movement will be rejected.

Groups
------

Group names must be prefixed with an ``@`` symbol. Specifying a group
name has the same effect as specifying all the users in that group.

You can define group members in the ``acl.groups`` section.
If a group name is not defined there, and Mercurial is running under
a Unix-like system, the list of users will be taken from the OS.
Otherwise, an exception will be raised.

Example Configuration
---------------------

::

  [hooks]

  # Use this if you want to check access restrictions at commit time
  pretxncommit.acl = python:hgext.acl.hook

  # Use this if you want to check access restrictions for pull, push,
  # bundle and serve.
  pretxnchangegroup.acl = python:hgext.acl.hook

  [acl]
  # Allow or deny access for incoming changes only if their source is
  # listed here, let them pass otherwise. Source is "serve" for all
  # remote access (http or ssh), "push", "pull" or "bundle" when the
  # related commands are run locally.
  # Default: serve
  sources = serve

  [acl.deny.branches]

  # Everyone is denied to the frozen branch:
  frozen-branch = *

  # A bad user is denied on all branches:
  * = bad-user

  [acl.allow.branches]

  # A few users are allowed on branch-a:
  branch-a = user-1, user-2, user-3

  # Only one user is allowed on branch-b:
  branch-b = user-1

  # The super user is allowed on any branch:
  * = super-user

  # Everyone is allowed on branch-for-tests:
  branch-for-tests = *

  [acl.deny]
  # This list is checked first. If a match is found, acl.allow is not
  # checked. All users are granted access if acl.deny is not present.
  # Format for both lists: glob pattern = user, ..., @group, ...

  # To match everyone, use an asterisk for the user:
  # my/glob/pattern = *

  # user6 will not have write access to any file:
  ** = user6

  # Group "hg-denied" will not have write access to any file:
  ** = @hg-denied

  # Nobody will be able to change "DONT-TOUCH-THIS.txt", despite
  # everyone being able to change all other files. See below.
  src/main/resources/DONT-TOUCH-THIS.txt = *

  [acl.allow]
  # if acl.allow is not present, all users are allowed by default
  # empty acl.allow = no users allowed

  # User "doc_writer" has write access to any file under the "docs"
  # folder:
  docs/** = doc_writer

  # User "jack" and group "designers" have write access to any file
  # under the "images" folder:
  images/** = jack, @designers

  # Everyone (except for "user6" and "@hg-denied" - see acl.deny above)
  # will have write access to any file under the "resources" folder
  # (except for 1 file. See acl.deny):
  src/main/resources/** = *

  .hgtags = release_engineer

Examples using the "!" prefix
.............................

Suppose there's a branch that only a given user (or group) should be able to
push to, and you don't want to restrict access to any other branch that may
be created.

The "!" prefix allows you to prevent anyone except a given user or group to
push changesets in a given branch or path.

In the examples below, we will:
1) Deny access to branch "ring" to anyone but user "gollum"
2) Deny access to branch "lake" to anyone but members of the group "hobbit"
3) Deny access to a file to anyone but user "gollum"

::

  [acl.allow.branches]
  # Empty

  [acl.deny.branches]

  # 1) only 'gollum' can commit to branch 'ring';
  # 'gollum' and anyone else can still commit to any other branch.
  ring = !gollum

  # 2) only members of the group 'hobbit' can commit to branch 'lake';
  # 'hobbit' members and anyone else can still commit to any other branch.
  lake = !@hobbit

  # You can also deny access based on file paths:

  [acl.allow]
  # Empty

  [acl.deny]
  # 3) only 'gollum' can change the file below;
  # 'gollum' and anyone else can still change any other file.
  /misty/mountains/cave/ring = !gollum

'''


from mercurial.i18n import _
from mercurial import (
    error,
    extensions,
    match,
    registrar,
    util,
)
from mercurial.utils import procutil

urlreq = util.urlreq

# Note for extension authors: ONLY specify testedwith = 'ships-with-hg-core' for
# extensions which SHIP WITH MERCURIAL. Non-mainline extensions should
# be specifying the version(s) of Mercurial they are tested with, or
# leave the attribute unspecified.
testedwith = b'ships-with-hg-core'

configtable = {}
configitem = registrar.configitem(configtable)

# deprecated config: acl.config
configitem(
    b'acl',
    b'config',
    default=None,
)
configitem(
    b'acl.groups',
    b'.*',
    default=None,
    generic=True,
)
configitem(
    b'acl.deny.branches',
    b'.*',
    default=None,
    generic=True,
)
configitem(
    b'acl.allow.branches',
    b'.*',
    default=None,
    generic=True,
)
configitem(
    b'acl.deny',
    b'.*',
    default=None,
    generic=True,
)
configitem(
    b'acl.allow',
    b'.*',
    default=None,
    generic=True,
)
configitem(
    b'acl',
    b'sources',
    default=lambda: [b'serve'],
)


def _getusers(ui, group):

    # First, try to use group definition from section [acl.groups]
    hgrcusers = ui.configlist(b'acl.groups', group)
    if hgrcusers:
        return hgrcusers

    ui.debug(b'acl: "%s" not defined in [acl.groups]\n' % group)
    # If no users found in group definition, get users from OS-level group
    try:
        return util.groupmembers(group)
    except KeyError:
        raise error.Abort(_(b"group '%s' is undefined") % group)


def _usermatch(ui, user, usersorgroups):

    if usersorgroups == b'*':
        return True

    for ug in usersorgroups.replace(b',', b' ').split():

        if ug.startswith(b'!'):
            # Test for excluded user or group. Format:
            # if ug is a user  name: !username
            # if ug is a group name: !@groupname
            ug = ug[1:]
            if (
                not ug.startswith(b'@')
                and user != ug
                or ug.startswith(b'@')
                and user not in _getusers(ui, ug[1:])
            ):
                return True

        # Test for user or group. Format:
        # if ug is a user  name: username
        # if ug is a group name: @groupname
        elif (
            user == ug or ug.startswith(b'@') and user in _getusers(ui, ug[1:])
        ):
            return True

    return False


def buildmatch(ui, repo, user, key):
    '''return tuple of (match function, list enabled).'''
    if not ui.has_section(key):
        ui.debug(b'acl: %s not enabled\n' % key)
        return None

    pats = [
        pat for pat, users in ui.configitems(key) if _usermatch(ui, user, users)
    ]
    ui.debug(
        b'acl: %s enabled, %d entries for user %s\n' % (key, len(pats), user)
    )

    # Branch-based ACL
    if not repo:
        if pats:
            # If there's an asterisk (meaning "any branch"), always return True;
            # Otherwise, test if b is in pats
            if b'*' in pats:
                return util.always
            return lambda b: b in pats
        return util.never

    # Path-based ACL
    if pats:
        return match.match(repo.root, b'', pats)
    return util.never


def ensureenabled(ui):
    """make sure the extension is enabled when used as hook

    When acl is used through hooks, the extension is never formally loaded and
    enabled. This has some side effect, for example the config declaration is
    never loaded. This function ensure the extension is enabled when running
    hooks.
    """
    if b'acl' in ui._knownconfig:
        return
    ui.setconfig(b'extensions', b'acl', b'', source=b'internal')
    extensions.loadall(ui, [b'acl'])


def hook(ui, repo, hooktype, node=None, source=None, **kwargs):

    ensureenabled(ui)

    if hooktype not in [b'pretxnchangegroup', b'pretxncommit', b'prepushkey']:
        raise error.Abort(
            _(
                b'config error - hook type "%s" cannot stop '
                b'incoming changesets, commits, nor bookmarks'
            )
            % hooktype
        )
    if hooktype == b'pretxnchangegroup' and source not in ui.configlist(
        b'acl', b'sources'
    ):
        ui.debug(b'acl: changes have source "%s" - skipping\n' % source)
        return

    user = None
    if source == b'serve' and 'url' in kwargs:
        url = kwargs['url'].split(b':')
        if url[0] == b'remote' and url[1].startswith(b'http'):
            user = urlreq.unquote(url[3])

    if user is None:
        user = procutil.getuser()

    ui.debug(b'acl: checking access for user "%s"\n' % user)

    if hooktype == b'prepushkey':
        _pkhook(ui, repo, hooktype, node, source, user, **kwargs)
    else:
        _txnhook(ui, repo, hooktype, node, source, user, **kwargs)


def _pkhook(ui, repo, hooktype, node, source, user, **kwargs):
    if kwargs['namespace'] == b'bookmarks':
        bookmark = kwargs['key']
        ctx = kwargs['new']
        allowbookmarks = buildmatch(ui, None, user, b'acl.allow.bookmarks')
        denybookmarks = buildmatch(ui, None, user, b'acl.deny.bookmarks')

        if denybookmarks and denybookmarks(bookmark):
            raise error.Abort(
                _(
                    b'acl: user "%s" denied on bookmark "%s"'
                    b' (changeset "%s")'
                )
                % (user, bookmark, ctx)
            )
        if allowbookmarks and not allowbookmarks(bookmark):
            raise error.Abort(
                _(
                    b'acl: user "%s" not allowed on bookmark "%s"'
                    b' (changeset "%s")'
                )
                % (user, bookmark, ctx)
            )
        ui.debug(
            b'acl: bookmark access granted: "%s" on bookmark "%s"\n'
            % (ctx, bookmark)
        )


def _txnhook(ui, repo, hooktype, node, source, user, **kwargs):
    # deprecated config: acl.config
    cfg = ui.config(b'acl', b'config')
    if cfg:
        ui.readconfig(
            cfg,
            sections=[
                b'acl.groups',
                b'acl.allow.branches',
                b'acl.deny.branches',
                b'acl.allow',
                b'acl.deny',
            ],
        )

    allowbranches = buildmatch(ui, None, user, b'acl.allow.branches')
    denybranches = buildmatch(ui, None, user, b'acl.deny.branches')
    allow = buildmatch(ui, repo, user, b'acl.allow')
    deny = buildmatch(ui, repo, user, b'acl.deny')

    for rev in range(repo[node].rev(), len(repo)):
        ctx = repo[rev]
        branch = ctx.branch()
        if denybranches and denybranches(branch):
            raise error.Abort(
                _(b'acl: user "%s" denied on branch "%s" (changeset "%s")')
                % (user, branch, ctx)
            )
        if allowbranches and not allowbranches(branch):
            raise error.Abort(
                _(
                    b'acl: user "%s" not allowed on branch "%s"'
                    b' (changeset "%s")'
                )
                % (user, branch, ctx)
            )
        ui.debug(
            b'acl: branch access granted: "%s" on branch "%s"\n' % (ctx, branch)
        )

        for f in ctx.files():
            if deny and deny(f):
                raise error.Abort(
                    _(b'acl: user "%s" denied on "%s" (changeset "%s")')
                    % (user, f, ctx)
                )
            if allow and not allow(f):
                raise error.Abort(
                    _(
                        b'acl: user "%s" not allowed on "%s"'
                        b' (changeset "%s")'
                    )
                    % (user, f, ctx)
                )
        ui.debug(b'acl: path access granted: "%s"\n' % ctx)
