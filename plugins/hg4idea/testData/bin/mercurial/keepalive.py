#   This library is free software; you can redistribute it and/or
#   modify it under the terms of the GNU Lesser General Public
#   License as published by the Free Software Foundation; either
#   version 2.1 of the License, or (at your option) any later version.
#
#   This library is distributed in the hope that it will be useful,
#   but WITHOUT ANY WARRANTY; without even the implied warranty of
#   MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
#   Lesser General Public License for more details.
#
#   You should have received a copy of the GNU Lesser General Public
#   License along with this library; if not, write to the
#      Free Software Foundation, Inc.,
#      59 Temple Place, Suite 330,
#      Boston, MA  02111-1307  USA

# This file is part of urlgrabber, a high-level cross-protocol url-grabber
# Copyright 2002-2004 Michael D. Stenner, Ryan Tomayko

# Modified by Benoit Boissinot:
#  - fix for digest auth (inspired from urllib2.py @ Python v2.4)
# Modified by Dirkjan Ochtman:
#  - import md5 function from a local util module
# Modified by Martin Geisler:
#  - moved md5 function from local util module to this module
# Modified by Augie Fackler:
#  - add safesend method and use it to prevent broken pipe errors
#    on large POST requests

"""An HTTP handler for urllib2 that supports HTTP 1.1 and keepalive.

>>> import urllib2
>>> from keepalive import HTTPHandler
>>> keepalive_handler = HTTPHandler()
>>> opener = urllib2.build_opener(keepalive_handler)
>>> urllib2.install_opener(opener)
>>>
>>> fo = urllib2.urlopen('http://www.python.org')

If a connection to a given host is requested, and all of the existing
connections are still in use, another connection will be opened.  If
the handler tries to use an existing connection but it fails in some
way, it will be closed and removed from the pool.

To remove the handler, simply re-run build_opener with no arguments, and
install that opener.

You can explicitly close connections by using the close_connection()
method of the returned file-like object (described below) or you can
use the handler methods:

  close_connection(host)
  close_all()
  open_connections()

NOTE: using the close_connection and close_all methods of the handler
should be done with care when using multiple threads.
  * there is nothing that prevents another thread from creating new
    connections immediately after connections are closed
  * no checks are done to prevent in-use connections from being closed

>>> keepalive_handler.close_all()

EXTRA ATTRIBUTES AND METHODS

  Upon a status of 200, the object returned has a few additional
  attributes and methods, which should not be used if you want to
  remain consistent with the normal urllib2-returned objects:

    close_connection()  -  close the connection to the host
    readlines()         -  you know, readlines()
    status              -  the return status (ie 404)
    reason              -  english translation of status (ie 'File not found')

  If you want the best of both worlds, use this inside an
  AttributeError-catching try:

  >>> try: status = fo.status
  >>> except AttributeError: status = None

  Unfortunately, these are ONLY there if status == 200, so it's not
  easy to distinguish between non-200 responses.  The reason is that
  urllib2 tries to do clever things with error codes 301, 302, 401,
  and 407, and it wraps the object upon return.

  For python versions earlier than 2.4, you can avoid this fancy error
  handling by setting the module-level global HANDLE_ERRORS to zero.
  You see, prior to 2.4, it's the HTTP Handler's job to determine what
  to handle specially, and what to just pass up.  HANDLE_ERRORS == 0
  means "pass everything up".  In python 2.4, however, this job no
  longer belongs to the HTTP Handler and is now done by a NEW handler,
  HTTPErrorProcessor.  Here's the bottom line:

    python version < 2.4
        HANDLE_ERRORS == 1  (default) pass up 200, treat the rest as
                            errors
        HANDLE_ERRORS == 0  pass everything up, error processing is
                            left to the calling code
    python version >= 2.4
        HANDLE_ERRORS == 1  pass up 200, treat the rest as errors
        HANDLE_ERRORS == 0  (default) pass everything up, let the
                            other handlers (specifically,
                            HTTPErrorProcessor) decide what to do

  In practice, setting the variable either way makes little difference
  in python 2.4, so for the most consistent behavior across versions,
  you probably just want to use the defaults, which will give you
  exceptions on errors.

"""

# $Id: keepalive.py,v 1.14 2006/04/04 21:00:32 mstenner Exp $

import errno
import httplib
import socket
import thread
import urllib2

DEBUG = None

import sys
if sys.version_info < (2, 4):
    HANDLE_ERRORS = 1
else: HANDLE_ERRORS = 0

