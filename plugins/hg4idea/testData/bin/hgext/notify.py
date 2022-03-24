# notify.py - email notifications for mercurial
#
# Copyright 2006 Vadim Gelfer <vadim.gelfer@gmail.com>
#
# This software may be used and distributed according to the terms of the
# GNU General Public License version 2 or any later version.

'''hooks for sending email push notifications

This extension implements hooks to send email notifications when
changesets are sent from or received by the local repository.

First, enable the extension as explained in :hg:`help extensions`, and
register the hook you want to run. ``incoming`` and ``changegroup`` hooks
are run when changesets are received, while ``outgoing`` hooks are for
changesets sent to another repository::

  [hooks]
  # one email for each incoming changeset
  incoming.notify = python:hgext.notify.hook
  # one email for all incoming changesets
  changegroup.notify = python:hgext.notify.hook

  # one email for all outgoing changesets
  outgoing.notify = python:hgext.notify.hook

This registers the hooks. To enable notification, subscribers must
be assigned to repositories. The ``[usersubs]`` section maps multiple
repositories to a given recipient. The ``[reposubs]`` section maps
multiple recipients to a single repository::

  [usersubs]
  # key is subscriber email, value is a comma-separated list of repo patterns
  user@host = pattern

  [reposubs]
  # key is repo pattern, value is a comma-separated list of subscriber emails
  pattern = user@host

A ``pattern`` is a ``glob`` matching the absolute path to a repository,
optionally combined with a revset expression. A revset expression, if
present, is separated from the glob by a hash. Example::

  [reposubs]
  */widgets#branch(release) = qa-team@example.com

This sends to ``qa-team@example.com`` whenever a changeset on the ``release``
branch triggers a notification in any repository ending in ``widgets``.

In order to place them under direct user management, ``[usersubs]`` and
``[reposubs]`` sections may be placed in a separate ``hgrc`` file and
incorporated by reference::

  [notify]
  config = /path/to/subscriptionsfile

Notifications will not be sent until the ``notify.test`` value is set
to ``False``; see below.

Notifications content can be tweaked with the following configuration entries:

notify.test
  If ``True``, print messages to stdout instead of sending them. Default: True.

notify.sources
  Space-separated list of change sources. Notifications are activated only
  when a changeset's source is in this list. Sources may be:

  :``serve``: changesets received via http or ssh
  :``pull``: changesets received via ``hg pull``
  :``unbundle``: changesets received via ``hg unbundle``
  :``push``: changesets sent or received via ``hg push``
  :``bundle``: changesets sent via ``hg unbundle``

  Default: serve.

notify.strip
  Number of leading slashes to strip from url paths. By default, notifications
  reference repositories with their absolute path. ``notify.strip`` lets you
  turn them into relative paths. For example, ``notify.strip=3`` will change
  ``/long/path/repository`` into ``repository``. Default: 0.

notify.domain
  Default email domain for sender or recipients with no explicit domain.
  It is also used for the domain part of the ``Message-Id`` when using
  ``notify.messageidseed``.

notify.messageidseed
  Create deterministic ``Message-Id`` headers for the mails based on the seed
  and the revision identifier of the first commit in the changeset.

notify.style
  Style file to use when formatting emails.

notify.template
  Template to use when formatting emails.

notify.incoming
  Template to use when run as an incoming hook, overriding ``notify.template``.

notify.outgoing
  Template to use when run as an outgoing hook, overriding ``notify.template``.

notify.changegroup
  Template to use when running as a changegroup hook, overriding
  ``notify.template``.

notify.maxdiff
  Maximum number of diff lines to include in notification email. Set to 0
  to disable the diff, or -1 to include all of it. Default: 300.

notify.maxdiffstat
  Maximum number of diffstat lines to include in notification email. Set to -1
  to include all of it. Default: -1.

notify.maxsubject
  Maximum number of characters in email's subject line. Default: 67.

notify.diffstat
  Set to True to include a diffstat before diff content. Default: True.

notify.showfunc
  If set, override ``diff.showfunc`` for the diff content. Default: None.

notify.merge
  If True, send notifications for merge changesets. Default: True.

notify.mbox
  If set, append mails to this mbox file instead of sending. Default: None.

notify.fromauthor
  If set, use the committer of the first changeset in a changegroup for
  the "From" field of the notification mail. If not set, take the user
  from the pushing repo.  Default: False.

notify.reply-to-predecessor (EXPERIMENTAL)
  If set and the changeset has a predecessor in the repository, try to thread
  the notification mail with the predecessor. This adds the "In-Reply-To" header
  to the notification mail with a reference to the predecessor with the smallest
  revision number. Mail threads can still be torn, especially when changesets
  are folded.

  This option must  be used in combination with ``notify.messageidseed``.

If set, the following entries will also be used to customize the
notifications:

email.from
  Email ``From`` address to use if none can be found in the generated
  email content.

web.baseurl
  Root repository URL to combine with repository paths when making
  references. See also ``notify.strip``.

'''
from __future__ import absolute_import

