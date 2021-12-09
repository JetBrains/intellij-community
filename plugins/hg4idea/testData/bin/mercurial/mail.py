# mail.py - mail sending bits for mercurial
#
# Copyright 2006 Olivia Mackall <olivia@selenic.com>
#
# This software may be used and distributed according to the terms of the
# GNU General Public License version 2 or any later version.

from __future__ import absolute_import

import email
import email.charset
import email.generator
import email.header
import email.message
import email.parser
import io
import os
import smtplib
import socket
import time

from .i18n import _
from .pycompat import (
    getattr,
    open,
)
from . import (
    encoding,
    error,
    pycompat,
    sslutil,
    util,
)
from .utils import (
    procutil,
    stringutil,
    urlutil,
)

if pycompat.TYPE_CHECKING:
    from typing import Any, List, Tuple, Union

    # keep pyflakes happy
    assert all((Any, List, Tuple, Union))


class STARTTLS(smtplib.SMTP):
    """Derived class to verify the peer certificate for STARTTLS.

    This class allows to pass any keyword arguments to SSL socket creation.
    """

    def __init__(self, ui, host=None, **kwargs):
        smtplib.SMTP.__init__(self, **kwargs)
        self._ui = ui
        self._host = host

    def starttls(self, keyfile=None, certfile=None):
        if not self.has_extn("starttls"):
            msg = b"STARTTLS extension not supported by server"
            raise smtplib.SMTPException(msg)
        (resp, reply) = self.docmd("STARTTLS")
        if resp == 220:
            self.sock = sslutil.wrapsocket(
                self.sock,
                keyfile,
                certfile,
                ui=self._ui,
                serverhostname=self._host,
            )
            self.file = self.sock.makefile("rb")
            self.helo_resp = None
            self.ehlo_resp = None
            self.esmtp_features = {}
            self.does_esmtp = 0
        return (resp, reply)


class SMTPS(smtplib.SMTP):
    """Derived class to verify the peer certificate for SMTPS.

    This class allows to pass any keyword arguments to SSL socket creation.
    """

    def __init__(self, ui, keyfile=None, certfile=None, host=None, **kwargs):
        self.keyfile = keyfile
        self.certfile = certfile
        smtplib.SMTP.__init__(self, **kwargs)
        self._host = host
        self.default_port = smtplib.SMTP_SSL_PORT
        self._ui = ui

    def _get_socket(self, host, port, timeout):
        if self.debuglevel > 0:
            self._ui.debug(b'connect: %r\n' % ((host, port),))
        new_socket = socket.create_connection((host, port), timeout)
        new_socket = sslutil.wrapsocket(
            new_socket,
            self.keyfile,
            self.certfile,
            ui=self._ui,
            serverhostname=self._host,
        )
        self.file = new_socket.makefile('rb')
        return new_socket


def _pyhastls():
    # type: () -> bool
    """Returns true iff Python has TLS support, false otherwise."""
    try:
        import ssl

        getattr(ssl, 'HAS_TLS', False)
        return True
    except ImportError:
        return False


def _smtp(ui):
    '''build an smtp connection and return a function to send mail'''
    local_hostname = ui.config(b'smtp', b'local_hostname')
    tls = ui.config(b'smtp', b'tls')
    # backward compatible: when tls = true, we use starttls.
    starttls = tls == b'starttls' or stringutil.parsebool(tls)
    smtps = tls == b'smtps'
    if (starttls or smtps) and not _pyhastls():
        raise error.Abort(_(b"can't use TLS: Python SSL support not installed"))
    mailhost = ui.config(b'smtp', b'host')
    if not mailhost:
        raise error.Abort(_(b'smtp.host not configured - cannot send mail'))
    if smtps:
        ui.note(_(b'(using smtps)\n'))
        s = SMTPS(ui, local_hostname=local_hostname, host=mailhost)
    elif starttls:
        s = STARTTLS(ui, local_hostname=local_hostname, host=mailhost)
    else:
        s = smtplib.SMTP(local_hostname=local_hostname)
    if smtps:
        defaultport = 465
    else:
        defaultport = 25
    mailport = urlutil.getport(ui.config(b'smtp', b'port', defaultport))
    ui.note(_(b'sending mail: smtp host %s, port %d\n') % (mailhost, mailport))
    s.connect(host=mailhost, port=mailport)
    if starttls:
        ui.note(_(b'(using starttls)\n'))
        s.ehlo()
        s.starttls()
        s.ehlo()
    if starttls or smtps:
        ui.note(_(b'(verifying remote certificate)\n'))
        sslutil.validatesocket(s.sock)

    try:
        _smtp_login(ui, s, mailhost, mailport)
    except smtplib.SMTPException as inst:
        raise error.Abort(stringutil.forcebytestr(inst))

    def send(sender, recipients, msg):
        try:
            return s.sendmail(sender, recipients, msg)
        except smtplib.SMTPRecipientsRefused as inst:
            recipients = [r[1] for r in inst.recipients.values()]
            raise error.Abort(b'\n' + b'\n'.join(recipients))
        except smtplib.SMTPException as inst:
            raise error.Abort(stringutil.forcebytestr(inst))

    return send