class ConnectionManager:
    """
    The connection manager must be able to:
      * keep track of all existing
      """
    def __init__(self):
        self._lock = thread.allocate_lock()
        self._hostmap = {} # map hosts to a list of connections
        self._connmap = {} # map connections to host
        self._readymap = {} # map connection to ready state

    def add(self, host, connection, ready):
        self._lock.acquire()
        try:
            if not host in self._hostmap:
                self._hostmap[host] = []
            self._hostmap[host].append(connection)
            self._connmap[connection] = host
            self._readymap[connection] = ready
        finally:
            self._lock.release()

    def remove(self, connection):
        self._lock.acquire()
        try:
            try:
                host = self._connmap[connection]
            except KeyError:
                pass
            else:
                del self._connmap[connection]
                del self._readymap[connection]
                self._hostmap[host].remove(connection)
                if not self._hostmap[host]: del self._hostmap[host]
        finally:
            self._lock.release()

    def set_ready(self, connection, ready):
        try:
            self._readymap[connection] = ready
        except KeyError:
            pass

    def get_ready_conn(self, host):
        conn = None
        self._lock.acquire()
        try:
            if host in self._hostmap:
                for c in self._hostmap[host]:
                    if self._readymap[c]:
                        self._readymap[c] = 0
                        conn = c
                        break
        finally:
            self._lock.release()
        return conn

    def get_all(self, host=None):
        if host:
            return list(self._hostmap.get(host, []))
        else:
            return dict(self._hostmap)

class KeepAliveHandler:
    def __init__(self):
        self._cm = ConnectionManager()

    #### Connection Management
    def open_connections(self):
        """return a list of connected hosts and the number of connections
        to each.  [('foo.com:80', 2), ('bar.org', 1)]"""
        return [(host, len(li)) for (host, li) in self._cm.get_all().items()]

    def close_connection(self, host):
        """close connection(s) to <host>
        host is the host:port spec, as in 'www.cnn.com:8080' as passed in.
        no error occurs if there is no connection to that host."""
        for h in self._cm.get_all(host):
            self._cm.remove(h)
            h.close()

    def close_all(self):
        """close all open connections"""
        for host, conns in self._cm.get_all().iteritems():
            for h in conns:
                self._cm.remove(h)
                h.close()

    def _request_closed(self, request, host, connection):
        """tells us that this request is now closed and the the
        connection is ready for another request"""
        self._cm.set_ready(connection, 1)

    def _remove_connection(self, host, connection, close=0):
        if close:
            connection.close()
        self._cm.remove(connection)

    #### Transaction Execution
    def http_open(self, req):
        return self.do_open(HTTPConnection, req)

    def do_open(self, http_class, req):
        host = req.get_host()
        if not host:
            raise urllib2.URLError('no host given')

        try:
            h = self._cm.get_ready_conn(host)
            while h:
                r = self._reuse_connection(h, req, host)

                # if this response is non-None, then it worked and we're
                # done.  Break out, skipping the else block.
                if r:
                    break

                # connection is bad - possibly closed by server
                # discard it and ask for the next free connection
                h.close()
                self._cm.remove(h)
                h = self._cm.get_ready_conn(host)
            else:
                # no (working) free connections were found.  Create a new one.
                h = http_class(host)
                if DEBUG:
                    DEBUG.info("creating new connection to %s (%d)",
                               host, id(h))
                self._cm.add(host, h, 0)
                self._start_transaction(h, req)
                r = h.getresponse()
        except (socket.error, httplib.HTTPException), err:
            raise urllib2.URLError(err)

        # if not a persistent connection, don't try to reuse it
        if r.will_close:
            self._cm.remove(h)

        if DEBUG:
            DEBUG.info("STATUS: %s, %s", r.status, r.reason)
        r._handler = self
        r._host = host
        r._url = req.get_full_url()
        r._connection = h
        r.code = r.status
        r.headers = r.msg
        r.msg = r.reason

        if r.status == 200 or not HANDLE_ERRORS:
            return r
        else:
            return self.parent.error('http', req, r,
                                     r.status, r.msg, r.headers)

    def _reuse_connection(self, h, req, host):
        """start the transaction with a re-used connection
        return a response object (r) upon success or None on failure.
        This DOES not close or remove bad connections in cases where
        it returns.  However, if an unexpected exception occurs, it
        will close and remove the connection before re-raising.
        """
        try:
            self._start_transaction(h, req)
            r = h.getresponse()
            # note: just because we got something back doesn't mean it
            # worked.  We'll check the version below, too.
        except (socket.error, httplib.HTTPException):
            r = None
        except:
            # adding this block just in case we've missed
            # something we will still raise the exception, but
            # lets try and close the connection and remove it
            # first.  We previously got into a nasty loop
            # where an exception was uncaught, and so the
            # connection stayed open.  On the next try, the
            # same exception was raised, etc.  The tradeoff is
            # that it's now possible this call will raise
            # a DIFFERENT exception
            if DEBUG:
                DEBUG.error("unexpected exception - closing "
                            "connection to %s (%d)", host, id(h))
            self._cm.remove(h)
            h.close()
            raise

        if r is None or r.version == 9:
            # httplib falls back to assuming HTTP 0.9 if it gets a
            # bad header back.  This is most likely to happen if
            # the socket has been closed by the server since we
            # last used the connection.
            if DEBUG:
                DEBUG.info("failed to re-use connection to %s (%d)",
                           host, id(h))
            r = None
        else:
            if DEBUG:
                DEBUG.info("re-using connection to %s (%d)", host, id(h))

        return r

    def _start_transaction(self, h, req):
        # What follows mostly reimplements HTTPConnection.request()
        # except it adds self.parent.addheaders in the mix.
        headers = req.headers.copy()
        if sys.version_info >= (2, 4):
            headers.update(req.unredirected_hdrs)
        headers.update(self.parent.addheaders)
        headers = dict((n.lower(), v) for n, v in headers.items())
        skipheaders = {}
        for n in ('host', 'accept-encoding'):
            if n in headers:
                skipheaders['skip_' + n.replace('-', '_')] = 1
        try:
            if req.has_data():
                data = req.get_data()
                h.putrequest('POST', req.get_selector(), **skipheaders)
                if 'content-type' not in headers:
                    h.putheader('Content-type',
                                'application/x-www-form-urlencoded')
                if 'content-length' not in headers:
                    h.putheader('Content-length', '%d' % len(data))
            else:
                h.putrequest('GET', req.get_selector(), **skipheaders)
        except (socket.error), err:
            raise urllib2.URLError(err)
        for k, v in headers.items():
            h.putheader(k, v)
        h.endheaders()
        if req.has_data():
            h.send(data)

