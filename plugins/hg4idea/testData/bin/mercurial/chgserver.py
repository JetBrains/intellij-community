# chgserver.py - command server extension for cHg
#
# Copyright 2011 Yuya Nishihara <yuya@tcha.org>
#
# This software may be used and distributed according to the terms of the
# GNU General Public License version 2 or any later version.

"""command server extension for cHg

'S' channel (read/write)
    propagate ui.system() request to client

'attachio' command
    attach client's stdio passed by sendmsg()

'chdir' command
    change current directory

'setenv' command
    replace os.environ completely

'setumask' command (DEPRECATED)
'setumask2' command
    set umask

'validate' command
    reload the config and check if the server is up to date

Config
------

::

  [chgserver]
  # how long (in seconds) should an idle chg server exit
  idletimeout = 3600

  # whether to skip config or env change checks
  skiphash = False
"""


import inspect
import os
import re
import socket
import stat
import struct
import time

from typing import (
    Optional,
)

from .i18n import _
from .node import hex

from . import (
    commandserver,
    encoding,
    error,
    extensions,
    pycompat,
    util,
)

from .utils import (
    hashutil,
    procutil,
    stringutil,
)


def _hashlist(items):
    """return sha1 hexdigest for a list"""
    return hex(hashutil.sha1(stringutil.pprint(items)).digest())


# sensitive config sections affecting confighash
_configsections = [
    b'alias',  # affects global state commands.table
    b'diff-tools',  # affects whether gui or not in extdiff's uisetup
    b'eol',  # uses setconfig('eol', ...)
    b'extdiff',  # uisetup will register new commands
    b'extensions',
    b'fastannotate',  # affects annotate command and adds fastannonate cmd
    b'merge-tools',  # affects whether gui or not in extdiff's uisetup
    b'schemes',  # extsetup will update global hg.schemes
]

_configsectionitems = [
    (b'commands', b'show.aliasprefix'),  # show.py reads it in extsetup
]

# sensitive environment variables affecting confighash
_envre = re.compile(
    br'''\A(?:
                    CHGHG
                    |HG(?:DEMANDIMPORT|EMITWARNINGS|MODULEPOLICY|PROF|RCPATH)?
                    |HG(?:ENCODING|PLAIN).*
                    |LANG(?:UAGE)?
                    |LC_.*
                    |LD_.*
                    |PATH
                    |PYTHON.*
                    |TERM(?:INFO)?
                    |TZ
                    )\Z''',
    re.X,
)


def _confighash(ui):
    """return a quick hash for detecting config/env changes

    confighash is the hash of sensitive config items and environment variables.

    for chgserver, it is designed that once confighash changes, the server is
    not qualified to serve its client and should redirect the client to a new
    server. different from mtimehash, confighash change will not mark the
    server outdated and exit since the user can have different configs at the
    same time.
    """
    sectionitems = []
    for section in _configsections:
        sectionitems.append(ui.configitems(section))
    for section, item in _configsectionitems:
        sectionitems.append(ui.config(section, item))
    sectionhash = _hashlist(sectionitems)
    # If $CHGHG is set, the change to $HG should not trigger a new chg server
    if b'CHGHG' in encoding.environ:
        ignored = {b'HG'}
    else:
        ignored = set()
    envitems = [
        (k, v)
        for k, v in encoding.environ.items()
        if _envre.match(k) and k not in ignored
    ]
    envhash = _hashlist(sorted(envitems))
    return sectionhash[:6] + envhash[:6]


def _getmtimepaths(ui):
    """get a list of paths that should be checked to detect change

    The list will include:
    - extensions (will not cover all files for complex extensions)
    - mercurial/__version__.py
    - python binary
    """
    modules = [m for n, m in extensions.extensions(ui)]
    try:
        from . import __version__

        modules.append(__version__)
    except ImportError:
        pass
    files = []
    if pycompat.sysexecutable:
        files.append(pycompat.sysexecutable)
    for m in modules:
        try:
            files.append(pycompat.fsencode(inspect.getabsfile(m)))
        except TypeError:
            pass
    return sorted(set(files))