def _smtp_login(ui, smtp, mailhost, mailport):
    """A hook for the keyring extension to perform the actual SMTP login.

    An already connected SMTP object of the proper type is provided, based on
    the current configuration.  The host and port to which the connection was
    established are provided for accessibility, since the SMTP object doesn't
    provide an accessor.  ``smtplib.SMTPException`` is raised on error.
    """
    username = ui.config(b'smtp', b'username')
    password = ui.config(b'smtp', b'password')
    if username:
        if password:
            password = encoding.strfromlocal(password)
        else:
            password = ui.getpass()
            if password is not None:
                password = encoding.strfromlocal(password)
    if username and password:
        ui.note(_(b'(authenticating to mail server as %s)\n') % username)
        username = encoding.strfromlocal(username)
        smtp.login(username, password)


def _sendmail(ui, sender, recipients, msg):
    '''send mail using sendmail.'''
    program = ui.config(b'email', b'method')

    def stremail(x):
        return procutil.shellquote(stringutil.email(encoding.strtolocal(x)))

    cmdline = b'%s -f %s %s' % (
        program,
        stremail(sender),
        b' '.join(map(stremail, recipients)),
    )
    ui.note(_(b'sending mail: %s\n') % cmdline)
    fp = procutil.popen(cmdline, b'wb')
    fp.write(util.tonativeeol(msg))
    ret = fp.close()
    if ret:
        raise error.Abort(
            b'%s %s'
            % (
                os.path.basename(procutil.shellsplit(program)[0]),
                procutil.explainexit(ret),
            )
        )


def _mbox(mbox, sender, recipients, msg):
    '''write mails to mbox'''
    # TODO: use python mbox library for proper locking
    with open(mbox, b'ab+') as fp:
        # Should be time.asctime(), but Windows prints 2-characters day
        # of month instead of one. Make them print the same thing.
        date = time.strftime('%a %b %d %H:%M:%S %Y', time.localtime())
        fp.write(
            b'From %s %s\n'
            % (encoding.strtolocal(sender), encoding.strtolocal(date))
        )
        fp.write(msg)
        fp.write(b'\n\n')


def connect(ui, mbox=None):
    """make a mail connection. return a function to send mail.
    call as sendmail(sender, list-of-recipients, msg)."""
    if mbox:
        open(mbox, b'wb').close()
        return lambda s, r, m: _mbox(mbox, s, r, m)
    if ui.config(b'email', b'method') == b'smtp':
        return _smtp(ui)
    return lambda s, r, m: _sendmail(ui, s, r, m)


def sendmail(ui, sender, recipients, msg, mbox=None):
    send = connect(ui, mbox=mbox)
    return send(sender, recipients, msg)


def validateconfig(ui):
    '''determine if we have enough config data to try sending email.'''
    method = ui.config(b'email', b'method')
    if method == b'smtp':
        if not ui.config(b'smtp', b'host'):
            raise error.Abort(
                _(
                    b'smtp specified as email transport, '
                    b'but no smtp host configured'
                )
            )
    else:
        if not procutil.findexe(method):
            raise error.Abort(
                _(b'%r specified as email transport, but not in PATH') % method
            )


