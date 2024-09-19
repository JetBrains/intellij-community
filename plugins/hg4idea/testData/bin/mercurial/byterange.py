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
#   License along with this library; if not, see
#   <http://www.gnu.org/licenses/>.

# This file is part of urlgrabber, a high-level cross-protocol url-grabber
# Copyright 2002-2004 Michael D. Stenner, Ryan Tomayko

# $Id: byterange.py,v 1.9 2005/02/14 21:55:07 mstenner Exp $

import os
import stat
import urllib
import urllib2
import email.Utils

class RangeError(IOError):
    """Error raised when an unsatisfiable range is requested."""
    pass

class HTTPRangeHandler(urllib2.BaseHandler):
    """Handler that enables HTTP Range headers.

    This was extremely simple. The Range header is a HTTP feature to
    begin with so all this class does is tell urllib2 that the
    "206 Partial Content" response from the HTTP server is what we
    expected.

    Example:
        import urllib2
        import byterange

        range_handler = range.HTTPRangeHandler()
        opener = urllib2.build_opener(range_handler)

        # install it
        urllib2.install_opener(opener)

        # create Request and set Range header
        req = urllib2.Request('http://www.python.org/')
        req.header['Range'] = 'bytes=30-50'
        f = urllib2.urlopen(req)
    """

    def http_error_206(self, req, fp, code, msg, hdrs):
        # 206 Partial Content Response
        r = urllib.addinfourl(fp, hdrs, req.get_full_url())
        r.code = code
        r.msg = msg
        return r

    def http_error_416(self, req, fp, code, msg, hdrs):
        # HTTP's Range Not Satisfiable error
        raise RangeError('Requested Range Not Satisfiable')

class RangeableFileObject(object):
    """File object wrapper to enable raw range handling.
    This was implemented primarily for handling range
    specifications for file:// urls. This object effectively makes
    a file object look like it consists only of a range of bytes in
    the stream.

    Examples:
        # expose 10 bytes, starting at byte position 20, from
        # /etc/aliases.
        >>> fo = RangeableFileObject(file('/etc/passwd', 'r'), (20,30))
        # seek seeks within the range (to position 23 in this case)
        >>> fo.seek(3)
        # tell tells where your at _within the range_ (position 3 in
        # this case)
        >>> fo.tell()
        # read EOFs if an attempt is made to read past the last
        # byte in the range. the following will return only 7 bytes.
        >>> fo.read(30)
    """

    def __init__(self, fo, rangetup):
        """Create a RangeableFileObject.
        fo       -- a file like object. only the read() method need be
                    supported but supporting an optimized seek() is
                    preferable.
        rangetup -- a (firstbyte,lastbyte) tuple specifying the range
                    to work over.
        The file object provided is assumed to be at byte offset 0.
        """
        self.fo = fo
        (self.firstbyte, self.lastbyte) = range_tuple_normalize(rangetup)
        self.realpos = 0
        self._do_seek(self.firstbyte)

    def __getattr__(self, name):
        """This effectively allows us to wrap at the instance level.
        Any attribute not found in _this_ object will be searched for
        in self.fo.  This includes methods."""
        return getattr(self.fo, name)

    def tell(self):
        """Return the position within the range.
        This is different from fo.seek in that position 0 is the
        first byte position of the range tuple. For example, if
        this object was created with a range tuple of (500,899),
        tell() will return 0 when at byte position 500 of the file.
        """
        return (self.realpos - self.firstbyte)

    def seek(self, offset, whence=0):
        """Seek within the byte range.
        Positioning is identical to that described under tell().
        """
        assert whence in (0, 1, 2)
        if whence == 0:   # absolute seek
            realoffset = self.firstbyte + offset
        elif whence == 1: # relative seek
            realoffset = self.realpos + offset
        elif whence == 2: # absolute from end of file
            # XXX: are we raising the right Error here?
            raise IOError('seek from end of file not supported.')

        # do not allow seek past lastbyte in range
        if self.lastbyte and (realoffset >= self.lastbyte):
            realoffset = self.lastbyte

        self._do_seek(realoffset - self.realpos)

    def read(self, size=-1):
        """Read within the range.
        This method will limit the size read based on the range.
        """
        size = self._calc_read_size(size)
        rslt = self.fo.read(size)
        self.realpos += len(rslt)
        return rslt

    def readline(self, size=-1):
        """Read lines within the range.
        This method will limit the size read based on the range.
        """
        size = self._calc_read_size(size)
        rslt = self.fo.readline(size)
        self.realpos += len(rslt)
        return rslt

    def _calc_read_size(self, size):
        """Handles calculating the amount of data to read based on
        the range.
        """
        if self.lastbyte:
            if size > -1:
                if ((self.realpos + size) >= self.lastbyte):
                    size = (self.lastbyte - self.realpos)
            else:
                size = (self.lastbyte - self.realpos)
        return size

    def _do_seek(self, offset):
        """Seek based on whether wrapped object supports seek().
        offset is relative to the current position (self.realpos).
        """
        assert offset >= 0
        seek = getattr(self.fo, 'seek', self._poor_mans_seek)
        seek(self.realpos + offset)
        self.realpos += offset

    def _poor_mans_seek(self, offset):
        """Seek by calling the wrapped file objects read() method.
        This is used for file like objects that do not have native
        seek support. The wrapped objects read() method is called
        to manually seek to the desired position.
        offset -- read this number of bytes from the wrapped
                  file object.
        raise RangeError if we encounter EOF before reaching the
        specified offset.
        """
        pos = 0
        bufsize = 1024
        while pos < offset:
            if (pos + bufsize) > offset:
                bufsize = offset - pos
            buf = self.fo.read(bufsize)
            if len(buf) != bufsize:
                raise RangeError('Requested Range Not Satisfiable')
            pos += bufsize

