# notify.py - email notifications for mercurial
#
# Copyright 2006 Vadim Gelfer <vadim.gelfer@gmail.com>
#
# This software may be used and distributed according to the terms of the
# GNU General Public License version 2 or any later version.

'''hooks for sending email notifications at commit/push time

Subscriptions can be managed through a hgrc file. Default mode is to
print messages to stdout, for testing and configuring.

To use, configure the notify extension and enable it in hgrc like
this::

  [extensions]
  notify =

  [hooks]
  # one email for each incoming changeset
  incoming.notify = python:hgext.notify.hook
  # batch emails when many changesets incoming at one time
  changegroup.notify = python:hgext.notify.hook

  [notify]
  # config items go here

Required configuration items::

  config = /path/to/file # file containing subscriptions

Optional configuration items::

  test = True            # print messages to stdout for testing
  strip = 3              # number of slashes to strip for url paths
  domain = example.com   # domain to use if committer missing domain
  style = ...            # style file to use when formatting email
  template = ...         # template to use when formatting email
  incoming = ...         # template to use when run as incoming hook
  changegroup = ...      # template when run as changegroup hook
  maxdiff = 300          # max lines of diffs to include (0=none, -1=all)
  maxsubject = 67        # truncate subject line longer than this
  diffstat = True        # add a diffstat before the diff content
  sources = serve        # notify if source of incoming changes in this list
                         # (serve == ssh or http, push, pull, bundle)
  merge = False          # send notification for merges (default True)
  [email]
  from = user@host.com   # email address to send as if none given
  [web]
  baseurl = http://hgserver/... # root of hg web site for browsing commits

The notify config file has same format as a regular hgrc file. It has
two sections so you can express subscriptions in whatever way is
handier for you.

::

  [usersubs]
  # key is subscriber email, value is ","-separated list of glob patterns
  user@host = pattern

  [reposubs]
  # key is glob pattern, value is ","-separated list of subscriber emails
  pattern = user@host

Glob patterns are matched against path to repository root.

If you like, you can put notify config file in repository that users
can push changes to, they can manage their own subscriptions.
'''

from mercurial.i18n import _
from mercurial import patch, cmdutil, templater, util, mail
import email.Parser, email.Errors, fnmatch, socket, time