class HTTPHandler(KeepAliveHandler, urllib2.HTTPHandler):
    pass

class HTTPResponse(httplib.HTTPResponse):
    # we need to subclass HTTPResponse in order to
    # 1) add readline() and readlines() methods
    # 2) add close_connection() methods
    # 3) add info() and geturl() methods

    # in order to add readline(), read must be modified to deal with a
    # buffer.  example: readline must read a buffer and then spit back
    # one line at a time.  The only real alternative is to read one
    # BYTE at a time (ick).  Once something has been read, it can't be
    # put back (ok, maybe it can, but that's even uglier than this),
    # so if you THEN do a normal read, you must first take stuff from
    # the buffer.

    # the read method wraps the original to accomodate buffering,
    # although read() never adds to the buffer.
    # Both readline and readlines have been stolen with almost no
    # modification from socket.py


    def __init__(self, sock, debuglevel=0, strict=0, method=None):
        if method: # the httplib in python 2.3 uses the method arg
            httplib.HTTPResponse.__init__(self, sock, debuglevel, method)
        else: # 2.2 doesn't
            httplib.HTTPResponse.__init__(self, sock, debuglevel)
        self.fileno = sock.fileno
        self.code = None
        self._rbuf = ''
        self._rbufsize = 8096
        self._handler = None # inserted by the handler later
        self._host = None    # (same)
        self._url = None     # (same)
        self._connection = None # (same)

    _raw_read = httplib.HTTPResponse.read

    def close(self):
        if self.fp:
            self.fp.close()
            self.fp = None
            if self._handler:
                self._handler._request_closed(self, self._host,
                                              self._connection)

    def close_connection(self):
        self._handler._remove_connection(self._host, self._connection, close=1)
        self.close()

    def info(self):
        return self.headers

    def geturl(self):
        return self._url

    def read(self, amt=None):
        # the _rbuf test is only in this first if for speed.  It's not
        # logically necessary
        if self._rbuf and not amt is None:
            L = len(self._rbuf)
            if amt > L:
                amt -= L
            else:
                s = self._rbuf[:amt]
                self._rbuf = self._rbuf[amt:]
                return s

        s = self._rbuf + self._raw_read(amt)
        self._rbuf = ''
        return s

    # stolen from Python SVN #68532 to fix issue1088
    def _read_chunked(self, amt):
        chunk_left = self.chunk_left
        value = ''

        # XXX This accumulates chunks by repeated string concatenation,
        # which is not efficient as the number or size of chunks gets big.
        while True:
            if chunk_left is None:
                line = self.fp.readline()
                i = line.find(';')
                if i >= 0:
                    line = line[:i] # strip chunk-extensions
                try:
                    chunk_left = int(line, 16)
                except ValueError:
                    # close the connection as protocol synchronisation is
                    # probably lost
                    self.close()
                    raise httplib.IncompleteRead(value)
                if chunk_left == 0:
                    break
            if amt is None:
                value += self._safe_read(chunk_left)
            elif amt < chunk_left:
                value += self._safe_read(amt)
                self.chunk_left = chunk_left - amt
                return value
            elif amt == chunk_left:
                value += self._safe_read(amt)
                self._safe_read(2)  # toss the CRLF at the end of the chunk
                self.chunk_left = None
                return value
            else:
                value += self._safe_read(chunk_left)
                amt -= chunk_left

            # we read the whole chunk, get another
            self._safe_read(2)      # toss the CRLF at the end of the chunk
            chunk_left = None

        # read and discard trailer up to the CRLF terminator
        ### note: we shouldn't have any trailers!
        while True:
            line = self.fp.readline()
            if not line:
                # a vanishingly small number of sites EOF without
                # sending the trailer
                break
            if line == '\r\n':
                break

        # we read everything; close the "file"
        self.close()

        return value

    def readline(self, limit=-1):
        i = self._rbuf.find('\n')
        while i < 0 and not (0 < limit <= len(self._rbuf)):
            new = self._raw_read(self._rbufsize)
            if not new:
                break
            i = new.find('\n')
            if i >= 0:
                i = i + len(self._rbuf)
            self._rbuf = self._rbuf + new
        if i < 0:
            i = len(self._rbuf)
        else:
            i = i + 1
        if 0 <= limit < len(self._rbuf):
            i = limit
        data, self._rbuf = self._rbuf[:i], self._rbuf[i:]
        return data

    def readlines(self, sizehint = 0):
        total = 0
        list = []
        while 1:
            line = self.readline()
            if not line:
                break
            list.append(line)
            total += len(line)
            if sizehint and total >= sizehint:
                break
        return list