def _mtimehash(paths):
    """return a quick hash for detecting file changes

    mtimehash calls stat on given paths and calculate a hash based on size and
    mtime of each file. mtimehash does not read file content because reading is
    expensive. therefore it's not 100% reliable for detecting content changes.
    it's possible to return different hashes for same file contents.
    it's also possible to return a same hash for different file contents for
    some carefully crafted situation.

    for chgserver, it is designed that once mtimehash changes, the server is
    considered outdated immediately and should no longer provide service.

    mtimehash is not included in confighash because we only know the paths of
    extensions after importing them (there is imp.find_module but that faces
    race conditions). We need to calculate confighash without importing.
    """

    def trystat(path):
        try:
            st = os.stat(path)
            return (st[stat.ST_MTIME], st.st_size)
        except OSError:
            # could be ENOENT, EPERM etc. not fatal in any case
            pass

    return _hashlist(pycompat.maplist(trystat, paths))[:12]


class hashstate:
    """a structure storing confighash, mtimehash, paths used for mtimehash"""

    def __init__(self, confighash, mtimehash, mtimepaths):
        self.confighash = confighash
        self.mtimehash = mtimehash
        self.mtimepaths = mtimepaths

    @staticmethod
    def fromui(ui, mtimepaths=None):
        if mtimepaths is None:
            mtimepaths = _getmtimepaths(ui)
        confighash = _confighash(ui)
        mtimehash = _mtimehash(mtimepaths)
        ui.log(
            b'cmdserver',
            b'confighash = %s mtimehash = %s\n',
            confighash,
            mtimehash,
        )
        return hashstate(confighash, mtimehash, mtimepaths)


def _newchgui(srcui, csystem, attachio):
    class chgui(srcui.__class__):
        def __init__(self, src=None):
            super(chgui, self).__init__(src)
            if src:
                self._csystem = getattr(src, '_csystem', csystem)
            else:
                self._csystem = csystem

        def _runsystem(self, cmd, environ, cwd, out):
            # fallback to the original system method if
            #  a. the output stream is not stdout (e.g. stderr, cStringIO),
            #  b. or stdout is redirected by protectfinout(),
            # because the chg client is not aware of these situations and
            # will behave differently (i.e. write to stdout).
            if (
                out is not self.fout
                or not hasattr(self.fout, 'fileno')
                or self.fout.fileno() != procutil.stdout.fileno()
                or self._finoutredirected
            ):
                return procutil.system(cmd, environ=environ, cwd=cwd, out=out)
            self.flush()
            return self._csystem(cmd, procutil.shellenviron(environ), cwd)

        def _runpager(self, cmd, env=None):
            self._csystem(
                cmd,
                procutil.shellenviron(env),
                type=b'pager',
                cmdtable={b'attachio': attachio},
            )
            return True

    return chgui(srcui)


def _loadnewui(srcui, args, cdebug):
    from . import dispatch  # avoid cycle

    newui = srcui.__class__.load()
    for a in ['fin', 'fout', 'ferr', 'environ']:
        setattr(newui, a, getattr(srcui, a))
    if hasattr(srcui, '_csystem'):
        newui._csystem = srcui._csystem

    # command line args
    options = dispatch._earlyparseopts(newui, args)
    dispatch._parseconfig(newui, options[b'config'])

    # stolen from tortoisehg.util.copydynamicconfig()
    for section, name, value in srcui.walkconfig():
        source = srcui.configsource(section, name)
        if b':' in source or source == b'--config' or source.startswith(b'$'):
            # path:line or command line, or environ
            continue
        newui.setconfig(section, name, value, source)

    # load wd and repo config, copied from dispatch.py
    cwd = options[b'cwd']
    cwd = cwd and os.path.realpath(cwd) or None
    rpath = options[b'repository']
    path, newlui = dispatch._getlocal(newui, rpath, wd=cwd)

    extensions.populateui(newui)
    commandserver.setuplogging(newui, fp=cdebug)
    if newui is not newlui:
        extensions.populateui(newlui)
        commandserver.setuplogging(newlui, fp=cdebug)

    return (newui, newlui)


