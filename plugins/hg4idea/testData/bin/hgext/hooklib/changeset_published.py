# Copyright 2020 Joerg Sonnenberger <joerg@bec.de>
#
# This software may be used and distributed according to the terms of the
# GNU General Public License version 2 or any later version.
"""changeset_published is a hook to send a mail when an
existing draft changeset is moved to the public phase.

Correct message threading requires the same messageidseed to be used for both
the original notification and the new mail.

Usage:
  [notify]
  messageidseed = myseed

  [hooks]
  txnclose-phase.changeset_published = \
    python:hgext.hooklib.changeset_published.hook
"""

from __future__ import absolute_import

import email.errors as emailerrors
import email.utils as emailutils

from mercurial.i18n import _
from mercurial import (
    encoding,
    error,
    formatter,
    logcmdutil,
    mail,
    pycompat,
    registrar,
)
from mercurial.utils import dateutil
from .. import notify

configtable = {}
configitem = registrar.configitem(configtable)

configitem(
    b'notify_published',
    b'domain',
    default=None,
)
configitem(
    b'notify_published',
    b'messageidseed',
    default=None,
)
configitem(
    b'notify_published',
    b'template',
    default=b'''Subject: changeset published

This changeset has been published.
''',
)


def _report_commit(ui, repo, ctx):
    domain = ui.config(b'notify_published', b'domain') or ui.config(
        b'notify', b'domain'
    )
    messageidseed = ui.config(
        b'notify_published', b'messageidseed'
    ) or ui.config(b'notify', b'messageidseed')
    template = ui.config(b'notify_published', b'template')
    spec = formatter.literal_templatespec(template)
    templater = logcmdutil.changesettemplater(ui, repo, spec)
    ui.pushbuffer()
    n = notify.notifier(ui, repo, b'incoming')

    subs = set()
    for sub, spec in n.subs:
        if spec is None:
            subs.add(sub)
            continue
        revs = repo.revs(b'%r and %d:', spec, ctx.rev())
        if len(revs):
            subs.add(sub)
            continue
    if len(subs) == 0:
        ui.debug(
            b'notify_published: no subscribers to selected repo and revset\n'
        )
        return

    templater.show(
        ctx,
        changes=ctx.changeset(),
        baseurl=ui.config(b'web', b'baseurl'),
        root=repo.root,
        webroot=n.root,
    )
    data = ui.popbuffer()

    try:
        msg = mail.parsebytes(data)
    except emailerrors.MessageParseError as inst:
        raise error.Abort(inst)

    msg['In-reply-to'] = notify.messageid(ctx, domain, messageidseed)
    msg['Message-Id'] = notify.messageid(
        ctx, domain, messageidseed + b'-published'
    )
    msg['Date'] = encoding.strfromlocal(
        dateutil.datestr(format=b"%a, %d %b %Y %H:%M:%S %1%2")
    )
    if not msg['From']:
        sender = ui.config(b'email', b'from') or ui.username()
        if b'@' not in sender or b'@localhost' in sender:
            sender = n.fixmail(sender)
        msg['From'] = mail.addressencode(ui, sender, n.charsets, n.test)
    msg['To'] = ', '.join(sorted(subs))

    msgtext = msg.as_bytes() if pycompat.ispy3 else msg.as_string()
    if ui.configbool(b'notify', b'test'):
        ui.write(msgtext)
        if not msgtext.endswith(b'\n'):
            ui.write(b'\n')
    else:
        ui.status(_(b'notify_published: sending mail for %d\n') % ctx.rev())
        mail.sendmail(
            ui, emailutils.parseaddr(msg['From'])[1], subs, msgtext, mbox=n.mbox
        )


def hook(ui, repo, hooktype, node=None, **kwargs):
    if hooktype != b"txnclose-phase":
        raise error.Abort(
            _(b'Unsupported hook type %r') % pycompat.bytestr(hooktype)
        )
    ctx = repo.unfiltered()[node]
    if kwargs['oldphase'] == b'draft' and kwargs['phase'] == b'public':
        _report_commit(ui, repo, ctx)
