# mail.py - mail sending bits for mercurial
#
# Copyright 2006 Matt Mackall <mpm@selenic.com>
#
# This software may be used and distributed according to the terms of the
# GNU General Public License version 2 or any later version.

from i18n import _
import util, encoding, sslutil
import os, smtplib, socket, quopri, time, sys
import email.Header, email.MIMEText, email.Utils

_oldheaderinit = email.Header.Header.__init__
def _unifiedheaderinit(self, *args, **kw):
    """
    Python 2.7 introduces a backwards incompatible change
    (Python issue1974, r70772) in email.Generator.Generator code:
    pre-2.7 code passed "continuation_ws='\t'" to the Header
    constructor, and 2.7 removed this parameter.

    Default argument is continuation_ws=' ', which means that the
    behaviour is different in <2.7 and 2.7

    We consider the 2.7 behaviour to be preferable, but need
    to have an unified behaviour for versions 2.4 to 2.7
    """
    # override continuation_ws
    kw['continuation_ws'] = ' '
    _oldheaderinit(self, *args, **kw)

email.Header.Header.__dict__['__init__'] = _unifiedheaderinit

class STARTTLS(smtplib.SMTP):
    '''Derived class to verify the peer certificate for STARTTLS.

    This class allows to pass any keyword arguments to SSL socket creation.
    '''
    def __init__(self, sslkwargs, **kwargs):
        smtplib.SMTP.__init__(self, **kwargs)
        self._sslkwargs = sslkwargs

    def starttls(self, keyfile=None, certfile=None):
        if not self.has_extn("starttls"):
            msg = "STARTTLS extension not supported by server"
            raise smtplib.SMTPException(msg)
        (resp, reply) = self.docmd("STARTTLS")
        if resp == 220:
            self.sock = sslutil.ssl_wrap_socket(self.sock, keyfile, certfile,
                                                **self._sslkwargs)
            if not util.safehasattr(self.sock, "read"):
                # using httplib.FakeSocket with Python 2.5.x or earlier
                self.sock.read = self.sock.recv
            self.file = smtplib.SSLFakeFile(self.sock)
            self.helo_resp = None
            self.ehlo_resp = None
            self.esmtp_features = {}
            self.does_esmtp = 0
        return (resp, reply)

if util.safehasattr(smtplib.SMTP, '_get_socket'):
    class SMTPS(smtplib.SMTP):
        '''Derived class to verify the peer certificate for SMTPS.

        This class allows to pass any keyword arguments to SSL socket creation.
        '''
        def __init__(self, sslkwargs, keyfile=None, certfile=None, **kwargs):
            self.keyfile = keyfile
            self.certfile = certfile
            smtplib.SMTP.__init__(self, **kwargs)
            self.default_port = smtplib.SMTP_SSL_PORT
            self._sslkwargs = sslkwargs

        def _get_socket(self, host, port, timeout):
            if self.debuglevel > 0:
                print >> sys.stderr, 'connect:', (host, port)
            new_socket = socket.create_connection((host, port), timeout)
            new_socket = sslutil.ssl_wrap_socket(new_socket,
                                                 self.keyfile, self.certfile,
                                                 **self._sslkwargs)
            self.file = smtplib.SSLFakeFile(new_socket)
            return new_socket
else:
    def SMTPS(sslkwargs, keyfile=None, certfile=None, **kwargs):
        raise util.Abort(_('SMTPS requires Python 2.6 or later'))

def _smtp(ui):
    '''build an smtp connection and return a function to send mail'''
    local_hostname = ui.config('smtp', 'local_hostname')
    tls = ui.config('smtp', 'tls', 'none')
    # backward compatible: when tls = true, we use starttls.
    starttls = tls == 'starttls' or util.parsebool(tls)
    smtps = tls == 'smtps'
    if (starttls or smtps) and not util.safehasattr(socket, 'ssl'):
        raise util.Abort(_("can't use TLS: Python SSL support not installed"))
    mailhost = ui.config('smtp', 'host')
    if not mailhost:
        raise util.Abort(_('smtp.host not configured - cannot send mail'))
    verifycert = ui.config('smtp', 'verifycert', 'strict')
    if verifycert not in ['strict', 'loose']:
        if util.parsebool(verifycert) is not False:
            raise util.Abort(_('invalid smtp.verifycert configuration: %s')
                             % (verifycert))
    if (starttls or smtps) and verifycert:
        sslkwargs = sslutil.sslkwargs(ui, mailhost)
    else:
        sslkwargs = {}
    if smtps:
        ui.note(_('(using smtps)\n'))
        s = SMTPS(sslkwargs, local_hostname=local_hostname)
    elif starttls:
        s = STARTTLS(sslkwargs, local_hostname=local_hostname)
    else:
        s = smtplib.SMTP(local_hostname=local_hostname)
    if smtps:
        defaultport = 465
    else:
        defaultport = 25
    mailport = util.getport(ui.config('smtp', 'port', defaultport))
    ui.note(_('sending mail: smtp host %s, port %s\n') %
            (mailhost, mailport))
    s.connect(host=mailhost, port=mailport)
    if starttls:
        ui.note(_('(using starttls)\n'))
        s.ehlo()
        s.starttls()
        s.ehlo()
    if (starttls or smtps) and verifycert:
        ui.note(_('(verifying remote certificate)\n'))
        sslutil.validator(ui, mailhost)(s.sock, verifycert == 'strict')
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
            util.explainexit(ret)[0]))

def _mbox(mbox, sender, recipients, msg):
    '''write mails to mbox'''
    fp = open(mbox, 'ab+')
    # Should be time.asctime(), but Windows prints 2-characters day
    # of month instead of one. Make them print the same thing.
    date = time.strftime('%a %b %d %H:%M:%S %Y', time.localtime())
    fp.write('From %s %s\n' % (sender, date))
    fp.write(msg)
    fp.write('\n\n')
    fp.close()

def connect(ui, mbox=None):
    '''make a mail connection. return a function to send mail.
    call as sendmail(sender, list-of-recipients, msg).'''
    if mbox:
        open(mbox, 'wb').close()
        return lambda s, r, m: _mbox(mbox, s, r, m)
    if ui.config('email', 'method', 'smtp') == 'smtp':
        return _smtp(ui)
    return lambda s, r, m: _sendmail(ui, s, r, m)

def sendmail(ui, sender, recipients, msg, mbox=None):
    send = connect(ui, mbox=mbox)
    return send(sender, recipients, msg)

def validateconfig(ui):
    '''determine if we have enough config data to try sending email.'''
    method = ui.config('email', 'method', 'smtp')
    if method == 'smtp':
        if not ui.config('smtp', 'host'):
            raise util.Abort(_('smtp specified as email transport, '
                               'but no smtp host configured'))
    else:
        if not util.findexe(method):
            raise util.Abort(_('%r specified as email transport, '
                               'but not in PATH') % method)

def mimetextpatch(s, subtype='plain', display=False):
    '''Return MIME message suitable for a patch.
    Charset will be detected as utf-8 or (possibly fake) us-ascii.
    Transfer encodings will be used if necessary.'''

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

    return mimetextqp(s, subtype, cs)

def mimetextqp(body, subtype, charset):
    '''Return MIME message.
    Quoted-printable transfer encoding will be used if necessary.
    '''
    enc = None
    for line in body.splitlines():
        if len(line) > 950:
            body = quopri.encodestring(body)
            enc = "quoted-printable"
            break

    msg = email.MIMEText.MIMEText(body, subtype, charset)
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
    return mimetextqp(s, 'plain', cs)