class channeledsystem:
    """Propagate ui.system() request in the following format:

    payload length (unsigned int),
    type, '\0',
    cmd, '\0',
    cwd, '\0',
    envkey, '=', val, '\0',
    ...
    envkey, '=', val

    if type == 'system', waits for:

    exitcode length (unsigned int),
    exitcode (int)

    if type == 'pager', repetitively waits for a command name ending with '\n'
    and executes it defined by cmdtable, or exits the loop if the command name
    is empty.
    """

    def __init__(self, in_, out, channel):
        self.in_ = in_
        self.out = out
        self.channel = channel

    def __call__(self, cmd, environ, cwd=None, type=b'system', cmdtable=None):
        args = [type, cmd, util.abspath(cwd or b'.')]
        args.extend(b'%s=%s' % (k, v) for k, v in environ.items())
        data = b'\0'.join(args)
        self.out.write(struct.pack(b'>cI', self.channel, len(data)))
        self.out.write(data)
        self.out.flush()

        if type == b'system':
            length = self.in_.read(4)
            (length,) = struct.unpack(b'>I', length)
            if length != 4:
                raise error.Abort(_(b'invalid response'))
            (rc,) = struct.unpack(b'>i', self.in_.read(4))
            return rc
        elif type == b'pager':
            while True:
                cmd = self.in_.readline()[:-1]
                if not cmd:
                    break
                if cmdtable and cmd in cmdtable:
                    cmdtable[cmd]()
                else:
                    raise error.Abort(_(b'unexpected command: %s') % cmd)
        else:
            raise error.ProgrammingError(b'invalid S channel type: %s' % type)


_iochannels = [
    # server.ch, ui.fp, mode
    ('cin', 'fin', 'rb'),
    ('cout', 'fout', 'wb'),
    ('cerr', 'ferr', 'wb'),
]