class FileRangeHandler(urllib2.FileHandler):
    """FileHandler subclass that adds Range support.
    This class handles Range headers exactly like an HTTP
    server would.
    """
    def open_local_file(self, req):
        import mimetypes
        import email
        host = req.get_host()
        file = req.get_selector()
        localfile = urllib.url2pathname(file)
        stats = os.stat(localfile)
        size = stats[stat.ST_SIZE]
        modified = email.Utils.formatdate(stats[stat.ST_MTIME])
        mtype = mimetypes.guess_type(file)[0]
        if host:
            host, port = urllib.splitport(host)
            if port or socket.gethostbyname(host) not in self.get_names():
                raise urllib2.URLError('file not on local host')
        fo = open(localfile,'rb')
        brange = req.headers.get('Range', None)
        brange = range_header_to_tuple(brange)
        assert brange != ()
        if brange:
            (fb, lb) = brange
            if lb == '':
                lb = size
            if fb < 0 or fb > size or lb > size:
                raise RangeError('Requested Range Not Satisfiable')
            size = (lb - fb)
            fo = RangeableFileObject(fo, (fb, lb))
        headers = email.message_from_string(
            'Content-Type: %s\nContent-Length: %d\nLast-Modified: %s\n' %
            (mtype or 'text/plain', size, modified))
        return urllib.addinfourl(fo, headers, 'file:'+file)


# FTP Range Support
# Unfortunately, a large amount of base FTP code had to be copied
# from urllib and urllib2 in order to insert the FTP REST command.
# Code modifications for range support have been commented as
# follows:
# -- range support modifications start/end here

from urllib import splitport, splituser, splitpasswd, splitattr, \
                   unquote, addclosehook, addinfourl
import ftplib
import socket
import mimetypes
import email

class FTPRangeHandler(urllib2.FTPHandler):
    def ftp_open(self, req):
        host = req.get_host()
        if not host:
            raise IOError('ftp error', 'no host given')
        host, port = splitport(host)
        if port is None:
            port = ftplib.FTP_PORT
        else:
            port = int(port)

        # username/password handling
        user, host = splituser(host)
        if user:
            user, passwd = splitpasswd(user)
        else:
            passwd = None
        host = unquote(host)
        user = unquote(user or '')
        passwd = unquote(passwd or '')

        try:
            host = socket.gethostbyname(host)
        except socket.error, msg:
            raise urllib2.URLError(msg)
        path, attrs = splitattr(req.get_selector())
        dirs = path.split('/')
        dirs = map(unquote, dirs)
        dirs, file = dirs[:-1], dirs[-1]
        if dirs and not dirs[0]:
            dirs = dirs[1:]
        try:
            fw = self.connect_ftp(user, passwd, host, port, dirs)
            type = file and 'I' or 'D'
            for attr in attrs:
                attr, value = splitattr(attr)
                if attr.lower() == 'type' and \
                   value in ('a', 'A', 'i', 'I', 'd', 'D'):
                    type = value.upper()

            # -- range support modifications start here
            rest = None
            range_tup = range_header_to_tuple(req.headers.get('Range', None))
            assert range_tup != ()
            if range_tup:
                (fb, lb) = range_tup
                if fb > 0:
                    rest = fb
            # -- range support modifications end here

            fp, retrlen = fw.retrfile(file, type, rest)

            # -- range support modifications start here
            if range_tup:
                (fb, lb) = range_tup
                if lb == '':
                    if retrlen is None or retrlen == 0:
                        raise RangeError('Requested Range Not Satisfiable due'
                                         ' to unobtainable file length.')
                    lb = retrlen
                    retrlen = lb - fb
                    if retrlen < 0:
                        # beginning of range is larger than file
                        raise RangeError('Requested Range Not Satisfiable')
                else:
                    retrlen = lb - fb
                    fp = RangeableFileObject(fp, (0, retrlen))
            # -- range support modifications end here

            headers = ""
            mtype = mimetypes.guess_type(req.get_full_url())[0]
            if mtype:
                headers += "Content-Type: %s\n" % mtype
            if retrlen is not None and retrlen >= 0:
                headers += "Content-Length: %d\n" % retrlen
            headers = email.message_from_string(headers)
            return addinfourl(fp, headers, req.get_full_url())
        except ftplib.all_errors, msg:
            raise IOError('ftp error', msg)

    def connect_ftp(self, user, passwd, host, port, dirs):
        fw = ftpwrapper(user, passwd, host, port, dirs)
        return fw

