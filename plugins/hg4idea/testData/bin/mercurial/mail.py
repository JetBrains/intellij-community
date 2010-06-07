# mail.py - mail sending bits for mercurial
#
# Copyright 2006 Matt Mackall <mpm@selenic.com>
#
# This software may be used and distributed according to the terms of the
# GNU General Public License version 2 or any later version.

from i18n import _
import util, encoding
import os, smtplib, socket, quopri
import email.Header, email.MIMEText, email.Utils

def _smtp(ui):
    '''build an smtp connection and return a function to send mail'''
    local_hostname = ui.config('smtp', 'local_hostname')
    s = smtplib.SMTP(local_hostname=local_hostname)
    mailhost = ui.config('smtp', 'host')
    if not mailhost:
        raise util.Abort(_('no [smtp]host in hgrc - cannot send mail'))
    mailport = int(ui.config('smtp', 'port', 25))
    ui.note(_('sending mail: smtp host %s, port %s\n') %
            (mailhost, mailport))
    s.connect(host=mailhost, port=mailport)
    if ui.configbool('smtp', 'tls'):
        if not hasattr(socket, 'ssl'):
            raise util.Abort(_("can't use TLS: Python SSL support "
                               "not installed"))
        ui.note(_('(using tls)\n'))
        s.ehlo()
        s.starttls()
        s.ehlo()
    username = ui.config('smtp', 'username')
    password = ui.config('smtp', 'password')
    if username and not password:
        password = ui.getpass()
    if username and password:
        ui.note(_('(authenticating to mail server as %s)\n') %
                  (username))
        try:
            s.login(username, password)
        except smtplib.SMTPException, inst:
            raise util.Abort(inst)

    def send(sender, recipients, msg):
        try:
            return s.sendmail(sender, recipients, msg)
        except smtplib.SMTPRecipientsRefused, inst:
            recipients = [r[1] for r in inst.recipients.values()]
            raise util.Abort('\n' + '\n'.join(recipients))
        except smtplib.SMTPException, inst:
            raise util.Abort(inst)

    return send

def _sendmail(ui, sender, recipients, msg):
    '''send mail using sendmail.'''
    program = ui.config('email', 'method')
    cmdline = '%s -f %s %s' % (program, util.email(sender),
                               ' '.join(map(util.email, recipients)))
    ui.note(_('sending mail: %s\n') % cmdline)
    fp = util.popen(cmdline, 'w')
    fp.write(msg)
    ret = fp.close()
    if ret:
        raise util.Abort('%s %s' % (
            os.path.basename(program.split(None, 1)[0]),
            util.explain_exit(ret)[0]))

def connect(ui):
    '''make a mail connection. return a function to send mail.
    call as sendmail(sender, list-of-recipients, msg).'''
    if ui.config('email', 'method', 'smtp') == 'smtp':
        return _smtp(ui)
    return lambda s, r, m: _sendmail(ui, s, r, m)

def sendmail(ui, sender, recipients, msg):
    send = connect(ui)
    return send(sender, recipients, msg)

def validateconfig(ui):
    '''determine if we have enough config data to try sending email.'''
    method = ui.config('email', 'method', 'smtp')
    if method == 'smtp':
        if not ui.config('smtp', 'host'):
            raise util.Abort(_('smtp specified as email transport, '
                               'but no smtp host configured'))
    else:
        if not util.find_exe(method):
            raise util.Abort(_('%r specified as email transport, '
                               'but not in PATH') % method)

def mimetextpatch(s, subtype='plain', display=False):
    '''If patch in utf-8 transfer-encode it.'''

    enc = None
    for line in s.splitlines():
        if len(line) > 950:
            s = quopri.encodestring(s)
            enc = "quoted-printable"
            break

    cs = 'us-ascii'
    if not display:
        try:
            s.decode('us-ascii')
        except UnicodeDecodeError:
            try:
                s.decode('utf-8')
                cs = 'utf-8'
            except UnicodeDecodeError:
                # We'll go with us-ascii as a fallback.
                pass

    msg = email.MIMEText.MIMEText(s, subtype, cs)
    if enc:
        del msg['Content-Transfer-Encoding']
        msg['Content-Transfer-Encoding'] = enc
    return msg

def _charsets(ui):
    '''Obtains charsets to send mail parts not containing patches.'''
    charsets = [cs.lower() for cs in ui.configlist('email', 'charsets')]
    fallbacks = [encoding.fallbackencoding.lower(),
                 encoding.encoding.lower(), 'utf-8']
    for cs in fallbacks: # find unique charsets while keeping order
        if cs not in charsets:
            charsets.append(cs)
    return [cs for cs in charsets if not cs.endswith('ascii')]

def _encode(ui, s, charsets):
    '''Returns (converted) string, charset tuple.
    Finds out best charset by cycling through sendcharsets in descending
    order. Tries both encoding and fallbackencoding for input. Only as
    last resort send as is in fake ascii.
    Caveat: Do not use for mail parts containing patches!'''
    try:
        s.decode('ascii')
    except UnicodeDecodeError:
        sendcharsets = charsets or _charsets(ui)
        for ics in (encoding.encoding, encoding.fallbackencoding):
            try:
                u = s.decode(ics)
            except UnicodeDecodeError:
                continue
            for ocs in sendcharsets:
                try:
                    return u.encode(ocs), ocs
                except UnicodeEncodeError:
                    pass
                except LookupError:
                    ui.warn(_('ignoring invalid sendcharset: %s\n') % ocs)
    # if ascii, or all conversion attempts fail, send (broken) ascii
    return s, 'us-ascii'

def headencode(ui, s, charsets=None, display=False):
    '''Returns RFC-2047 compliant header from given string.'''
    if not display:
        # split into words?
        s, cs = _encode(ui, s, charsets)
        return str(email.Header.Header(s, cs))
    return s

def _addressencode(ui, name, addr, charsets=None):
    name = headencode(ui, name, charsets)
    try:
        acc, dom = addr.split('@')
        acc = acc.encode('ascii')
        dom = dom.decode(encoding.encoding).encode('idna')
        addr = '%s@%s' % (acc, dom)
    except UnicodeDecodeError:
        raise util.Abort(_('invalid email address: %s') % addr)
    except ValueError:
        try:
            # too strict?
            addr = addr.encode('ascii')
        except UnicodeDecodeError:
            raise util.Abort(_('invalid local address: %s') % addr)
    return email.Utils.formataddr((name, addr))

def addressencode(ui, address, charsets=None, display=False):
    '''Turns address into RFC-2047 compliant header.'''
    if display or not address:
        return address or ''
    name, addr = email.Utils.parseaddr(address)
    return _addressencode(ui, name, addr, charsets)

def addrlistencode(ui, addrs, charsets=None, display=False):
    '''Turns a list of addresses into a list of RFC-2047 compliant headers.
    A single element of input list may contain multiple addresses, but output
    always has one address per item'''
    if display:
        return [a.strip() for a in addrs if a.strip()]

    result = []
    for name, addr in email.Utils.getaddresses(addrs):
        if name or addr:
            result.append(_addressencode(ui, name, addr, charsets))
    return result

def mimeencode(ui, s, charsets=None, display=False):
    '''creates mime text object, encodes it if needed, and sets
    charset and transfer-encoding accordingly.'''
    cs = 'us-ascii'
    if not display:
        s, cs = _encode(ui, s, charsets)
    return email.MIMEText.MIMEText(s, 'plain', cs)