class chgcmdserver(commandserver.server):
    def __init__(
        self, ui, repo, fin, fout, sock, prereposetups, hashstate, baseaddress
    ):
        super(chgcmdserver, self).__init__(
            _newchgui(ui, channeledsystem(fin, fout, b'S'), self.attachio),
            repo,
            fin,
            fout,
            prereposetups,
        )
        self.clientsock = sock
        self._ioattached = False
        self._oldios = []  # original (self.ch, ui.fp, fd) before "attachio"
        self.hashstate = hashstate
        self.baseaddress = baseaddress
        if hashstate is not None:
            self.capabilities = self.capabilities.copy()
            self.capabilities[b'validate'] = chgcmdserver.validate

    def cleanup(self):
        super(chgcmdserver, self).cleanup()
        # dispatch._runcatch() does not flush outputs if exception is not
        # handled by dispatch._dispatch()
        self.ui.flush()
        self._restoreio()
        self._ioattached = False

    def attachio(self):
        """Attach to client's stdio passed via unix domain socket; all
        channels except cresult will no longer be used
        """
        # tell client to sendmsg() with 1-byte payload, which makes it
        # distinctive from "attachio\n" command consumed by client.read()
        self.clientsock.sendall(struct.pack(b'>cI', b'I', 1))

        data, ancdata, msg_flags, address = self.clientsock.recvmsg(1, 256)
        assert len(ancdata) == 1
        cmsg_level, cmsg_type, cmsg_data = ancdata[0]
        assert cmsg_level == socket.SOL_SOCKET
        assert cmsg_type == socket.SCM_RIGHTS
        # memoryview.cast() was added in typeshed 61600d68772a, but pytype
        # still complains
        # pytype: disable=attribute-error
        clientfds = memoryview(cmsg_data).cast('i').tolist()
        # pytype: enable=attribute-error
        self.ui.log(b'chgserver', b'received fds: %r\n', clientfds)

        ui = self.ui
        ui.flush()
        self._saveio()
        for fd, (cn, fn, mode) in zip(clientfds, _iochannels):
            assert fd > 0
            fp = getattr(ui, fn)
            os.dup2(fd, fp.fileno())
            os.close(fd)
            if self._ioattached:
                continue
            # reset buffering mode when client is first attached. as we want
            # to see output immediately on pager, the mode stays unchanged
            # when client re-attached. ferr is unchanged because it should
            # be unbuffered no matter if it is a tty or not.
            if fn == b'ferr':
                newfp = fp
            else:
                # On Python 3, the standard library doesn't offer line-buffered
                # binary streams, so wrap/unwrap it.
                if fp.isatty():
                    newfp = procutil.make_line_buffered(fp)
                else:
                    newfp = procutil.unwrap_line_buffered(fp)
            if newfp is not fp:
                setattr(ui, fn, newfp)
            setattr(self, cn, newfp)

        self._ioattached = True
        self.cresult.write(struct.pack(b'>i', len(clientfds)))

    def _saveio(self):
        if self._oldios:
            return
        ui = self.ui
        for cn, fn, _mode in _iochannels:
            ch = getattr(self, cn)
            fp = getattr(ui, fn)
            fd = os.dup(fp.fileno())
            self._oldios.append((ch, fp, fd))

    def _restoreio(self):
        if not self._oldios:
            return
        nullfd = os.open(os.devnull, os.O_WRONLY)
        ui = self.ui
        for (ch, fp, fd), (cn, fn, mode) in zip(self._oldios, _iochannels):
            try:
                if 'w' in mode:
                    # Discard buffered data which couldn't be flushed because
                    # of EPIPE. The data should belong to the current session
                    # and should never persist.
                    os.dup2(nullfd, fp.fileno())
                    fp.flush()
                os.dup2(fd, fp.fileno())
                os.close(fd)
            except OSError as err:
                # According to issue6330, running chg on heavy loaded systems
                # can lead to EBUSY. [man dup2] indicates that, on Linux,
                # EBUSY comes from a race condition between open() and dup2().
                # However it's not clear why open() race occurred for
                # newfd=stdin/out/err.
                self.ui.log(
                    b'chgserver',
                    b'got %s while duplicating %s\n',
                    stringutil.forcebytestr(err),
                    fn,
                )
            setattr(self, cn, ch)
            setattr(ui, fn, fp)
        os.close(nullfd)
        del self._oldios[:]

    def validate(self):
        """Reload the config and check if the server is up to date

        Read a list of '\0' separated arguments.
        Write a non-empty list of '\0' separated instruction strings or '\0'
        if the list is empty.
        An instruction string could be either:
            - "unlink $path", the client should unlink the path to stop the
              outdated server.
            - "redirect $path", the client should attempt to connect to $path
              first. If it does not work, start a new server. It implies
              "reconnect".
            - "exit $n", the client should exit directly with code n.
              This may happen if we cannot parse the config.
            - "reconnect", the client should close the connection and
              reconnect.
        If neither "reconnect" nor "redirect" is included in the instruction
        list, the client can continue with this server after completing all
        the instructions.
        """
        args = self._readlist()
        errorraised = False
        detailed_exit_code = 255
        try:
            self.ui, lui = _loadnewui(self.ui, args, self.cdebug)
        except error.RepoError as inst:
            # RepoError can be raised while trying to read shared source
            # configuration
            self.ui.error(_(b"abort: %s\n") % stringutil.forcebytestr(inst))
            if inst.hint:
                self.ui.error(_(b"(%s)\n") % inst.hint)
            errorraised = True
        except error.Error as inst:
            if inst.detailed_exit_code is not None:
                detailed_exit_code = inst.detailed_exit_code
            self.ui.error(inst.format())
            errorraised = True

        if errorraised:
            self.ui.flush()
            exit_code = 255
            if self.ui.configbool(b'ui', b'detailed-exit-code'):
                exit_code = detailed_exit_code
            self.cresult.write(b'exit %d' % exit_code)
            return
        newhash = hashstate.fromui(lui, self.hashstate.mtimepaths)
        insts = []
        if newhash.mtimehash != self.hashstate.mtimehash:
            addr = _hashaddress(self.baseaddress, self.hashstate.confighash)
            insts.append(b'unlink %s' % addr)
            # mtimehash is empty if one or more extensions fail to load.
            # to be compatible with hg, still serve the client this time.
            if self.hashstate.mtimehash:
                insts.append(b'reconnect')
        if newhash.confighash != self.hashstate.confighash:
            addr = _hashaddress(self.baseaddress, newhash.confighash)
            insts.append(b'redirect %s' % addr)
        self.ui.log(b'chgserver', b'validate: %s\n', stringutil.pprint(insts))
        self.cresult.write(b'\0'.join(insts) or b'\0')

    def chdir(self):
        """Change current directory

        Note that the behavior of --cwd option is bit different from this.
        It does not affect --config parameter.
        """
        path = self._readstr()
        if not path:
            return
        self.ui.log(b'chgserver', b"chdir to '%s'\n", path)
        os.chdir(path)

    def setumask(self):
        """Change umask (DEPRECATED)"""
        # BUG: this does not follow the message frame structure, but kept for
        # backward compatibility with old chg clients for some time
        self._setumask(self._read(4))

    def setumask2(self):
        """Change umask"""
        data = self._readstr()
        if len(data) != 4:
            raise ValueError(b'invalid mask length in setumask2 request')
        self._setumask(data)

    def _setumask(self, data):
        mask = struct.unpack(b'>I', data)[0]
        self.ui.log(b'chgserver', b'setumask %r\n', mask)
        util.setumask(mask)

    def runcommand(self):
        # pager may be attached within the runcommand session, which should
        # be detached at the end of the session. otherwise the pager wouldn't
        # receive EOF.
        globaloldios = self._oldios
        self._oldios = []
        try:
            return super(chgcmdserver, self).runcommand()
        finally:
            self._restoreio()
            self._oldios = globaloldios

    def setenv(self):
        """Clear and update os.environ

        Note that not all variables can make an effect on the running process.
        """
        l = self._readlist()
        try:
            newenv = dict(s.split(b'=', 1) for s in l)
        except ValueError:
            raise ValueError(b'unexpected value in setenv request')
        self.ui.log(b'chgserver', b'setenv: %r\n', sorted(newenv.keys()))

        encoding.environ.clear()
        encoding.environ.update(newenv)

    capabilities = commandserver.server.capabilities.copy()
    capabilities.update(
        {
            b'attachio': attachio,
            b'chdir': chdir,
            b'runcommand': runcommand,
            b'setenv': setenv,
            b'setumask': setumask,
            b'setumask2': setumask2,
        }
    )

    if hasattr(procutil, 'setprocname'):

        def setprocname(self):
            """Change process title"""
            name = self._readstr()
            self.ui.log(b'chgserver', b'setprocname: %r\n', name)
            procutil.setprocname(name)

        capabilities[b'setprocname'] = setprocname