def safesend(self, str):
    """Send `str' to the server.

    Shamelessly ripped off from httplib to patch a bad behavior.
    """
    # _broken_pipe_resp is an attribute we set in this function
    # if the socket is closed while we're sending data but
    # the server sent us a response before hanging up.
    # In that case, we want to pretend to send the rest of the
    # outgoing data, and then let the user use getresponse()
    # (which we wrap) to get this last response before
    # opening a new socket.
    if getattr(self, '_broken_pipe_resp', None) is not None:
        return

    if self.sock is None:
        if self.auto_open:
            self.connect()
        else:
            raise httplib.NotConnected()

    # send the data to the server. if we get a broken pipe, then close
    # the socket. we want to reconnect when somebody tries to send again.
    #
    # NOTE: we DO propagate the error, though, because we cannot simply
    #       ignore the error... the caller will know if they can retry.
    if self.debuglevel > 0:
        print "send:", repr(str)
    try:
        blocksize = 8192
        if hasattr(str,'read') :
            if self.debuglevel > 0:
                print "sendIng a read()able"
            data = str.read(blocksize)
            while data:
                self.sock.sendall(data)
                data = str.read(blocksize)
        else:
            self.sock.sendall(str)
    except socket.error, v:
        reraise = True
        if v[0] == errno.EPIPE:      # Broken pipe
            if self._HTTPConnection__state == httplib._CS_REQ_SENT:
                self._broken_pipe_resp = None
                self._broken_pipe_resp = self.getresponse()
                reraise = False
            self.close()
        if reraise:
            raise

def wrapgetresponse(cls):
    """Wraps getresponse in cls with a broken-pipe sane version.
    """
    def safegetresponse(self):
        # In safesend() we might set the _broken_pipe_resp
        # attribute, in which case the socket has already
        # been closed and we just need to give them the response
        # back. Otherwise, we use the normal response path.
        r = getattr(self, '_broken_pipe_resp', None)
        if r is not None:
            return r
        return cls.getresponse(self)
    safegetresponse.__doc__ = cls.getresponse.__doc__
    return safegetresponse

class HTTPConnection(httplib.HTTPConnection):
    # use the modified response class
    response_class = HTTPResponse
    send = safesend
    getresponse = wrapgetresponse(httplib.HTTPConnection)


#########################################################################
#####   TEST FUNCTIONS
#########################################################################