import email.errors as emailerrors
import email.utils as emailutils
import fnmatch
import hashlib
import socket
import time

from mercurial.i18n import _
from mercurial import (
    encoding,
    error,
    logcmdutil,
    mail,
    obsutil,
    patch,
    pycompat,
    registrar,
    util,
)
from mercurial.utils import (
    dateutil,
    stringutil,
)

# Note for extension authors: ONLY specify testedwith = 'ships-with-hg-core' for
# extensions which SHIP WITH MERCURIAL. Non-mainline extensions should
# be specifying the version(s) of Mercurial they are tested with, or
# leave the attribute unspecified.
testedwith = b'ships-with-hg-core'

configtable = {}
configitem = registrar.configitem(configtable)

configitem(
    b'notify',
    b'changegroup',
    default=None,
)
configitem(
    b'notify',
    b'config',
    default=None,
)
configitem(
    b'notify',
    b'diffstat',
    default=True,
)
configitem(
    b'notify',
    b'domain',
    default=None,
)
configitem(
    b'notify',
    b'messageidseed',
    default=None,
)
configitem(
    b'notify',
    b'fromauthor',
    default=None,
)
configitem(
    b'notify',
    b'incoming',
    default=None,
)
configitem(
    b'notify',
    b'maxdiff',
    default=300,
)
configitem(
    b'notify',
    b'maxdiffstat',
    default=-1,
)
configitem(
    b'notify',
    b'maxsubject',
    default=67,
)
configitem(
    b'notify',
    b'mbox',
    default=None,
)
configitem(
    b'notify',
    b'merge',
    default=True,
)
configitem(
    b'notify',
    b'outgoing',
    default=None,
)
configitem(
    b'notify',
    b'reply-to-predecessor',
    default=False,
)
configitem(
    b'notify',
    b'sources',
    default=b'serve',
)
configitem(
    b'notify',
    b'showfunc',
    default=None,
)
configitem(
    b'notify',
    b'strip',
    default=0,
)
configitem(
    b'notify',
    b'style',
    default=None,
)
configitem(
    b'notify',
    b'template',
    default=None,
)
configitem(
    b'notify',
    b'test',
    default=True,
)

# template for single changeset can include email headers.
single_template = b'''
Subject: changeset in {webroot}: {desc|firstline|strip}
From: {author}

changeset {node|short} in {root}
details: {baseurl}{webroot}?cmd=changeset;node={node|short}
description:
\t{desc|tabindent|strip}
'''.lstrip()

# template for multiple changesets should not contain email headers,
# because only first set of headers will be used and result will look
# strange.
multiple_template = b'''
changeset {node|short} in {root}
details: {baseurl}{webroot}?cmd=changeset;node={node|short}
summary: {desc|firstline}
'''

