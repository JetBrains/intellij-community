#!/usr/bin/env python

from mercurial import filemerge, ui, util, dispatch
from mercurial.node import short
import sys, struct, socket

def sendInt( client, number):
    length = struct.pack('>L', number)
    client.sendall( length )

def send( client, data ):
    sendInt(client, len(data))
    client.sendall( data )
    
def receiveInt(client):
    requiredLength = struct.calcsize('>L')
    buffer = ''
    while len(buffer)<requiredLength:
        chunk = client.recv(requiredLength-len(buffer))
        if chunk == '':
            raise RuntimeError, "socket connection broken"
        buffer = buffer + chunk
        
    # struct.unpack always returns a tuple, even if that tuple only contains a single
    # item. The trailing , is to destructure the tuple into its first element.
    intToReturn, = struct.unpack('>L', buffer)   
      
    return intToReturn
    
def receive( client ):
    length = receiveInt(client)
    buffer = ''
    while len(buffer) < length :
        chunk = client.recv(length - len(buffer))
        if chunk == '':
            raise RuntimeError, "socket connection broken"
        buffer = buffer+chunk
        
    return buffer

# decorator to cleanly monkey patch methods in mercurial
def monkeypatch_method(cls):
    def decorator(func):
        setattr(cls, func.__name__, func)
        return func
    return decorator

def sendchangestoidea(ui, msg, choices, default):
    port = int(ui.config( 'hg4ideaprompt', 'port', None, True))
  
    if not port:
        raise util.Abort("No port was specified")

    numOfChoices = len(choices)
    if numOfChoices == 0:
        return default

    client = socket.socket( socket.AF_INET, socket.SOCK_STREAM )
    
    try:
        client.connect( ('127.0.0.1', port) )

        send( client, msg )
        sendInt( client, numOfChoices )
        for choice in choices:
            send( client, choice )
        sendInt( client, default )
    
        answer = receiveInt( client )
        print "Received answer: %s" % answer
        if answer == -1:
            raise util.Abort("User cancelled")
        else:      
            return answer
    except:
        raise

# determine which method to monkey patch : 
# in Mercurial 1.4 the prompt method was renamed to promptchoice
if getattr(ui.ui, 'promptchoice', None):
    @monkeypatch_method(ui.ui)
    def promptchoice(self, msg, choices=None, default=0):
        return sendchangestoidea(self, msg, choices, default)
else:
    @monkeypatch_method(ui.ui)
    def prompt(self, msg, choices=None, default="y"):
        resps = [s[s.index('&')+1].lower() for s in choices]
        defaultIndex = resps.index( default )
        responseIndex = sendchangestoidea( self, msg, choices, defaultIndex)
        return resps[responseIndex]

original_warn = ui.ui.warn
@monkeypatch_method(ui.ui)
def warn(self, *msg):
    original_warn(self, *msg)
    
    port = int(self.config( 'hg4ideawarn', 'port', None, True))
  
    if not port:
        raise util.Abort("No port was specified")

    self.debug( "hg4idea prompt server waiting on port %s" % port )

    client = socket.socket( socket.AF_INET, socket.SOCK_STREAM )
    
    self.debug( "connecting ..." )
    client.connect( ('127.0.0.1', port) )
    self.debug( "connected, sending data ..." )
    
    sendInt( client, len(msg) )
    for message in msg:
        send( client, message )
