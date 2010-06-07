# zeroconf.py - zeroconf support for Mercurial
#
# Copyright 2005-2007 Matt Mackall <mpm@selenic.com>
#
# This software may be used and distributed according to the terms of the
# GNU General Public License version 2 or any later version.

'''discover and advertise repositories on the local network

Zeroconf enabled repositories will be announced in a network without
the need to configure a server or a service. They can be discovered
without knowing their actual IP address.

To allow other people to discover your repository using run "hg serve"
in your repository::

  $ cd test
  $ hg serve

You can discover zeroconf enabled repositories by running "hg paths"::

  $ hg paths
  zc-test = http://example.com:8000/test
'''

import Zeroconf, socket, time, os
from mercurial import ui, hg, encoding
from mercurial import extensions
from mercurial.hgweb import hgweb_mod
from mercurial.hgweb import hgwebdir_mod

# publish

server = None
localip = None

def getip():
    # finds external-facing interface without sending any packets (Linux)
    try:
        s = socket.socket(socket.AF_INET, socket.SOCK_DGRAM)
        s.connect(('1.0.0.1', 0))
        ip = s.getsockname()[0]
        return ip
    except:
        pass

    # Generic method, sometimes gives useless results
    try:
        dumbip = socket.gethostbyaddr(socket.gethostname())[2][0]
        if not dumbip.startswith('127.') and ':' not in dumbip:
            return dumbip
    except (socket.gaierror, socket.herror):
        dumbip = '127.0.0.1'

    # works elsewhere, but actually sends a packet
    try:
        s = socket.socket(socket.AF_INET, socket.SOCK_DGRAM)
        s.connect(('1.0.0.1', 1))
        ip = s.getsockname()[0]
        return ip
    except:
        pass

    return dumbip

def publish(name, desc, path, port):
    global server, localip
    if not server:
        ip = getip()
        if ip.startswith('127.'):
            # if we have no internet connection, this can happen.
            return
        localip = socket.inet_aton(ip)
        server = Zeroconf.Zeroconf(ip)

    hostname = socket.gethostname().split('.')[0]
    host = hostname + ".local"
    name = "%s-%s" % (hostname, name)

    # advertise to browsers
    svc = Zeroconf.ServiceInfo('_http._tcp.local.',
                               name + '._http._tcp.local.',
                               server = host,
                               port = port,
                               properties = {'description': desc,
                                             'path': "/" + path},
                               address = localip, weight = 0, priority = 0)
    server.registerService(svc)

    # advertise to Mercurial clients
    svc = Zeroconf.ServiceInfo('_hg._tcp.local.',
                               name + '._hg._tcp.local.',
                               server = host,
                               port = port,
                               properties = {'description': desc,
                                             'path': "/" + path},
                               address = localip, weight = 0, priority = 0)
    server.registerService(svc)

class hgwebzc(hgweb_mod.hgweb):
    def __init__(self, repo, name=None):
        super(hgwebzc, self).__init__(repo, name)
        name = self.reponame or os.path.basename(repo.root)
        path = self.repo.ui.config("web", "prefix", "").strip('/')
        desc = self.repo.ui.config("web", "description", name)
        publish(name, desc, path, int(repo.ui.config("web", "port", 8000)))

class hgwebdirzc(hgwebdir_mod.hgwebdir):
    def __init__(self, conf, baseui=None):
        super(hgwebdirzc, self).__init__(conf, baseui)
        prefix = self.ui.config("web", "prefix", "").strip('/') + '/'
        for repo, path in self.repos:
            u = self.ui.copy()
            u.readconfig(os.path.join(path, '.hg', 'hgrc'))
            name = os.path.basename(repo)
            path = (prefix + repo).strip('/')
            desc = u.config('web', 'description', name)
            publish(name, desc, path, int(u.config("web", "port", 8000)))

# listen

class listener(object):
    def __init__(self):
        self.found = {}
    def removeService(self, server, type, name):
        if repr(name) in self.found:
            del self.found[repr(name)]
    def addService(self, server, type, name):
        self.found[repr(name)] = server.getServiceInfo(type, name)

def getzcpaths():
    ip = getip()
    if ip.startswith('127.'):
        return
    server = Zeroconf.Zeroconf(ip)
    l = listener()
    Zeroconf.ServiceBrowser(server, "_hg._tcp.local.", l)
    time.sleep(1)
    server.close()
    for value in l.found.values():
        name = value.name[:value.name.index('.')]
        url = "http://%s:%s%s" % (socket.inet_ntoa(value.address), value.port,
                                  value.properties.get("path", "/"))
        yield "zc-" + name, url

def config(orig, self, section, key, default=None, untrusted=False):
    if section == "paths" and key.startswith("zc-"):
        for name, path in getzcpaths():
            if name == key:
                return path
    return orig(self, section, key, default, untrusted)

def configitems(orig, self, section, untrusted=False):
    repos = orig(self, section, untrusted)
    if section == "paths":
        repos += getzcpaths()
    return repos

def defaultdest(orig, source):
    for name, path in getzcpaths():
        if path == source:
            return name.encode(encoding.encoding)
    return orig(source)

extensions.wrapfunction(ui.ui, 'config', config)
extensions.wrapfunction(ui.ui, 'configitems', configitems)
extensions.wrapfunction(hg, 'defaultdest', defaultdest)
hgweb_mod.hgweb = hgwebzc
hgwebdir_mod.hgwebdir = hgwebdirzc