deftemplates = {
    b'changegroup': multiple_template,
}


class notifier(object):
    '''email notification class.'''

    def __init__(self, ui, repo, hooktype):
        self.ui = ui
        cfg = self.ui.config(b'notify', b'config')
        if cfg:
            self.ui.readconfig(cfg, sections=[b'usersubs', b'reposubs'])
        self.repo = repo
        self.stripcount = int(self.ui.config(b'notify', b'strip'))
        self.root = self.strip(self.repo.root)
        self.domain = self.ui.config(b'notify', b'domain')
        self.mbox = self.ui.config(b'notify', b'mbox')
        self.test = self.ui.configbool(b'notify', b'test')
        self.charsets = mail._charsets(self.ui)
        self.subs = self.subscribers()
        self.merge = self.ui.configbool(b'notify', b'merge')
        self.showfunc = self.ui.configbool(b'notify', b'showfunc')
        self.messageidseed = self.ui.config(b'notify', b'messageidseed')
        self.reply = self.ui.configbool(b'notify', b'reply-to-predecessor')

        if self.reply and not self.messageidseed:
            raise error.Abort(
                _(
                    b'notify.reply-to-predecessor used without '
                    b'notify.messageidseed'
                )
            )

        if self.showfunc is None:
            self.showfunc = self.ui.configbool(b'diff', b'showfunc')

        mapfile = None
        template = self.ui.config(b'notify', hooktype) or self.ui.config(
            b'notify', b'template'
        )
        if not template:
            mapfile = self.ui.config(b'notify', b'style')
        if not mapfile and not template:
            template = deftemplates.get(hooktype) or single_template
        spec = logcmdutil.templatespec(template, mapfile)
        self.t = logcmdutil.changesettemplater(self.ui, self.repo, spec)

    def strip(self, path):
        '''strip leading slashes from local path, turn into web-safe path.'''

        path = util.pconvert(path)
        count = self.stripcount
        while count > 0:
            c = path.find(b'/')
            if c == -1:
                break
            path = path[c + 1 :]
            count -= 1
        return path

    def fixmail(self, addr):
        '''try to clean up email addresses.'''

        addr = stringutil.email(addr.strip())
        if self.domain:
            a = addr.find(b'@localhost')
            if a != -1:
                addr = addr[:a]
            if b'@' not in addr:
                return addr + b'@' + self.domain
        return addr

    def subscribers(self):
        '''return list of email addresses of subscribers to this repo.'''
        subs = set()
        for user, pats in self.ui.configitems(b'usersubs'):
            for pat in pats.split(b','):
                if b'#' in pat:
                    pat, revs = pat.split(b'#', 1)
                else:
                    revs = None
                if fnmatch.fnmatch(self.repo.root, pat.strip()):
                    subs.add((self.fixmail(user), revs))
        for pat, users in self.ui.configitems(b'reposubs'):
            if b'#' in pat:
                pat, revs = pat.split(b'#', 1)
            else:
                revs = None
            if fnmatch.fnmatch(self.repo.root, pat):
                for user in users.split(b','):
                    subs.add((self.fixmail(user), revs))
        return [
            (mail.addressencode(self.ui, s, self.charsets, self.test), r)
            for s, r in sorted(subs)
        ]

    def node(self, ctx, **props):
        '''format one changeset, unless it is a suppressed merge.'''
        if not self.merge and len(ctx.parents()) > 1:
            return False
        self.t.show(
            ctx,
            changes=ctx.changeset(),
            baseurl=self.ui.config(b'web', b'baseurl'),
            root=self.repo.root,
            webroot=self.root,
            **props
        )
        return True

    def skipsource(self, source):
        '''true if incoming changes from this source should be skipped.'''
        ok_sources = self.ui.config(b'notify', b'sources').split()
        return source not in ok_sources

    def send(self, ctx, count, data):
        '''send message.'''

        # Select subscribers by revset
        subs = set()
        for sub, spec in self.subs:
            if spec is None:
                subs.add(sub)
                continue
            revs = self.repo.revs(b'%r and %d:', spec, ctx.rev())
            if len(revs):
                subs.add(sub)
                continue
        if len(subs) == 0:
            self.ui.debug(
                b'notify: no subscribers to selected repo and revset\n'
            )
            return

        try:
            msg = mail.parsebytes(data)
        except emailerrors.MessageParseError as inst:
            raise error.Abort(inst)

        # store sender and subject
        sender = msg['From']
        subject = msg['Subject']
        if sender is not None:
            sender = mail.headdecode(sender)
        if subject is not None:
            subject = mail.headdecode(subject)
        del msg['From'], msg['Subject']

        if not msg.is_multipart():
            # create fresh mime message from scratch
            # (multipart templates must take care of this themselves)
            headers = msg.items()
            payload = msg.get_payload(decode=pycompat.ispy3)
            # for notification prefer readability over data precision
            msg = mail.mimeencode(self.ui, payload, self.charsets, self.test)
            # reinstate custom headers
            for k, v in headers:
                msg[k] = v

        msg['Date'] = encoding.strfromlocal(
            dateutil.datestr(format=b"%a, %d %b %Y %H:%M:%S %1%2")
        )

        # try to make subject line exist and be useful
        if not subject:
            if count > 1:
                subject = _(b'%s: %d new changesets') % (self.root, count)
            else:
                s = ctx.description().lstrip().split(b'\n', 1)[0].rstrip()
                subject = b'%s: %s' % (self.root, s)
        maxsubject = int(self.ui.config(b'notify', b'maxsubject'))
        if maxsubject:
            subject = stringutil.ellipsis(subject, maxsubject)
        msg['Subject'] = mail.headencode(
            self.ui, subject, self.charsets, self.test
        )

        # try to make message have proper sender
        if not sender:
            sender = self.ui.config(b'email', b'from') or self.ui.username()
        if b'@' not in sender or b'@localhost' in sender:
            sender = self.fixmail(sender)
        msg['From'] = mail.addressencode(
            self.ui, sender, self.charsets, self.test
        )

        msg['X-Hg-Notification'] = 'changeset %s' % ctx
        if not msg['Message-Id']:
            msg['Message-Id'] = messageid(ctx, self.domain, self.messageidseed)
        if self.reply:
            unfi = self.repo.unfiltered()
            has_node = unfi.changelog.index.has_node
            predecessors = [
                unfi[ctx2]
                for ctx2 in obsutil.allpredecessors(unfi.obsstore, [ctx.node()])
                if ctx2 != ctx.node() and has_node(ctx2)
            ]
            if predecessors:
                # There is at least one predecessor, so which to pick?
                # Ideally, there is a unique root because changesets have
                # been evolved/rebased one step at a time. In this case,
                # just picking the oldest known changeset provides a stable
                # base. It doesn't help when changesets are folded. Any
                # better solution would require storing more information
                # in the repository.
                pred = min(predecessors, key=lambda ctx: ctx.rev())
                msg['In-Reply-To'] = messageid(
                    pred, self.domain, self.messageidseed
                )
        msg['To'] = ', '.join(sorted(subs))

        msgtext = msg.as_bytes() if pycompat.ispy3 else msg.as_string()
        if self.test:
            self.ui.write(msgtext)
            if not msgtext.endswith(b'\n'):
                self.ui.write(b'\n')
        else:
            self.ui.status(
                _(b'notify: sending %d subscribers %d changes\n')
                % (len(subs), count)
            )
            mail.sendmail(
                self.ui,
                emailutils.parseaddr(msg['From'])[1],
                subs,
                msgtext,
                mbox=self.mbox,
            )

    def diff(self, ctx, ref=None):

        maxdiff = int(self.ui.config(b'notify', b'maxdiff'))
        prev = ctx.p1().node()
        if ref:
            ref = ref.node()
        else:
            ref = ctx.node()
        diffopts = patch.diffallopts(self.ui)
        diffopts.showfunc = self.showfunc
        chunks = patch.diff(self.repo, prev, ref, opts=diffopts)
        difflines = b''.join(chunks).splitlines()

        if self.ui.configbool(b'notify', b'diffstat'):
            maxdiffstat = int(self.ui.config(b'notify', b'maxdiffstat'))
            s = patch.diffstat(difflines)
            # s may be nil, don't include the header if it is
            if s:
                if maxdiffstat >= 0 and s.count(b"\n") > maxdiffstat + 1:
                    s = s.split(b"\n")
                    msg = _(b'\ndiffstat (truncated from %d to %d lines):\n\n')
                    self.ui.write(msg % (len(s) - 2, maxdiffstat))
                    self.ui.write(b"\n".join(s[:maxdiffstat] + s[-2:]))
                else:
                    self.ui.write(_(b'\ndiffstat:\n\n%s') % s)

        if maxdiff == 0:
            return
        elif maxdiff > 0 and len(difflines) > maxdiff:
            msg = _(b'\ndiffs (truncated from %d to %d lines):\n\n')
            self.ui.write(msg % (len(difflines), maxdiff))
            difflines = difflines[:maxdiff]
        elif difflines:
            self.ui.write(_(b'\ndiffs (%d lines):\n\n') % len(difflines))

        self.ui.write(b"\n".join(difflines))


