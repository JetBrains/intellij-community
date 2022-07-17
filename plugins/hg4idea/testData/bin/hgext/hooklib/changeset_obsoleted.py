# Copyright 2020 Joerg Sonnenberger <joerg@bec.de>
#
# This software may be used and distributed according to the terms of the
# GNU General Public License version 2 or any later version.
"""changeset_obsoleted is a hook to send a mail when an
existing draft changeset is obsoleted by an obsmarker without successor.

Correct message threading requires the same messageidseed to be used for both
the original notification and the new mail.

Usage:
  [notify]
  messageidseed = myseed

  [hooks]
  txnclose.changeset_obsoleted = \
    python:hgext.hooklib.changeset_obsoleted.hook
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
    obsutil,
    pycompat,
    registrar,
)
from mercurial.utils import dateutil
from .. import notify

configtable = {}
configitem = registrar.configitem(configtable)

configitem(
    b'notify_obsoleted',
    b'domain',
    default=None,
)
configitem(
    b'notify_obsoleted',
    b'messageidseed',
    default=None,
)
configitem(
    b'notify_obsoleted',
    b'template',
    default=b'''Subject: changeset abandoned

This changeset has been abandoned.
''',
)


def _report_commit(ui, repo, ctx):
    domain = ui.config(b'notify_obsoleted', b'domain') or ui.config(
        b'notify', b'domain'
    )
    messageidseed = ui.config(
        b'notify_obsoleted', b'messageidseed'
    ) or ui.config(b'notify', b'messageidseed')
    template = ui.config(b'notify_obsoleted', b'template')
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
            b'notify_obsoleted: no subscribers to selected repo and revset\n'
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
        ctx, domain, messageidseed + b'-obsoleted'
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
        ui.status(_(b'notify_obsoleted: sending mail for %d\n') % ctx.rev())
        mail.sendmail(
            ui, emailutils.parseaddr(msg['From'])[1], subs, msgtext, mbox=n.mbox
        )


def has_successor(repo, rev):
    return any(
        r for r in obsutil.allsuccessors(repo.obsstore, [rev]) if r != rev
    )


def hook(ui, repo, hooktype, node=None, **kwargs):
    if hooktype != b"txnclose":
        raise error.Abort(
            _(b'Unsupported hook type %r') % pycompat.bytestr(hooktype)
        )
    for rev in obsutil.getobsoleted(repo, changes=kwargs['changes']):
        ctx = repo.unfiltered()[rev]
        if not has_successor(repo, ctx.node()):
            _report_commit(ui, repo, ctx)
