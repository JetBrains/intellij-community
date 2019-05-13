# client.py - inotify status client
#
# Copyright 2006, 2007, 2008 Bryan O'Sullivan <bos@serpentine.com>
# Copyright 2007, 2008 Brendan Cully <brendan@kublai.com>
# Copyright 2009 Nicolas Dumazet <nicdumz@gmail.com>
#
# This software may be used and distributed according to the terms of the
# GNU General Public License version 2 or any later version.

from mercurial.i18n import _
import common, server
import errno, os, socket, struct

class QueryFailed(Exception):
    pass

def start_server(function):
    """
    Decorator.
    Tries to call function, if it fails, try to (re)start inotify server.
    Raise QueryFailed if something went wrong
    """
    def decorated_function(self, *args):
        try:
            return function(self, *args)
        except (OSError, socket.error), err:
            autostart = self.ui.configbool('inotify', 'autostart', True)

            if err.args[0] == errno.ECONNREFUSED:
                self.ui.warn(_('inotify-client: found dead inotify server '
                               'socket; removing it\n'))
                os.unlink(os.path.join(self.root, '.hg', 'inotify.sock'))
            if err.args[0] in (errno.ECONNREFUSED, errno.ENOENT) and autostart:
                try:
                    try:
                        server.start(self.ui, self.dirstate, self.root,
                                     dict(daemon=True, daemon_pipefds=''))
                    except server.AlreadyStartedException, inst:
                        # another process may have started its own
                        # inotify server while this one was starting.
                        self.ui.debug(str(inst))
                except Exception, inst:
                    self.ui.warn(_('inotify-client: could not start inotify '
                                   'server: %s\n') % inst)
                else:
                    try:
                        return function(self, *args)
                    except socket.error, err:
                        self.ui.warn(_('inotify-client: could not talk to new '
                                       'inotify server: %s\n') % err.args[-1])
            elif err.args[0] in (errno.ECONNREFUSED, errno.ENOENT):
                # silently ignore normal errors if autostart is False
                self.ui.debug('(inotify server not running)\n')
            else:
                self.ui.warn(_('inotify-client: failed to contact inotify '
                               'server: %s\n') % err.args[-1])

        self.ui.traceback()
        raise QueryFailed('inotify query failed')

    return decorated_function


class client(object):
    def __init__(self, ui, repo):
        self.ui = ui
        self.dirstate = repo.dirstate
        self.root = repo.root
        self.sock = socket.socket(socket.AF_UNIX)

    def _connect(self):
        sockpath = os.path.join(self.root, '.hg', 'inotify.sock')
        try:
            self.sock.connect(sockpath)
        except socket.error, err:
            if err.args[0] == "AF_UNIX path too long":
                sockpath = os.readlink(sockpath)
                self.sock.connect(sockpath)
            else:
                raise

    def _send(self, type, data):
        """Sends protocol version number, and the data"""
        self.sock.sendall(chr(common.version) + type + data)

        self.sock.shutdown(socket.SHUT_WR)

    def _receive(self, type):
        """
        Read data, check version number, extract headers,
        and returns a tuple (data descriptor, header)
        Raises QueryFailed on error
        """
        cs = common.recvcs(self.sock)
        try:
            version = ord(cs.read(1))
        except TypeError:
            # empty answer, assume the server crashed
            self.ui.warn(_('inotify-client: received empty answer from inotify '
                           'server'))
            raise QueryFailed('server crashed')

        if version != common.version:
            self.ui.warn(_('(inotify: received response from incompatible '
                      'server version %d)\n') % version)
            raise QueryFailed('incompatible server version')

        readtype = cs.read(4)
        if readtype != type:
            self.ui.warn(_('(inotify: received \'%s\' response when expecting'
                       ' \'%s\')\n') % (readtype, type))
            raise QueryFailed('wrong response type')

        hdrfmt = common.resphdrfmts[type]
        hdrsize = common.resphdrsizes[type]
        try:
            resphdr = struct.unpack(hdrfmt, cs.read(hdrsize))
        except struct.error:
            raise QueryFailed('unable to retrieve query response headers')

        return cs, resphdr

    def query(self, type, req):
        self._connect()

        self._send(type, req)

        return self._receive(type)

    @start_server
    def statusquery(self, names, match, ignored, clean, unknown=True):

        def genquery():
            for n in names:
                yield n
            states = 'almrx!'
            if ignored:
                raise ValueError('this is insanity')
            if clean:
                states += 'c'
            if unknown:
                states += '?'
            yield states

        req = '\0'.join(genquery())

        cs, resphdr = self.query('STAT', req)

        def readnames(nbytes):
            if nbytes:
                names = cs.read(nbytes)
                if names:
                    return filter(match, names.split('\0'))
            return []
        results = tuple(map(readnames, resphdr[:-1]))

        if names:
            nbytes = resphdr[-1]
            vdirs = cs.read(nbytes)
            if vdirs:
                for vdir in vdirs.split('\0'):
                    match.dir(vdir)

        return results

    @start_server
    def debugquery(self):
        cs, resphdr = self.query('DBUG', '')

        nbytes = resphdr[0]
        names = cs.read(nbytes)
        return names.split('\0')