class ftpwrapper(urllib.ftpwrapper):
    # range support note:
    # this ftpwrapper code is copied directly from
    # urllib. The only enhancement is to add the rest
    # argument and pass it on to ftp.ntransfercmd
    def retrfile(self, file, type, rest=None):
        self.endtransfer()
        if type in ('d', 'D'):
            cmd = 'TYPE A'
            isdir = 1
        else:
            cmd = 'TYPE ' + type
            isdir = 0
        try:
            self.ftp.voidcmd(cmd)
        except ftplib.all_errors:
            self.init()
            self.ftp.voidcmd(cmd)
        conn = None
        if file and not isdir:
            # Use nlst to see if the file exists at all
            try:
                self.ftp.nlst(file)
            except ftplib.error_perm, reason:
                raise IOError('ftp error', reason)
            # Restore the transfer mode!
            self.ftp.voidcmd(cmd)
            # Try to retrieve as a file
            try:
                cmd = 'RETR ' + file
                conn = self.ftp.ntransfercmd(cmd, rest)
            except ftplib.error_perm, reason:
                if str(reason).startswith('501'):
                    # workaround for REST not supported error
                    fp, retrlen = self.retrfile(file, type)
                    fp = RangeableFileObject(fp, (rest,''))
                    return (fp, retrlen)
                elif not str(reason).startswith('550'):
                    raise IOError('ftp error', reason)
        if not conn:
            # Set transfer mode to ASCII!
            self.ftp.voidcmd('TYPE A')
            # Try a directory listing
            if file:
                cmd = 'LIST ' + file
            else:
                cmd = 'LIST'
            conn = self.ftp.ntransfercmd(cmd)
        self.busy = 1
        # Pass back both a suitably decorated object and a retrieval length
        return (addclosehook(conn[0].makefile('rb'),
                            self.endtransfer), conn[1])


####################################################################
# Range Tuple Functions
# XXX: These range tuple functions might go better in a class.

_rangere = None
def range_header_to_tuple(range_header):
    """Get a (firstbyte,lastbyte) tuple from a Range header value.

    Range headers have the form "bytes=<firstbyte>-<lastbyte>". This
    function pulls the firstbyte and lastbyte values and returns
    a (firstbyte,lastbyte) tuple. If lastbyte is not specified in
    the header value, it is returned as an empty string in the
    tuple.

    Return None if range_header is None
    Return () if range_header does not conform to the range spec
    pattern.

    """
    global _rangere
    if range_header is None:
        return None
    if _rangere is None:
        import re
        _rangere = re.compile(r'^bytes=(\d{1,})-(\d*)')
    match = _rangere.match(range_header)
    if match:
        tup = range_tuple_normalize(match.group(1, 2))
        if tup and tup[1]:
            tup = (tup[0], tup[1]+1)
        return tup
    return ()

def range_tuple_to_header(range_tup):
    """Convert a range tuple to a Range header value.
    Return a string of the form "bytes=<firstbyte>-<lastbyte>" or None
    if no range is needed.
    """
    if range_tup is None:
        return None
    range_tup = range_tuple_normalize(range_tup)
    if range_tup:
        if range_tup[1]:
            range_tup = (range_tup[0], range_tup[1] - 1)
        return 'bytes=%s-%s' % range_tup

def range_tuple_normalize(range_tup):
    """Normalize a (first_byte,last_byte) range tuple.
    Return a tuple whose first element is guaranteed to be an int
    and whose second element will be '' (meaning: the last byte) or
    an int. Finally, return None if the normalized tuple == (0,'')
    as that is equivalent to retrieving the entire file.
    """
    if range_tup is None:
        return None
    # handle first byte
    fb = range_tup[0]
    if fb in (None, ''):
        fb = 0
    else:
        fb = int(fb)
    # handle last byte
    try:
        lb = range_tup[1]
    except IndexError:
        lb = ''
    else:
        if lb is None:
            lb = ''
        elif lb != '':
            lb = int(lb)
    # check if range is over the entire file
    if (fb, lb) == (0, ''):
        return None
    # check that the range is valid
    if lb < fb:
        raise RangeError('Invalid byte range: %s-%s' % (fb, lb))
    return (fb, lb)