def _tempaddress(address):
    return b'%s.%d.tmp' % (address, os.getpid())


def _hashaddress(address, hashstr):
    # if the basename of address contains '.', use only the left part. this
    # makes it possible for the client to pass 'server.tmp$PID' and follow by
    # an atomic rename to avoid locking when spawning new servers.
    dirname, basename = os.path.split(address)
    basename = basename.split(b'.', 1)[0]
    return b'%s-%s' % (os.path.join(dirname, basename), hashstr)


class chgunixservicehandler:
    """Set of operations for chg services"""

    pollinterval = 1  # [sec]

    _hashstate: Optional[hashstate]
    _baseaddress: Optional[bytes]
    _realaddress: Optional[bytes]

    def __init__(self, ui):
        self.ui = ui

        self._hashstate = None
        self._baseaddress = None
        self._realaddress = None

        self._idletimeout = ui.configint(b'chgserver', b'idletimeout')
        self._lastactive = time.time()

    def bindsocket(self, sock, address):
        self._inithashstate(address)
        self._checkextensions()
        self._bind(sock)
        self._createsymlink()
        # no "listening at" message should be printed to simulate hg behavior

    def _inithashstate(self, address):
        self._baseaddress = address
        if self.ui.configbool(b'chgserver', b'skiphash'):
            self._hashstate = None
            self._realaddress = address
            return
        self._hashstate = hashstate.fromui(self.ui)
        self._realaddress = _hashaddress(address, self._hashstate.confighash)

    def _checkextensions(self):
        if not self._hashstate:
            return
        if extensions.notloaded():
            # one or more extensions failed to load. mtimehash becomes
            # meaningless because we do not know the paths of those extensions.
            # set mtimehash to an illegal hash value to invalidate the server.
            self._hashstate.mtimehash = b''

    def _bind(self, sock):
        # use a unique temp address so we can stat the file and do ownership
        # check later
        tempaddress = _tempaddress(self._realaddress)
        util.bindunixsocket(sock, tempaddress)
        self._socketstat = os.stat(tempaddress)
        sock.listen(socket.SOMAXCONN)
        # rename will replace the old socket file if exists atomically. the
        # old server will detect ownership change and exit.
        util.rename(tempaddress, self._realaddress)

    def _createsymlink(self):
        if self._baseaddress == self._realaddress:
            return
        tempaddress = _tempaddress(self._baseaddress)
        os.symlink(os.path.basename(self._realaddress), tempaddress)
        util.rename(tempaddress, self._baseaddress)

    def _issocketowner(self):
        try:
            st = os.stat(self._realaddress)
            return (
                st.st_ino == self._socketstat.st_ino
                and st[stat.ST_MTIME] == self._socketstat[stat.ST_MTIME]
            )
        except OSError:
            return False

    def unlinksocket(self, address):
        if not self._issocketowner():
            return
        # it is possible to have a race condition here that we may
        # remove another server's socket file. but that's okay
        # since that server will detect and exit automatically and
        # the client will start a new server on demand.
        util.tryunlink(self._realaddress)

    def shouldexit(self):
        if not self._issocketowner():
            self.ui.log(
                b'chgserver', b'%s is not owned, exiting.\n', self._realaddress
            )
            return True
        if time.time() - self._lastactive > self._idletimeout:
            self.ui.log(b'chgserver', b'being idle too long. exiting.\n')
            return True
        return False

    def newconnection(self):
        self._lastactive = time.time()

    def createcmdserver(self, repo, conn, fin, fout, prereposetups):
        return chgcmdserver(
            self.ui,
            repo,
            fin,
            fout,
            conn,
            prereposetups,
            self._hashstate,
            self._baseaddress,
        )