def codec2iana(cs):
    # type: (str) -> str
    ''' '''
    cs = email.charset.Charset(cs).input_charset.lower()

    # "latin1" normalizes to "iso8859-1", standard calls for "iso-8859-1"
    if cs.startswith("iso") and not cs.startswith("iso-"):
        return "iso-" + cs[3:]
    return cs


def mimetextpatch(s, subtype='plain', display=False):
    # type: (bytes, str, bool) -> email.message.Message
    """Return MIME message suitable for a patch.
    Charset will be detected by first trying to decode as us-ascii, then utf-8,
    and finally the global encodings. If all those fail, fall back to
    ISO-8859-1, an encoding with that allows all byte sequences.
    Transfer encodings will be used if necessary."""

    cs = [
        'us-ascii',
        'utf-8',
        pycompat.sysstr(encoding.encoding),
        pycompat.sysstr(encoding.fallbackencoding),
    ]
    if display:
        cs = ['us-ascii']
    for charset in cs:
        try:
            s.decode(charset)
            return mimetextqp(s, subtype, codec2iana(charset))
        except UnicodeDecodeError:
            pass

    return mimetextqp(s, subtype, "iso-8859-1")


def mimetextqp(body, subtype, charset):
    # type: (bytes, str, str) -> email.message.Message
    """Return MIME message.
    Quoted-printable transfer encoding will be used if necessary.
    """
    cs = email.charset.Charset(charset)
    msg = email.message.Message()
    msg.set_type('text/' + subtype)

    for line in body.splitlines():
        if len(line) > 950:
            cs.body_encoding = email.charset.QP
            break

    # On Python 2, this simply assigns a value. Python 3 inspects
    # body and does different things depending on whether it has
    # encode() or decode() attributes. We can get the old behavior
    # if we pass a str and charset is None and we call set_charset().
    # But we may get into  trouble later due to Python attempting to
    # encode/decode using the registered charset (or attempting to
    # use ascii in the absence of a charset).
    msg.set_payload(body, cs)

    return msg


def _charsets(ui):
    # type: (Any) -> List[str]
    '''Obtains charsets to send mail parts not containing patches.'''
    charsets = [
        pycompat.sysstr(cs.lower())
        for cs in ui.configlist(b'email', b'charsets')
    ]
    fallbacks = [
        pycompat.sysstr(encoding.fallbackencoding.lower()),
        pycompat.sysstr(encoding.encoding.lower()),
        'utf-8',
    ]
    for cs in fallbacks:  # find unique charsets while keeping order
        if cs not in charsets:
            charsets.append(cs)
    return [cs for cs in charsets if not cs.endswith('ascii')]


def _encode(ui, s, charsets):
    # type: (Any, bytes, List[str]) -> Tuple[bytes, str]
    """Returns (converted) string, charset tuple.
    Finds out best charset by cycling through sendcharsets in descending
    order. Tries both encoding and fallbackencoding for input. Only as
    last resort send as is in fake ascii.
    Caveat: Do not use for mail parts containing patches!"""
    sendcharsets = charsets or _charsets(ui)
    if not isinstance(s, bytes):
        # We have unicode data, which we need to try and encode to
        # some reasonable-ish encoding. Try the encodings the user
        # wants, and fall back to garbage-in-ascii.
        for ocs in sendcharsets:
            try:
                return s.encode(ocs), ocs
            except UnicodeEncodeError:
                pass
            except LookupError:
                ui.warn(
                    _(b'ignoring invalid sendcharset: %s\n')
                    % pycompat.sysbytes(ocs)
                )
        else:
            # Everything failed, ascii-armor what we've got and send it.
            return s.encode('ascii', 'backslashreplace'), 'us-ascii'
    # We have a bytes of unknown encoding. We'll try and guess a valid
    # encoding, falling back to pretending we had ascii even though we
    # know that's wrong.
    try:
        s.decode('ascii')
    except UnicodeDecodeError:
        for ics in (encoding.encoding, encoding.fallbackencoding):
            ics = pycompat.sysstr(ics)
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
                    ui.warn(
                        _(b'ignoring invalid sendcharset: %s\n')
                        % pycompat.sysbytes(ocs)
                    )
    # if ascii, or all conversion attempts fail, send (broken) ascii
    return s, 'us-ascii'