def error_handler(url):
    global HANDLE_ERRORS
    orig = HANDLE_ERRORS
    keepalive_handler = HTTPHandler()
    opener = urllib2.build_opener(keepalive_handler)
    urllib2.install_opener(opener)
    pos = {0: 'off', 1: 'on'}
    for i in (0, 1):
        print "  fancy error handling %s (HANDLE_ERRORS = %i)" % (pos[i], i)
        HANDLE_ERRORS = i
        try:
            fo = urllib2.urlopen(url)
            fo.read()
            fo.close()
            try:
                status, reason = fo.status, fo.reason
            except AttributeError:
                status, reason = None, None
        except IOError, e:
            print "  EXCEPTION: %s" % e
            raise
        else:
            print "  status = %s, reason = %s" % (status, reason)
    HANDLE_ERRORS = orig
    hosts = keepalive_handler.open_connections()
    print "open connections:", hosts
    keepalive_handler.close_all()

def md5(s):
    try:
        from hashlib import md5 as _md5
    except ImportError:
        from md5 import md5 as _md5
    global md5
    md5 = _md5
    return _md5(s)

def continuity(url):
    format = '%25s: %s'

    # first fetch the file with the normal http handler
    opener = urllib2.build_opener()
    urllib2.install_opener(opener)
    fo = urllib2.urlopen(url)
    foo = fo.read()
    fo.close()
    m = md5.new(foo)
    print format % ('normal urllib', m.hexdigest())

    # now install the keepalive handler and try again
    opener = urllib2.build_opener(HTTPHandler())
    urllib2.install_opener(opener)

    fo = urllib2.urlopen(url)
    foo = fo.read()
    fo.close()
    m = md5.new(foo)
    print format % ('keepalive read', m.hexdigest())

    fo = urllib2.urlopen(url)
    foo = ''
    while 1:
        f = fo.readline()
        if f:
            foo = foo + f
        else: break
    fo.close()
    m = md5.new(foo)
    print format % ('keepalive readline', m.hexdigest())

def comp(N, url):
    print '  making %i connections to:\n  %s' % (N, url)

    sys.stdout.write('  first using the normal urllib handlers')
    # first use normal opener
    opener = urllib2.build_opener()
    urllib2.install_opener(opener)
    t1 = fetch(N, url)
    print '  TIME: %.3f s' % t1

    sys.stdout.write('  now using the keepalive handler       ')
    # now install the keepalive handler and try again
    opener = urllib2.build_opener(HTTPHandler())
    urllib2.install_opener(opener)
    t2 = fetch(N, url)
    print '  TIME: %.3f s' % t2
    print '  improvement factor: %.2f' % (t1 / t2)

def fetch(N, url, delay=0):
    import time
    lens = []
    starttime = time.time()
    for i in range(N):
        if delay and i > 0:
            time.sleep(delay)
        fo = urllib2.urlopen(url)
        foo = fo.read()
        fo.close()
        lens.append(len(foo))
    diff = time.time() - starttime

    j = 0
    for i in lens[1:]:
        j = j + 1
        if not i == lens[0]:
            print "WARNING: inconsistent length on read %i: %i" % (j, i)

    return diff

def test_timeout(url):
    global DEBUG
    dbbackup = DEBUG
    class FakeLogger:
        def debug(self, msg, *args):
            print msg % args
        info = warning = error = debug
    DEBUG = FakeLogger()
    print "  fetching the file to establish a connection"
    fo = urllib2.urlopen(url)
    data1 = fo.read()
    fo.close()

    i = 20
    print "  waiting %i seconds for the server to close the connection" % i
    while i > 0:
        sys.stdout.write('\r  %2i' % i)
        sys.stdout.flush()
        time.sleep(1)
        i -= 1
    sys.stderr.write('\r')

    print "  fetching the file a second time"
    fo = urllib2.urlopen(url)
    data2 = fo.read()
    fo.close()

    if data1 == data2:
        print '  data are identical'
    else:
        print '  ERROR: DATA DIFFER'

    DEBUG = dbbackup


def test(url, N=10):
    print "checking error hander (do this on a non-200)"
    try: error_handler(url)
    except IOError:
        print "exiting - exception will prevent further tests"
        sys.exit()
    print
    print "performing continuity test (making sure stuff isn't corrupted)"
    continuity(url)
    print
    print "performing speed comparison"
    comp(N, url)
    print
    print "performing dropped-connection check"
    test_timeout(url)

if __name__ == '__main__':
    import time
    import sys
    try:
        N = int(sys.argv[1])
        url = sys.argv[2]
    except:
        print "%s <integer> <url>" % sys.argv[0]
    else:
        test(url, N)