def chgunixservice(ui, repo, opts):
    # CHGINTERNALMARK is set by chg client. It is an indication of things are
    # started by chg so other code can do things accordingly, like disabling
    # demandimport or detecting chg client started by chg client. When executed
    # here, CHGINTERNALMARK is no longer useful and hence dropped to make
    # environ cleaner.
    if b'CHGINTERNALMARK' in encoding.environ:
        del encoding.environ[b'CHGINTERNALMARK']
    # Python3.7+ "coerces" the LC_CTYPE environment variable to a UTF-8 one if
    # it thinks the current value is "C". This breaks the hash computation and
    # causes chg to restart loop.
    if b'CHGORIG_LC_CTYPE' in encoding.environ:
        encoding.environ[b'LC_CTYPE'] = encoding.environ[b'CHGORIG_LC_CTYPE']
        del encoding.environ[b'CHGORIG_LC_CTYPE']
    elif b'CHG_CLEAR_LC_CTYPE' in encoding.environ:
        if b'LC_CTYPE' in encoding.environ:
            del encoding.environ[b'LC_CTYPE']
        del encoding.environ[b'CHG_CLEAR_LC_CTYPE']

    if repo:
        # one chgserver can serve multiple repos. drop repo information
        ui.setconfig(b'bundle', b'mainreporoot', b'', b'repo')
    h = chgunixservicehandler(ui)
    return commandserver.unixforkingservice(ui, repo=None, opts=opts, handler=h)