# template for single changeset can include email headers.
single_template = '''
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
multiple_template = '''
changeset {node|short} in {root}
details: {baseurl}{webroot}?cmd=changeset;node={node|short}
summary: {desc|firstline}
'''

deftemplates = {
    'changegroup': multiple_template,
}

class notifier(object):
    '''email notification class.'''

    def __init__(self, ui, repo, hooktype):
        self.ui = ui
        cfg = self.ui.config('notify', 'config')
        if cfg:
            self.ui.readconfig(cfg, sections=['usersubs', 'reposubs'])
        self.repo = repo
        self.stripcount = int(self.ui.config('notify', 'strip', 0))
        self.root = self.strip(self.repo.root)
        self.domain = self.ui.config('notify', 'domain')
        self.test = self.ui.configbool('notify', 'test', True)
        self.charsets = mail._charsets(self.ui)
        self.subs = self.subscribers()
        self.merge = self.ui.configbool('notify', 'merge', True)

        mapfile = self.ui.config('notify', 'style')
        template = (self.ui.config('notify', hooktype) or
                    self.ui.config('notify', 'template'))
        self.t = cmdutil.changeset_templater(self.ui, self.repo,
                                             False, None, mapfile, False)
        if not mapfile and not template:
            template = deftemplates.get(hooktype) or single_template
        if template:
            template = templater.parsestring(template, quoted=False)
            self.t.use_template(template)

    def strip(self, path):
        '''strip leading slashes from local path, turn into web-safe path.'''

        path = util.pconvert(path)
        count = self.stripcount
        while count > 0:
            c = path.find('/')
            if c == -1:
                break
            path = path[c + 1:]
            count -= 1
        return path

    def fixmail(self, addr):
        '''try to clean up email addresses.'''

        addr = util.email(addr.strip())
        if self.domain:
            a = addr.find('@localhost')
            if a != -1:
                addr = addr[:a]
            if '@' not in addr:
                return addr + '@' + self.domain
        return addr

    def subscribers(self):
        '''return list of email addresses of subscribers to this repo.'''
        subs = set()
        for user, pats in self.ui.configitems('usersubs'):
            for pat in pats.split(','):
                if fnmatch.fnmatch(self.repo.root, pat.strip()):
                    subs.add(self.fixmail(user))
        for pat, users in self.ui.configitems('reposubs'):
            if fnmatch.fnmatch(self.repo.root, pat):
                for user in users.split(','):
                    subs.add(self.fixmail(user))
        return [mail.addressencode(self.ui, s, self.charsets, self.test)
                for s in sorted(subs)]

    def url(self, path=None):
        return self.ui.config('web', 'baseurl') + (path or self.root)

    def node(self, ctx, **props):
        '''format one changeset, unless it is a suppressed merge.'''
        if not self.merge and len(ctx.parents()) > 1:
            return False
        self.t.show(ctx, changes=ctx.changeset(),
                    baseurl=self.ui.config('web', 'baseurl'),
                    root=self.repo.root, webroot=self.root, **props)
        return True

    def skipsource(self, source):
        '''true if incoming changes from this source should be skipped.'''
        ok_sources = self.ui.config('notify', 'sources', 'serve').split()
        return source not in ok_sources

    def send(self, ctx, count, data):
        '''send message.'''

        p = email.Parser.Parser()
        try:
            msg = p.parsestr(data)
        except email.Errors.MessageParseError, inst:
            raise util.Abort(inst)

        # store sender and subject
        sender, subject = msg['From'], msg['Subject']
        del msg['From'], msg['Subject']

        if not msg.is_multipart():
            # create fresh mime message from scratch
            # (multipart templates must take care of this themselves)
            headers = msg.items()
            payload = msg.get_payload()
            # for notification prefer readability over data precision
            msg = mail.mimeencode(self.ui, payload, self.charsets, self.test)
            # reinstate custom headers
            for k, v in headers:
                msg[k] = v

        msg['Date'] = util.datestr(format="%a, %d %b %Y %H:%M:%S %1%2")

        # try to make subject line exist and be useful
        if not subject:
            if count > 1:
                subject = _('%s: %d new changesets') % (self.root, count)
            else:
                s = ctx.description().lstrip().split('\n', 1)[0].rstrip()
                subject = '%s: %s' % (self.root, s)
        maxsubject = int(self.ui.config('notify', 'maxsubject', 67))
        if maxsubject and len(subject) > maxsubject:
            subject = subject[:maxsubject - 3] + '...'
        msg['Subject'] = mail.headencode(self.ui, subject,
                                         self.charsets, self.test)

        # try to make message have proper sender
        if not sender:
            sender = self.ui.config('email', 'from') or self.ui.username()
        if '@' not in sender or '@localhost' in sender:
            sender = self.fixmail(sender)
        msg['From'] = mail.addressencode(self.ui, sender,
                                         self.charsets, self.test)

        msg['X-Hg-Notification'] = 'changeset %s' % ctx
        if not msg['Message-Id']:
            msg['Message-Id'] = ('<hg.%s.%s.%s@%s>' %
                                 (ctx, int(time.time()),
                                  hash(self.repo.root), socket.getfqdn()))
        msg['To'] = ', '.join(self.subs)

        msgtext = msg.as_string()
        if self.test:
            self.ui.write(msgtext)
            if not msgtext.endswith('\n'):
                self.ui.write('\n')
        else:
            self.ui.status(_('notify: sending %d subscribers %d changes\n') %
                           (len(self.subs), count))
            mail.sendmail(self.ui, util.email(msg['From']),
                          self.subs, msgtext)

    def diff(self, ctx, ref=None):

        maxdiff = int(self.ui.config('notify', 'maxdiff', 300))
        prev = ctx.parents()[0].node()
        ref = ref and ref.node() or ctx.node()
        chunks = patch.diff(self.repo, prev, ref, opts=patch.diffopts(self.ui))
        difflines = ''.join(chunks).splitlines()

        if self.ui.configbool('notify', 'diffstat', True):
            s = patch.diffstat(difflines)
            # s may be nil, don't include the header if it is
            if s:
                self.ui.write('\ndiffstat:\n\n%s' % s)

        if maxdiff == 0:
            return
        elif maxdiff > 0 and len(difflines) > maxdiff:
            msg = _('\ndiffs (truncated from %d to %d lines):\n\n')
            self.ui.write(msg % (len(difflines), maxdiff))
            difflines = difflines[:maxdiff]
        elif difflines:
            self.ui.write(_('\ndiffs (%d lines):\n\n') % len(difflines))

        self.ui.write("\n".join(difflines))

def hook(ui, repo, hooktype, node=None, source=None, **kwargs):
    '''send email notifications to interested subscribers.

    if used as changegroup hook, send one email for all changesets in
    changegroup. else send one email per changeset.'''

    n = notifier(ui, repo, hooktype)
    ctx = repo[node]

    if not n.subs:
        ui.debug('notify: no subscribers to repository %s\n' % n.root)
        return
    if n.skipsource(source):
        ui.debug('notify: changes have source "%s" - skipping\n' % source)
        return

    ui.pushbuffer()
    data = ''
    count = 0
    if hooktype == 'changegroup':
        start, end = ctx.rev(), len(repo)
        for rev in xrange(start, end):
            if n.node(repo[rev]):
                count += 1
            else:
                data += ui.popbuffer()
                ui.note(_('notify: suppressing notification for merge %d:%s\n') %
                        (rev, repo[rev].hex()[:12]))
                ui.pushbuffer()
        if count:
            n.diff(ctx, repo['tip'])
    else:
        if not n.node(ctx):
            ui.popbuffer()
            ui.note(_('notify: suppressing notification for merge %d:%s\n') %
                    (ctx.rev(), ctx.hex()[:12]))
            return
        count += 1
        n.diff(ctx)

    data += ui.popbuffer()
    if count:
        n.send(ctx, count, data)
