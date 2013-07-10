# sslutil.py - SSL handling for mercurial
#
# Copyright 2005, 2006, 2007, 2008 Matt Mackall <mpm@selenic.com>
# Copyright 2006, 2007 Alexis S. L. Carvalho <alexis@cecm.usp.br>
# Copyright 2006 Vadim Gelfer <vadim.gelfer@gmail.com>
#
# This software may be used and distributed according to the terms of the
# GNU General Public License version 2 or any later version.
import os

from mercurial import util
from mercurial.i18n import _
try:
    # avoid using deprecated/broken FakeSocket in python 2.6
    import ssl
    CERT_REQUIRED = ssl.CERT_REQUIRED
    def ssl_wrap_socket(sock, keyfile, certfile,
                cert_reqs=ssl.CERT_NONE, ca_certs=None):
        sslsocket = ssl.wrap_socket(sock, keyfile, certfile,
                cert_reqs=cert_reqs, ca_certs=ca_certs)
        # check if wrap_socket failed silently because socket had been closed
        # - see http://bugs.python.org/issue13721
        if not sslsocket.cipher():
            raise util.Abort(_('ssl connection failed'))
        return sslsocket
except ImportError:
    CERT_REQUIRED = 2

    import socket, httplib

    def ssl_wrap_socket(sock, key_file, cert_file,
                        cert_reqs=CERT_REQUIRED, ca_certs=None):
        if not util.safehasattr(socket, 'ssl'):
            raise util.Abort(_('Python SSL support not found'))
        if ca_certs:
            raise util.Abort(_(
                'certificate checking requires Python 2.6'))

        ssl = socket.ssl(sock, key_file, cert_file)
        return httplib.FakeSocket(sock, ssl)

def _verifycert(cert, hostname):
    '''Verify that cert (in socket.getpeercert() format) matches hostname.
    CRLs is not handled.

    Returns error message if any problems are found and None on success.
    '''
    if not cert:
        return _('no certificate received')
    dnsname = hostname.lower()
    def matchdnsname(certname):
        return (certname == dnsname or
                '.' in dnsname and certname == '*.' + dnsname.split('.', 1)[1])

    san = cert.get('subjectAltName', [])
    if san:
        certnames = [value.lower() for key, value in san if key == 'DNS']
        for name in certnames:
            if matchdnsname(name):
                return None
        if certnames:
            return _('certificate is for %s') % ', '.join(certnames)

    # subject is only checked when subjectAltName is empty
    for s in cert.get('subject', []):
        key, value = s[0]
        if key == 'commonName':
            try:
                # 'subject' entries are unicode
                certname = value.lower().encode('ascii')
            except UnicodeEncodeError:
                return _('IDN in certificate not supported')
            if matchdnsname(certname):
                return None
            return _('certificate is for %s') % certname
    return _('no commonName or subjectAltName found in certificate')


# CERT_REQUIRED means fetch the cert from the server all the time AND
# validate it against the CA store provided in web.cacerts.
#
# We COMPLETELY ignore CERT_REQUIRED on Python <= 2.5, as it's totally
# busted on those versions.

def sslkwargs(ui, host):
    cacerts = ui.config('web', 'cacerts')
    hostfingerprint = ui.config('hostfingerprints', host)
    if cacerts and not hostfingerprint:
        cacerts = util.expandpath(cacerts)
        if not os.path.exists(cacerts):
            raise util.Abort(_('could not find web.cacerts: %s') % cacerts)
        return {'ca_certs': cacerts,
                'cert_reqs': CERT_REQUIRED,
                }
    return {}

class validator(object):
    def __init__(self, ui, host):
        self.ui = ui
        self.host = host

    def __call__(self, sock, strict=False):
        host = self.host
        cacerts = self.ui.config('web', 'cacerts')
        hostfingerprint = self.ui.config('hostfingerprints', host)
        if not getattr(sock, 'getpeercert', False): # python 2.5 ?
            if hostfingerprint:
                raise util.Abort(_("host fingerprint for %s can't be "
                                   "verified (Python too old)") % host)
            if strict:
                raise util.Abort(_("certificate for %s can't be verified "
                                   "(Python too old)") % host)
            if self.ui.configbool('ui', 'reportoldssl', True):
                self.ui.warn(_("warning: certificate for %s can't be verified "
                               "(Python too old)\n") % host)
            return

        if not sock.cipher(): # work around http://bugs.python.org/issue13721
            raise util.Abort(_('%s ssl connection error') % host)
        try:
            peercert = sock.getpeercert(True)
            peercert2 = sock.getpeercert()
        except AttributeError:
            raise util.Abort(_('%s ssl connection error') % host)

        if not peercert:
            raise util.Abort(_('%s certificate error: '
                               'no certificate received') % host)
        peerfingerprint = util.sha1(peercert).hexdigest()
        nicefingerprint = ":".join([peerfingerprint[x:x + 2]
            for x in xrange(0, len(peerfingerprint), 2)])
        if hostfingerprint:
            if peerfingerprint.lower() != \
                    hostfingerprint.replace(':', '').lower():
                raise util.Abort(_('certificate for %s has unexpected '
                                   'fingerprint %s') % (host, nicefingerprint),
                                 hint=_('check hostfingerprint configuration'))
            self.ui.debug('%s certificate matched fingerprint %s\n' %
                          (host, nicefingerprint))
        elif cacerts:
            msg = _verifycert(peercert2, host)
            if msg:
                raise util.Abort(_('%s certificate error: %s') % (host, msg),
                                 hint=_('configure hostfingerprint %s or use '
                                        '--insecure to connect insecurely') %
                                      nicefingerprint)
            self.ui.debug('%s certificate successfully verified\n' % host)
        elif strict:
            raise util.Abort(_('%s certificate with fingerprint %s not '
                               'verified') % (host, nicefingerprint),
                             hint=_('check hostfingerprints or web.cacerts '
                                     'config setting'))
        else:
            self.ui.warn(_('warning: %s certificate with fingerprint %s not '
                           'verified (check hostfingerprints or web.cacerts '
                           'config setting)\n') %
                         (host, nicefingerprint))