def hook(ui, repo, hooktype, node=None, source=None, **kwargs):
    """send email notifications to interested subscribers.

    if used as changegroup hook, send one email for all changesets in
    changegroup. else send one email per changeset."""

    n = notifier(ui, repo, hooktype)
    ctx = repo.unfiltered()[node]

    if not n.subs:
        ui.debug(b'notify: no subscribers to repository %s\n' % n.root)
        return
    if n.skipsource(source):
        ui.debug(b'notify: changes have source "%s" - skipping\n' % source)
        return

    ui.pushbuffer()
    data = b''
    count = 0
    author = b''
    if hooktype == b'changegroup' or hooktype == b'outgoing':
        for rev in repo.changelog.revs(start=ctx.rev()):
            if n.node(repo[rev]):
                count += 1
                if not author:
                    author = repo[rev].user()
            else:
                data += ui.popbuffer()
                ui.note(
                    _(b'notify: suppressing notification for merge %d:%s\n')
                    % (rev, repo[rev].hex()[:12])
                )
                ui.pushbuffer()
        if count:
            n.diff(ctx, repo[b'tip'])
    elif ctx.rev() in repo:
        if not n.node(ctx):
            ui.popbuffer()
            ui.note(
                _(b'notify: suppressing notification for merge %d:%s\n')
                % (ctx.rev(), ctx.hex()[:12])
            )
            return
        count += 1
        n.diff(ctx)
        if not author:
            author = ctx.user()

    data += ui.popbuffer()
    fromauthor = ui.config(b'notify', b'fromauthor')
    if author and fromauthor:
        data = b'\n'.join([b'From: %s' % author, data])

    if count:
        n.send(ctx, count, data)


def messageid(ctx, domain, messageidseed):
    if domain and messageidseed:
        host = domain
    else:
        host = encoding.strtolocal(socket.getfqdn())
    if messageidseed:
        messagehash = hashlib.sha512(ctx.hex() + messageidseed)
        messageid = b'<hg.%s@%s>' % (
            pycompat.sysbytes(messagehash.hexdigest()[:64]),
            host,
        )
    else:
        messageid = b'<hg.%s.%d.%d@%s>' % (
            ctx,
            int(time.time()),
            hash(ctx.repo().root),
            host,
        )
    return encoding.strfromlocal(messageid)
