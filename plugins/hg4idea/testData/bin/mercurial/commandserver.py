# commandserver.py - communicate with Mercurial's API over a pipe
#
#  Copyright Matt Mackall <mpm@selenic.com>
#
# This software may be used and distributed according to the terms of the
# GNU General Public License version 2 or any later version.

from i18n import _
import struct
import sys, os
import dispatch, encoding, util

logfile = None

def log(*args):
    if not logfile:
        return

    for a in args:
        logfile.write(str(a))

    logfile.flush()

class channeledoutput(object):
    """
    Write data from in_ to out in the following format:

    data length (unsigned int),
    data
    """
    def __init__(self, in_, out, channel):
        self.in_ = in_
        self.out = out
        self.channel = channel

    def write(self, data):
        if not data:
            return
        self.out.write(struct.pack('>cI', self.channel, len(data)))
        self.out.write(data)
        self.out.flush()

    def __getattr__(self, attr):
        if attr in ('isatty', 'fileno'):
            raise AttributeError(attr)
        return getattr(self.in_, attr)

class channeledinput(object):
    """
    Read data from in_.

    Requests for input are written to out in the following format:
    channel identifier - 'I' for plain input, 'L' line based (1 byte)
    how many bytes to send at most (unsigned int),

    The client replies with:
    data length (unsigned int), 0 meaning EOF
    data
    """

    maxchunksize = 4 * 1024

    def __init__(self, in_, out, channel):
        self.in_ = in_
        self.out = out
        self.channel = channel

    def read(self, size=-1):
        if size < 0:
            # if we need to consume all the clients input, ask for 4k chunks
            # so the pipe doesn't fill up risking a deadlock
            size = self.maxchunksize
            s = self._read(size, self.channel)
            buf = s
            while s:
                s = self._read(size, self.channel)
                buf += s

            return buf
        else:
            return self._read(size, self.channel)

    def _read(self, size, channel):
        if not size:
            return ''
        assert size > 0

        # tell the client we need at most size bytes
        self.out.write(struct.pack('>cI', channel, size))
        self.out.flush()

        length = self.in_.read(4)
        length = struct.unpack('>I', length)[0]
        if not length:
            return ''
        else:
            return self.in_.read(length)

    def readline(self, size=-1):
        if size < 0:
            size = self.maxchunksize
            s = self._read(size, 'L')
            buf = s
            # keep asking for more until there's either no more or
            # we got a full line
            while s and s[-1] != '\n':
                s = self._read(size, 'L')
                buf += s

            return buf
        else:
            return self._read(size, 'L')

    def __iter__(self):
        return self

    def next(self):
        l = self.readline()
        if not l:
            raise StopIteration
        return l

    def __getattr__(self, attr):
        if attr in ('isatty', 'fileno'):
            raise AttributeError(attr)
        return getattr(self.in_, attr)

class server(object):
    """
    Listens for commands on stdin, runs them and writes the output on a channel
    based stream to stdout.
    """
    def __init__(self, ui, repo, mode):
        self.cwd = os.getcwd()

        logpath = ui.config("cmdserver", "log", None)
        if logpath:
            global logfile
            if logpath == '-':
                # write log on a special 'd' (debug) channel
                logfile = channeledoutput(sys.stdout, sys.stdout, 'd')
            else:
                logfile = open(logpath, 'a')

        # the ui here is really the repo ui so take its baseui so we don't end
        # up with its local configuration
        self.ui = repo.baseui
        self.repo = repo
        self.repoui = repo.ui

        if mode == 'pipe':
            self.cerr = channeledoutput(sys.stderr, sys.stdout, 'e')
            self.cout = channeledoutput(sys.stdout, sys.stdout, 'o')
            self.cin = channeledinput(sys.stdin, sys.stdout, 'I')
            self.cresult = channeledoutput(sys.stdout, sys.stdout, 'r')

            self.client = sys.stdin
        else:
            raise util.Abort(_('unknown mode %s') % mode)

    def _read(self, size):
        if not size:
            return ''

        data = self.client.read(size)

        # is the other end closed?
        if not data:
            raise EOFError

        return data

    def runcommand(self):
        """ reads a list of \0 terminated arguments, executes
        and writes the return code to the result channel """

        length = struct.unpack('>I', self._read(4))[0]
        if not length:
            args = []
        else:
            args = self._read(length).split('\0')

        # copy the uis so changes (e.g. --config or --verbose) don't
        # persist between requests
        copiedui = self.ui.copy()
        self.repo.baseui = copiedui
        self.repo.ui = self.repo.dirstate._ui = self.repoui.copy()
        self.repo.invalidate()
        self.repo.invalidatedirstate()

        req = dispatch.request(args[:], copiedui, self.repo, self.cin,
                               self.cout, self.cerr)

        ret = dispatch.dispatch(req) or 0 # might return None

        # restore old cwd
        if '--cwd' in args:
            os.chdir(self.cwd)

        self.cresult.write(struct.pack('>i', int(ret)))

    def getencoding(self):
        """ writes the current encoding to the result channel """
        self.cresult.write(encoding.encoding)

    def serveone(self):
        cmd = self.client.readline()[:-1]
        if cmd:
            handler = self.capabilities.get(cmd)
            if handler:
                handler(self)
            else:
                # clients are expected to check what commands are supported by
                # looking at the servers capabilities
                raise util.Abort(_('unknown command %s') % cmd)

        return cmd != ''

    capabilities = {'runcommand'  : runcommand,
                    'getencoding' : getencoding}

    def serve(self):
        hellomsg = 'capabilities: ' + ' '.join(sorted(self.capabilities))
        hellomsg += '\n'
        hellomsg += 'encoding: ' + encoding.encoding

        # write the hello msg in -one- chunk
        self.cout.write(hellomsg)

        try:
            while self.serveone():
                pass
        except EOFError:
            # we'll get here if the client disconnected while we were reading
            # its request
            return 1

        return 0
