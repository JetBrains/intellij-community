#!/usr/bin/env python

from mercurial import filemerge, ui, util
from mercurial.node import short
import sys, struct, socket

def monkeypatch_method(cls):
    def decorator(func):
        setattr(cls, func.__name__, func)
        return func
    return decorator

@monkeypatch_method(filemerge)
def filemerge(repo, mynode, orig, fcd, fco, fca):
    port = int(repo.ui.config( 'hg4ideafilemerge', 'port', None, True))
  
    repo.ui.debug( "hg4idea server waiting on port %s" % port )
  
    if not port:
        util.abort("No port was specified")

    def send( client, data ):
        length = struct.pack('>L', len(data))
        client.sendall( length )
        client.sendall( data)

    client = socket.socket( socket.AF_INET, socket.SOCK_STREAM )
    repo.ui.debug( "connecting ..." )
    try:
        client.connect( ('127.0.0.1', port) )
        repo.ui.debug( "connected, sending data ..." )
        send( client, fcd.data() )
        send( client, fco.data() )
        send( client, fca.data() )
        print client.recv(1024)
        return 1;
    except:
        util.abort( "Could not send data to hg4idea")