def headencode(ui, s, charsets=None, display=False):
    # type: (Any, Union[bytes, str], List[str], bool) -> str
    '''Returns RFC-2047 compliant header from given string.'''
    if not display:
        # split into words?
        s, cs = _encode(ui, s, charsets)
        return email.header.Header(s, cs).encode()
    return encoding.strfromlocal(s)


def _addressencode(ui, name, addr, charsets=None):
    # type: (Any, str, str, List[str]) -> str
    addr = encoding.strtolocal(addr)
    name = headencode(ui, name, charsets)
    try:
        acc, dom = addr.split(b'@')
        acc.decode('ascii')
        dom = dom.decode(pycompat.sysstr(encoding.encoding)).encode('idna')
        addr = b'%s@%s' % (acc, dom)
    except UnicodeDecodeError:
        raise error.Abort(_(b'invalid email address: %s') % addr)
    except ValueError:
        try:
            # too strict?
            addr.decode('ascii')
        except UnicodeDecodeError:
            raise error.Abort(_(b'invalid local address: %s') % addr)
    return email.utils.formataddr((name, encoding.strfromlocal(addr)))


def addressencode(ui, address, charsets=None, display=False):
    # type: (Any, bytes, List[str], bool) -> str
    '''Turns address into RFC-2047 compliant header.'''
    if display or not address:
        return encoding.strfromlocal(address or b'')
    name, addr = email.utils.parseaddr(encoding.strfromlocal(address))
    return _addressencode(ui, name, addr, charsets)


def addrlistencode(ui, addrs, charsets=None, display=False):
    # type: (Any, List[bytes], List[str], bool) -> List[str]
    """Turns a list of addresses into a list of RFC-2047 compliant headers.
    A single element of input list may contain multiple addresses, but output
    always has one address per item"""
    straddrs = []
    for a in addrs:
        assert isinstance(a, bytes), '%r unexpectedly not a bytestr' % a
        straddrs.append(encoding.strfromlocal(a))
    if display:
        return [a.strip() for a in straddrs if a.strip()]

    result = []
    for name, addr in email.utils.getaddresses(straddrs):
        if name or addr:
            r = _addressencode(ui, name, addr, charsets)
            result.append(r)
    return result


def mimeencode(ui, s, charsets=None, display=False):
    # type: (Any, bytes, List[str], bool) -> email.message.Message
    """creates mime text object, encodes it if needed, and sets
    charset and transfer-encoding accordingly."""
    cs = 'us-ascii'
    if not display:
        s, cs = _encode(ui, s, charsets)
    return mimetextqp(s, 'plain', cs)


if pycompat.ispy3:

    Generator = email.generator.BytesGenerator

    def parse(fp):
        # type: (Any) -> email.message.Message
        ep = email.parser.Parser()
        # disable the "universal newlines" mode, which isn't binary safe.
        # I have no idea if ascii/surrogateescape is correct, but that's
        # what the standard Python email parser does.
        fp = io.TextIOWrapper(
            fp, encoding='ascii', errors='surrogateescape', newline=chr(10)
        )
        try:
            return ep.parse(fp)
        finally:
            fp.detach()

    def parsebytes(data):
        # type: (bytes) -> email.message.Message
        ep = email.parser.BytesParser()
        return ep.parsebytes(data)


else:

    Generator = email.generator.Generator

    def parse(fp):
        # type: (Any) -> email.message.Message
        ep = email.parser.Parser()
        return ep.parse(fp)

    def parsebytes(data):
        # type: (str) -> email.message.Message
        ep = email.parser.Parser()
        return ep.parsestr(data)


def headdecode(s):
    # type: (Union[email.header.Header, bytes]) -> bytes
    '''Decodes RFC-2047 header'''
    uparts = []
    for part, charset in email.header.decode_header(s):
        if charset is not None:
            try:
                uparts.append(part.decode(charset))
                continue
            except (UnicodeDecodeError, LookupError):
                pass
        # On Python 3, decode_header() may return either bytes or unicode
        # depending on whether the header has =?<charset>? or not
        if isinstance(part, type(u'')):
            uparts.append(part)
            continue
        try:
            uparts.append(part.decode('UTF-8'))
            continue
        except UnicodeDecodeError:
            pass
        uparts.append(part.decode('ISO-8859-1'))
    return encoding.unitolocal(u' '.join(uparts))
