# acl.py - changeset access control for mercurial
#
# Copyright 2006 Vadim Gelfer <vadim.gelfer@gmail.com>
#
# This software may be used and distributed according to the terms of the
# GNU General Public License version 2 or any later version.

'''hooks for controlling repository access

This hook makes it possible to allow or deny write access to portions
of a repository when receiving incoming changesets.

The authorization is matched based on the local user name on the
system where the hook runs, and not the committer of the original
changeset (since the latter is merely informative).

The acl hook is best used along with a restricted shell like hgsh,
preventing authenticating users from doing anything other than
pushing or pulling. The hook is not safe to use if users have
interactive shell access, as they can then disable the hook.
Nor is it safe if remote users share an account, because then there
is no way to distinguish them.

To use this hook, configure the acl extension in your hgrc like this::

  [extensions]
  acl =

  [hooks]
  pretxnchangegroup.acl = python:hgext.acl.hook

  [acl]
  # Check whether the source of incoming changes is in this list
  # ("serve" == ssh or http, "push", "pull", "bundle")
  sources = serve

The allow and deny sections take a subtree pattern as key (with a glob
syntax by default), and a comma separated list of users as the
corresponding value. The deny list is checked before the allow list
is. ::

  [acl.allow]
  # If acl.allow is not present, all users are allowed by default.
  # An empty acl.allow section means no users allowed.
  docs/** = doc_writer
  .hgtags = release_engineer

  [acl.deny]
  # If acl.deny is not present, no users are refused by default.
  # An empty acl.deny section means all users allowed.
  glob pattern = user4, user5
   ** = user6
'''

from mercurial.i18n import _
from mercurial import util, match
import getpass, urllib

def buildmatch(ui, repo, user, key):
    '''return tuple of (match function, list enabled).'''
    if not ui.has_section(key):
        ui.debug('acl: %s not enabled\n' % key)
        return None

    pats = [pat for pat, users in ui.configitems(key)
            if users == '*' or user in users.replace(',', ' ').split()]
    ui.debug('acl: %s enabled, %d entries for user %s\n' %
             (key, len(pats), user))
    if pats:
        return match.match(repo.root, '', pats)
    return match.exact(repo.root, '', [])


def hook(ui, repo, hooktype, node=None, source=None, **kwargs):
    if hooktype != 'pretxnchangegroup':
        raise util.Abort(_('config error - hook type "%s" cannot stop '
                           'incoming changesets') % hooktype)
    if source not in ui.config('acl', 'sources', 'serve').split():
        ui.debug('acl: changes have source "%s" - skipping\n' % source)
        return

    user = None
    if source == 'serve' and 'url' in kwargs:
        url = kwargs['url'].split(':')
        if url[0] == 'remote' and url[1].startswith('http'):
            user = urllib.unquote(url[3])

    if user is None:
        user = getpass.getuser()

    cfg = ui.config('acl', 'config')
    if cfg:
        ui.readconfig(cfg, sections = ['acl.allow', 'acl.deny'])
    allow = buildmatch(ui, repo, user, 'acl.allow')
    deny = buildmatch(ui, repo, user, 'acl.deny')

    for rev in xrange(repo[node], len(repo)):
        ctx = repo[rev]
        for f in ctx.files():
            if deny and deny(f):
                ui.debug('acl: user %s denied on %s\n' % (user, f))
                raise util.Abort(_('acl: access denied for changeset %s') % ctx)
            if allow and not allow(f):
                ui.debug('acl: user %s not allowed on %s\n' % (user, f))
                raise util.Abort(_('acl: access denied for changeset %s') % ctx)
        ui.debug('acl: allowing changeset %s\n' % ctx)
