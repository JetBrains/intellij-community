#!/usr/bin/env python

#Mercurial extension to robustly integrate prompts with other processes
#Copyright (C) 2010-2011 Willem Verstraeten
#
#This program is free software; you can redistribute it and/or
#modify it under the terms of the GNU General Public License
#as published by the Free Software Foundation; either version 2
#of the License, or (at your option) any later version.
#
#This program is distributed in the hope that it will be useful,
#but WITHOUT ANY WARRANTY; without even the implied warranty of
#MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
#GNU General Public License for more details.
#
#You should have received a copy of the GNU General Public License
#along with this program; if not, write to the Free Software
#Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston, MA  02110-1301, USA.

import socket
import struct
import sys
from mercurial import ui, util, error
from mercurial.i18n import _

try:
    from mercurial.url import passwordmgr
except:
    from mercurial.httprepo import passwordmgr

PY3 = sys.version_info[0] == 3

def sendInt(client, number):
    length = struct.pack('>L', number)
    client.sendall(length)

def send(client, data):
    if data is None:
        sendInt(client, 0)
    else:
        # we need to send data length and data together because it may produce read problems, see org.zmlx.hg4idea.execution.SocketServer
        client.sendall(struct.pack('>L', len(data)) + data.encode('utf-8'))
    
def receiveIntWithMessage(client, message):
    requiredLength = struct.calcsize('>L')
    buffer = ''.encode('utf-8')
    while len(buffer)<requiredLength:
        chunk = client.recv(requiredLength-len(buffer))
        if chunk == '':
            raise error.Abort(message)
        buffer = buffer + chunk
        
    # struct.unpack always returns a tuple, even if that tuple only contains a single
    # item. The trailing , is to destructure the tuple into its first element.
    intToReturn, = struct.unpack('>L', buffer)   
      
    return intToReturn
    
    
def receiveInt(client):
    return receiveIntWithMessage(client, "could not get information from server")

def receive(client):
    receiveWithMessage(client, "could not get information from server")
    
def receiveWithMessage(client, message):
    length = receiveIntWithMessage(client, message)
    buffer = ''.encode('utf-8')
    while len(buffer) < length :
        chunk = client.recv(length - len(buffer))
        if chunk == '':
            raise error.Abort(message)
        buffer = buffer + chunk
        
    return buffer

# decorator to cleanly monkey patch methods in mercurial
def monkeypatch_method(cls):
    def decorator(func):
        setattr(cls, func.__name__, func)
        return func
    return decorator

def sendchoicestoidea(ui, msg, choices, default):
    port = int(ui.config( b'hg4ideaprompt', b'port', None, True))

    if not port:
        raise error.Abort("No port was specified")
    if (type(choices) is int) and (type(msg) is str):
        # since Mercurial 2.7 the promptchoice method doesn't accept 'choices' as parameter, so we need to parse them from msg
        # see ui.py -> promptchoice(self, prompt, default=0)
        parts = msg.split('$$')
        msg = parts[0].rstrip(' ')
        choices = [p.strip(' ') for p in parts[1:]]

    numOfChoices = len(choices)
    if not numOfChoices:
        return default

    client = socket.socket(socket.AF_INET, socket.SOCK_STREAM)
    
    try:
        client.connect(('127.0.0.1', port))

        send(client, msg)
        sendInt(client, numOfChoices)
        for choice in choices:
            send(client, choice)
        sendInt(client, default)
    
        answer = receiveInt(client)
        if answer == -1:
            raise error.Abort("User cancelled")
        else:      
            return answer
    except:
        raise

@monkeypatch_method(ui.ui)
def promptchoice(self, msg, choices=None, default=0):
    return sendchoicestoidea(self, msg, choices, default)

original_warn = ui.ui.warn
@monkeypatch_method(ui.ui)
def warn(self, *msg):
    original_warn(self, *msg)
    hg4ideaWarnConfig = self.config(b'hg4ideawarn', b'port', None, True)
    if hg4ideaWarnConfig is None:
        return
    port = int(hg4ideaWarnConfig)
  
    if not port:
        raise error.Abort("No port was specified")

    self.debug( "hg4idea prompt server waiting on port %s" % port )

    client = socket.socket(socket.AF_INET, socket.SOCK_STREAM)
    
    self.debug("connecting ...")
    client.connect(('127.0.0.1', port))
    self.debug("connected, sending data ...")
    
    sendInt(client, len(msg))
    for message in msg:
        send(client, message)


def retrieve_pass_from_server(ui, uri,path, proposed_user):
    port = int(ui.config(b'hg4ideapass', b'port', None, True))
    if port is None:
        raise error.Abort("No port was specified")
    client = socket.socket(socket.AF_INET, socket.SOCK_STREAM)
    ui.debug("connecting ...")
    client.connect(('127.0.0.1', port))
    ui.debug("connected, sending data ...")
    
    send(client, "getpass")
    send(client, uri)
    send(client, path)
    send(client, proposed_user)
    user = receiveWithMessage(client, b"http authorization required")
    password = receiveWithMessage(client, b"http authorization required")
    return user, password


original_retrievepass=passwordmgr.find_user_password
@monkeypatch_method(passwordmgr)
def find_user_password(self, realm, authuri):
    try:
        return original_retrievepass(self, realm, authuri)
    except error.Abort:
        def read_hgrc_authtoken(ui, authuri):
            from mercurial.httpconnection import readauthforuri
            from inspect import getargspec
            args, _, _, _ = getargspec(readauthforuri)
            res = readauthforuri(self.ui, authuri, "")
            if res:
                group, auth = res
                return auth
            else:
                return None

        # After mercurial 3.8.3 urllib2.HTTPPasswordmgrwithdefaultrealm.find_user_password etc were changed to appropriate methods
        # in util.urlreq module with slightly different semantics
        #
        # Mercurial 5.2 started supporting python3, where urllib2 has been split into several modules
        if PY3:
            import urllib.request as urllib
        else:
            import urllib2 as urllib
        newMerc = False if isinstance(self, urllib.HTTPPasswordMgrWithDefaultRealm) else True
        if newMerc:
            user, password = util.urlreq.httppasswordmgrwithdefaultrealm().find_user_password(realm, authuri)
        else:
            user, password = urllib.HTTPPasswordMgrWithDefaultRealm.find_user_password(self, realm, authuri)
        if user is None:
            auth = read_hgrc_authtoken(self.ui, authuri)
            if auth:
                user = auth.get(b"username")

        pmWithRealm = util.urlreq.httppasswordmgrwithdefaultrealm() if newMerc else self
        reduced_uri, path = pmWithRealm.reduce_uri(authuri, False)
        retrievedPass = retrieve_pass_from_server(self.ui, reduced_uri, path, user)
        if retrievedPass is None:
            raise error.Abort(_('http authorization required'))
        user, passwd = retrievedPass
        pmWithRealm.add_password(realm, authuri, user, passwd)
        return retrievedPass