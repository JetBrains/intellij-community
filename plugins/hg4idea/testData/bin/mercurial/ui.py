# ui.py - user interface bits for mercurial
#
# Copyright 2005-2007 Olivia Mackall <olivia@selenic.com>
#
# This software may be used and distributed according to the terms of the
# GNU General Public License version 2 or any later version.

from __future__ import absolute_import

import collections
import contextlib
import datetime
import errno
import inspect
import os
import re
import signal
import socket
import subprocess
import sys
import traceback

from .i18n import _
from .node import hex
from .pycompat import (
    getattr,
    open,
)

from . import (
    color,
    config,
    configitems,
    encoding,
    error,
    formatter,
    loggingutil,
    progress,
    pycompat,
    rcutil,
    scmutil,
    util,
)
from .utils import (
    dateutil,
    procutil,
    resourceutil,
    stringutil,
    urlutil,
)

urlreq = util.urlreq

# for use with str.translate(None, _keepalnum), to keep just alphanumerics
_keepalnum = b''.join(
    c for c in map(pycompat.bytechr, range(256)) if not c.isalnum()
)

# The config knobs that will be altered (if unset) by ui.tweakdefaults.
tweakrc = b"""
[ui]
# The rollback command is dangerous. As a rule, don't use it.
rollback = False
# Make `hg status` report copy information
statuscopies = yes
# Prefer curses UIs when available. Revert to plain-text with `text`.
interface = curses
# Make compatible commands emit cwd-relative paths by default.
relative-paths = yes

[commands]
# Grep working directory by default.
grep.all-files = True
# Refuse to perform an `hg update` that would cause a file content merge
update.check = noconflict
# Show conflicts information in `hg status`
status.verbose = True
# Make `hg resolve` with no action (like `-m`) fail instead of re-merging.
resolve.explicit-re-merge = True

[diff]
git = 1
showfunc = 1
word-diff = 1
"""

samplehgrcs = {
    b'user': b"""# example user config (see 'hg help config' for more info)
[ui]
# name and email, e.g.
# username = Jane Doe <jdoe@example.com>
username =

# We recommend enabling tweakdefaults to get slight improvements to
# the UI over time. Make sure to set HGPLAIN in the environment when
# writing scripts!
# tweakdefaults = True

# uncomment to disable color in command output
# (see 'hg help color' for details)
# color = never

# uncomment to disable command output pagination
# (see 'hg help pager' for details)
# paginate = never

[extensions]
# uncomment the lines below to enable some popular extensions
# (see 'hg help extensions' for more info)
#
# histedit =
# rebase =
# uncommit =
""",
    b'cloned': b"""# example repository config (see 'hg help config' for more info)
[paths]
default = %s

# path aliases to other clones of this repo in URLs or filesystem paths
# (see 'hg help config.paths' for more info)
#
# default:pushurl = ssh://jdoe@example.net/hg/jdoes-fork
# my-fork         = ssh://jdoe@example.net/hg/jdoes-fork
# my-clone        = /home/jdoe/jdoes-clone

[ui]
# name and email (local to this repository, optional), e.g.
# username = Jane Doe <jdoe@example.com>
""",
    b'local': b"""# example repository config (see 'hg help config' for more info)
[paths]
# path aliases to other clones of this repo in URLs or filesystem paths
# (see 'hg help config.paths' for more info)
#
# default         = http://example.com/hg/example-repo
# default:pushurl = ssh://jdoe@example.net/hg/jdoes-fork
# my-fork         = ssh://jdoe@example.net/hg/jdoes-fork
# my-clone        = /home/jdoe/jdoes-clone

[ui]
# name and email (local to this repository, optional), e.g.
# username = Jane Doe <jdoe@example.com>
""",
    b'global': b"""# example system-wide hg config (see 'hg help config' for more info)

[ui]
# uncomment to disable color in command output
# (see 'hg help color' for details)
# color = never

# uncomment to disable command output pagination
# (see 'hg help pager' for details)
# paginate = never

[extensions]
# uncomment the lines below to enable some popular extensions
# (see 'hg help extensions' for more info)
#
# blackbox =
# churn =
""",
}


def _maybestrurl(maybebytes):
    return pycompat.rapply(pycompat.strurl, maybebytes)


def _maybebytesurl(maybestr):
    return pycompat.rapply(pycompat.bytesurl, maybestr)


class httppasswordmgrdbproxy(object):
    """Delays loading urllib2 until it's needed."""

    def __init__(self):
        self._mgr = None

    def _get_mgr(self):
        if self._mgr is None:
            self._mgr = urlreq.httppasswordmgrwithdefaultrealm()
        return self._mgr

    def add_password(self, realm, uris, user, passwd):
        return self._get_mgr().add_password(
            _maybestrurl(realm),
            _maybestrurl(uris),
            _maybestrurl(user),
            _maybestrurl(passwd),
        )

    def find_user_password(self, realm, uri):
        mgr = self._get_mgr()
        return _maybebytesurl(
            mgr.find_user_password(_maybestrurl(realm), _maybestrurl(uri))
        )


def _catchterm(*args):
    raise error.SignalInterrupt


# unique object used to detect no default value has been provided when
# retrieving configuration value.
_unset = object()

# _reqexithandlers: callbacks run at the end of a request
_reqexithandlers = []


class ui(object):
    def __init__(self, src=None):
        """Create a fresh new ui object if no src given

        Use uimod.ui.load() to create a ui which knows global and user configs.
        In most cases, you should use ui.copy() to create a copy of an existing
        ui object.
        """
        # _buffers: used for temporary capture of output
        self._buffers = []
        # 3-tuple describing how each buffer in the stack behaves.
        # Values are (capture stderr, capture subprocesses, apply labels).
        self._bufferstates = []
        # When a buffer is active, defines whether we are expanding labels.
        # This exists to prevent an extra list lookup.
        self._bufferapplylabels = None
        self.quiet = self.verbose = self.debugflag = self.tracebackflag = False
        self._reportuntrusted = True
        self._knownconfig = configitems.coreitems
        self._ocfg = config.config()  # overlay
        self._tcfg = config.config()  # trusted
        self._ucfg = config.config()  # untrusted
        self._trustusers = set()
        self._trustgroups = set()
        self.callhooks = True
        # hold the root to use for each [paths] entry
        self._path_to_root = {}
        # Insecure server connections requested.
        self.insecureconnections = False
        # Blocked time
        self.logblockedtimes = False
        # color mode: see mercurial/color.py for possible value
        self._colormode = None
        self._terminfoparams = {}
        self._styles = {}
        self._uninterruptible = False
        self.showtimestamp = False

        if src:
            self._fout = src._fout
            self._ferr = src._ferr
            self._fin = src._fin
            self._fmsg = src._fmsg
            self._fmsgout = src._fmsgout
            self._fmsgerr = src._fmsgerr
            self._finoutredirected = src._finoutredirected
            self._loggers = src._loggers.copy()
            self.pageractive = src.pageractive
            self._disablepager = src._disablepager
            self._tweaked = src._tweaked

            self._tcfg = src._tcfg.copy()
            self._ucfg = src._ucfg.copy()
            self._ocfg = src._ocfg.copy()
            self._trustusers = src._trustusers.copy()
            self._trustgroups = src._trustgroups.copy()
            self.environ = src.environ
            self.callhooks = src.callhooks
            self._path_to_root = src._path_to_root
            self.insecureconnections = src.insecureconnections
            self._colormode = src._colormode
            self._terminfoparams = src._terminfoparams.copy()
            self._styles = src._styles.copy()

            self.fixconfig()

            self.httppasswordmgrdb = src.httppasswordmgrdb
            self._blockedtimes = src._blockedtimes
        else:
            self._fout = procutil.stdout
            self._ferr = procutil.stderr
            self._fin = procutil.stdin
            self._fmsg = None
            self._fmsgout = self.fout  # configurable
            self._fmsgerr = self.ferr  # configurable
            self._finoutredirected = False
            self._loggers = {}
            self.pageractive = False
            self._disablepager = False
            self._tweaked = False

            # shared read-only environment
            self.environ = encoding.environ

            self.httppasswordmgrdb = httppasswordmgrdbproxy()
            self._blockedtimes = collections.defaultdict(int)

        allowed = self.configlist(b'experimental', b'exportableenviron')
        if b'*' in allowed:
            self._exportableenviron = self.environ
        else:
            self._exportableenviron = {}
            for k in allowed:
                if k in self.environ:
                    self._exportableenviron[k] = self.environ[k]

    def _new_source(self):
        self._ocfg.new_source()
        self._tcfg.new_source()
        self._ucfg.new_source()

    @classmethod
    def load(cls):
        """Create a ui and load global and user configs"""
        u = cls()
        # we always trust global config files and environment variables
        for t, f in rcutil.rccomponents():
            if t == b'path':
                u.readconfig(f, trust=True)
            elif t == b'resource':
                u.read_resource_config(f, trust=True)
            elif t == b'items':
                u._new_source()
                sections = set()
                for section, name, value, source in f:
                    # do not set u._ocfg
                    # XXX clean this up once immutable config object is a thing
                    u._tcfg.set(section, name, value, source)
                    u._ucfg.set(section, name, value, source)
                    sections.add(section)
                for section in sections:
                    u.fixconfig(section=section)
            else:
                raise error.ProgrammingError(b'unknown rctype: %s' % t)
        u._maybetweakdefaults()
        u._new_source()  # anything after that is a different level
        return u

    def _maybetweakdefaults(self):
        if not self.configbool(b'ui', b'tweakdefaults'):
            return
        if self._tweaked or self.plain(b'tweakdefaults'):
            return

        # Note: it is SUPER IMPORTANT that you set self._tweaked to
        # True *before* any calls to setconfig(), otherwise you'll get
        # infinite recursion between setconfig and this method.
        #
        # TODO: We should extract an inner method in setconfig() to
        # avoid this weirdness.
        self._tweaked = True
        tmpcfg = config.config()
        tmpcfg.parse(b'<tweakdefaults>', tweakrc)
        for section in tmpcfg:
            for name, value in tmpcfg.items(section):
                if not self.hasconfig(section, name):
                    self.setconfig(section, name, value, b"<tweakdefaults>")

    def copy(self):
        return self.__class__(self)

    def resetstate(self):
        """Clear internal state that shouldn't persist across commands"""
        if self._progbar:
            self._progbar.resetstate()  # reset last-print time of progress bar
        self.httppasswordmgrdb = httppasswordmgrdbproxy()

    @contextlib.contextmanager
    def timeblockedsection(self, key):
        # this is open-coded below - search for timeblockedsection to find them
        starttime = util.timer()
        try:
            yield
        finally:
            self._blockedtimes[key + b'_blocked'] += (
                util.timer() - starttime
            ) * 1000

    @contextlib.contextmanager
    def uninterruptible(self):
        """Mark an operation as unsafe.

        Most operations on a repository are safe to interrupt, but a
        few are risky (for example repair.strip). This context manager
        lets you advise Mercurial that something risky is happening so
        that control-C etc can be blocked if desired.
        """
        enabled = self.configbool(b'experimental', b'nointerrupt')
        if enabled and self.configbool(
            b'experimental', b'nointerrupt-interactiveonly'
        ):
            enabled = self.interactive()
        if self._uninterruptible or not enabled:
            # if nointerrupt support is turned off, the process isn't
            # interactive, or we're already in an uninterruptible
            # block, do nothing.
            yield
            return

        def warn():
            self.warn(_(b"shutting down cleanly\n"))
            self.warn(
                _(b"press ^C again to terminate immediately (dangerous)\n")
            )
            return True

        with procutil.uninterruptible(warn):
            try:
                self._uninterruptible = True
                yield
            finally:
                self._uninterruptible = False

    def formatter(self, topic, opts):
        return formatter.formatter(self, self, topic, opts)

    def _trusted(self, fp, f):
        st = util.fstat(fp)
        if util.isowner(st):
            return True

        tusers, tgroups = self._trustusers, self._trustgroups
        if b'*' in tusers or b'*' in tgroups:
            return True

        user = util.username(st.st_uid)
        group = util.groupname(st.st_gid)
        if user in tusers or group in tgroups or user == util.username():
            return True

        if self._reportuntrusted:
            self.warn(
                _(
                    b'not trusting file %s from untrusted '
                    b'user %s, group %s\n'
                )
                % (f, user, group)
            )
        return False

    def read_resource_config(
        self, name, root=None, trust=False, sections=None, remap=None
    ):
        try:
            fp = resourceutil.open_resource(name[0], name[1])
        except IOError:
            if not sections:  # ignore unless we were looking for something
                return
            raise

        self._readconfig(
            b'resource:%s.%s' % name, fp, root, trust, sections, remap
        )

    def readconfig(
        self, filename, root=None, trust=False, sections=None, remap=None
    ):
        try:
            fp = open(filename, 'rb')
        except IOError:
            if not sections:  # ignore unless we were looking for something
                return
            raise

        self._readconfig(filename, fp, root, trust, sections, remap)

    def _readconfig(
        self, filename, fp, root=None, trust=False, sections=None, remap=None
    ):
        with fp:
            cfg = config.config()
            trusted = sections or trust or self._trusted(fp, filename)

            try:
                cfg.read(filename, fp, sections=sections, remap=remap)
            except error.ConfigError as inst:
                if trusted:
                    raise
                self.warn(
                    _(b'ignored %s: %s\n') % (inst.location, inst.message)
                )

        self._applyconfig(cfg, trusted, root)

    def applyconfig(self, configitems, source=b"", root=None):
        """Add configitems from a non-file source.  Unlike with ``setconfig()``,
        they can be overridden by subsequent config file reads.  The items are
        in the same format as ``configoverride()``, namely a dict of the
        following structures: {(section, name) : value}

        Typically this is used by extensions that inject themselves into the
        config file load procedure by monkeypatching ``localrepo.loadhgrc()``.
        """
        cfg = config.config()

        for (section, name), value in configitems.items():
            cfg.set(section, name, value, source)

        self._applyconfig(cfg, True, root)

    def _applyconfig(self, cfg, trusted, root):
        if self.plain():
            for k in (
                b'debug',
                b'fallbackencoding',
                b'quiet',
                b'slash',
                b'logtemplate',
                b'message-output',
                b'statuscopies',
                b'style',
                b'traceback',
                b'verbose',
            ):
                if k in cfg[b'ui']:
                    del cfg[b'ui'][k]
            for k, v in cfg.items(b'defaults'):
                del cfg[b'defaults'][k]
            for k, v in cfg.items(b'commands'):
                del cfg[b'commands'][k]
            for k, v in cfg.items(b'command-templates'):
                del cfg[b'command-templates'][k]
        # Don't remove aliases from the configuration if in the exceptionlist
        if self.plain(b'alias'):
            for k, v in cfg.items(b'alias'):
                del cfg[b'alias'][k]
        if self.plain(b'revsetalias'):
            for k, v in cfg.items(b'revsetalias'):
                del cfg[b'revsetalias'][k]
        if self.plain(b'templatealias'):
            for k, v in cfg.items(b'templatealias'):
                del cfg[b'templatealias'][k]

        if trusted:
            self._tcfg.update(cfg)
            self._tcfg.update(self._ocfg)
        self._ucfg.update(cfg)
        self._ucfg.update(self._ocfg)

        if root is None:
            root = os.path.expanduser(b'~')
        self.fixconfig(root=root)

    def fixconfig(self, root=None, section=None):
        if section in (None, b'paths'):
            # expand vars and ~
            # translate paths relative to root (or home) into absolute paths
            root = root or encoding.getcwd()
            for c in self._tcfg, self._ucfg, self._ocfg:
                for n, p in c.items(b'paths'):
                    old_p = p
                    s = self.configsource(b'paths', n) or b'none'
                    root_key = (n, p, s)
                    if root_key not in self._path_to_root:
                        self._path_to_root[root_key] = root
                    # Ignore sub-options.
                    if b':' in n:
                        continue
                    if not p:
                        continue
                    if b'%%' in p:
                        if s is None:
                            s = 'none'
                        self.warn(
                            _(b"(deprecated '%%' in path %s=%s from %s)\n")
                            % (n, p, s)
                        )
                        p = p.replace(b'%%', b'%')
                    if p != old_p:
                        c.alter(b"paths", n, p)

        if section in (None, b'ui'):
            # update ui options
            self._fmsgout, self._fmsgerr = _selectmsgdests(self)
            self.debugflag = self.configbool(b'ui', b'debug')
            self.verbose = self.debugflag or self.configbool(b'ui', b'verbose')
            self.quiet = not self.debugflag and self.configbool(b'ui', b'quiet')
            if self.verbose and self.quiet:
                self.quiet = self.verbose = False
            self._reportuntrusted = self.debugflag or self.configbool(
                b"ui", b"report_untrusted"
            )
            self.showtimestamp = self.configbool(b'ui', b'timestamp-output')
            self.tracebackflag = self.configbool(b'ui', b'traceback')
            self.logblockedtimes = self.configbool(b'ui', b'logblockedtimes')

        if section in (None, b'trusted'):
            # update trust information
            self._trustusers.update(self.configlist(b'trusted', b'users'))
            self._trustgroups.update(self.configlist(b'trusted', b'groups'))

        if section in (None, b'devel', b'ui') and self.debugflag:
            tracked = set()
            if self.configbool(b'devel', b'debug.extensions'):
                tracked.add(b'extension')
            if tracked:
                logger = loggingutil.fileobjectlogger(self._ferr, tracked)
                self.setlogger(b'debug', logger)

    def backupconfig(self, section, item):
        return (
            self._ocfg.backup(section, item),
            self._tcfg.backup(section, item),
            self._ucfg.backup(section, item),
        )

    def restoreconfig(self, data):
        self._ocfg.restore(data[0])
        self._tcfg.restore(data[1])
        self._ucfg.restore(data[2])

    def setconfig(self, section, name, value, source=b''):
        for cfg in (self._ocfg, self._tcfg, self._ucfg):
            cfg.set(section, name, value, source)
        self.fixconfig(section=section)
        self._maybetweakdefaults()

    def _data(self, untrusted):
        return untrusted and self._ucfg or self._tcfg

    def configsource(self, section, name, untrusted=False):
        return self._data(untrusted).source(section, name)

    def config(self, section, name, default=_unset, untrusted=False):
        """return the plain string version of a config"""
        value = self._config(
            section, name, default=default, untrusted=untrusted
        )
        if value is _unset:
            return None
        return value

    def _config(self, section, name, default=_unset, untrusted=False):
        value = itemdefault = default
        item = self._knownconfig.get(section, {}).get(name)
        alternates = [(section, name)]

        if item is not None:
            alternates.extend(item.alias)
            if callable(item.default):
                itemdefault = item.default()
            else:
                itemdefault = item.default
        else:
            msg = b"accessing unregistered config item: '%s.%s'"
            msg %= (section, name)
            self.develwarn(msg, 2, b'warn-config-unknown')

        if default is _unset:
            if item is None:
                value = default
            elif item.default is configitems.dynamicdefault:
                value = None
                msg = b"config item requires an explicit default value: '%s.%s'"
                msg %= (section, name)
                self.develwarn(msg, 2, b'warn-config-default')
            else:
                value = itemdefault
        elif (
            item is not None
            and item.default is not configitems.dynamicdefault
            and default != itemdefault
        ):
            msg = (
                b"specifying a mismatched default value for a registered "
                b"config item: '%s.%s' '%s'"
            )
            msg %= (section, name, pycompat.bytestr(default))
            self.develwarn(msg, 2, b'warn-config-default')

        candidates = []
        config = self._data(untrusted)
        for s, n in alternates:
            candidate = config.get(s, n, None)
            if candidate is not None:
                candidates.append((s, n, candidate))
        if candidates:

            def level(x):
                return config.level(x[0], x[1])

            value = max(candidates, key=level)[2]

        if self.debugflag and not untrusted and self._reportuntrusted:
            for s, n in alternates:
                uvalue = self._ucfg.get(s, n)
                if uvalue is not None and uvalue != value:
                    self.debug(
                        b"ignoring untrusted configuration option "
                        b"%s.%s = %s\n" % (s, n, uvalue)
                    )
        return value

    def config_default(self, section, name):
        """return the default value for a config option

        The default is returned "raw", for example if it is a callable, the
        callable was not called.
        """
        item = self._knownconfig.get(section, {}).get(name)

        if item is None:
            raise KeyError((section, name))
        return item.default

    def configsuboptions(self, section, name, default=_unset, untrusted=False):
        """Get a config option and all sub-options.

        Some config options have sub-options that are declared with the
        format "key:opt = value". This method is used to return the main
        option and all its declared sub-options.

        Returns a 2-tuple of ``(option, sub-options)``, where `sub-options``
        is a dict of defined sub-options where keys and values are strings.
        """
        main = self.config(section, name, default, untrusted=untrusted)
        data = self._data(untrusted)
        sub = {}
        prefix = b'%s:' % name
        for k, v in data.items(section):
            if k.startswith(prefix):
                sub[k[len(prefix) :]] = v

        if self.debugflag and not untrusted and self._reportuntrusted:
            for k, v in sub.items():
                uvalue = self._ucfg.get(section, b'%s:%s' % (name, k))
                if uvalue is not None and uvalue != v:
                    self.debug(
                        b'ignoring untrusted configuration option '
                        b'%s:%s.%s = %s\n' % (section, name, k, uvalue)
                    )

        return main, sub

    def configpath(self, section, name, default=_unset, untrusted=False):
        """get a path config item, expanded relative to repo root or config
        file"""
        v = self.config(section, name, default, untrusted)
        if v is None:
            return None
        if not os.path.isabs(v) or b"://" not in v:
            src = self.configsource(section, name, untrusted)
            if b':' in src:
                base = os.path.dirname(src.rsplit(b':')[0])
                v = os.path.join(base, os.path.expanduser(v))
        return v

    def configbool(self, section, name, default=_unset, untrusted=False):
        """parse a configuration element as a boolean

        >>> u = ui(); s = b'foo'
        >>> u.setconfig(s, b'true', b'yes')
        >>> u.configbool(s, b'true')
        True
        >>> u.setconfig(s, b'false', b'no')
        >>> u.configbool(s, b'false')
        False
        >>> u.configbool(s, b'unknown')
        False
        >>> u.configbool(s, b'unknown', True)
        True
        >>> u.setconfig(s, b'invalid', b'somevalue')
        >>> u.configbool(s, b'invalid')
        Traceback (most recent call last):
            ...
        ConfigError: foo.invalid is not a boolean ('somevalue')
        """

        v = self._config(section, name, default, untrusted=untrusted)
        if v is None:
            return v
        if v is _unset:
            if default is _unset:
                return False
            return default
        if isinstance(v, bool):
            return v
        b = stringutil.parsebool(v)
        if b is None:
            raise error.ConfigError(
                _(b"%s.%s is not a boolean ('%s')") % (section, name, v)
            )
        return b

    def configwith(
        self, convert, section, name, default=_unset, desc=None, untrusted=False
    ):
        """parse a configuration element with a conversion function

        >>> u = ui(); s = b'foo'
        >>> u.setconfig(s, b'float1', b'42')
        >>> u.configwith(float, s, b'float1')
        42.0
        >>> u.setconfig(s, b'float2', b'-4.25')
        >>> u.configwith(float, s, b'float2')
        -4.25
        >>> u.configwith(float, s, b'unknown', 7)
        7.0
        >>> u.setconfig(s, b'invalid', b'somevalue')
        >>> u.configwith(float, s, b'invalid')
        Traceback (most recent call last):
            ...
        ConfigError: foo.invalid is not a valid float ('somevalue')
        >>> u.configwith(float, s, b'invalid', desc=b'womble')
        Traceback (most recent call last):
            ...
        ConfigError: foo.invalid is not a valid womble ('somevalue')
        """

        v = self.config(section, name, default, untrusted)
        if v is None:
            return v  # do not attempt to convert None
        try:
            return convert(v)
        except (ValueError, error.ParseError):
            if desc is None:
                desc = pycompat.sysbytes(convert.__name__)
            raise error.ConfigError(
                _(b"%s.%s is not a valid %s ('%s')") % (section, name, desc, v)
            )

    def configint(self, section, name, default=_unset, untrusted=False):
        """parse a configuration element as an integer

        >>> u = ui(); s = b'foo'
        >>> u.setconfig(s, b'int1', b'42')
        >>> u.configint(s, b'int1')
        42
        >>> u.setconfig(s, b'int2', b'-42')
        >>> u.configint(s, b'int2')
        -42
        >>> u.configint(s, b'unknown', 7)
        7
        >>> u.setconfig(s, b'invalid', b'somevalue')
        >>> u.configint(s, b'invalid')
        Traceback (most recent call last):
            ...
        ConfigError: foo.invalid is not a valid integer ('somevalue')
        """

        return self.configwith(
            int, section, name, default, b'integer', untrusted
        )

    def configbytes(self, section, name, default=_unset, untrusted=False):
        """parse a configuration element as a quantity in bytes

        Units can be specified as b (bytes), k or kb (kilobytes), m or
        mb (megabytes), g or gb (gigabytes).

        >>> u = ui(); s = b'foo'
        >>> u.setconfig(s, b'val1', b'42')
        >>> u.configbytes(s, b'val1')
        42
        >>> u.setconfig(s, b'val2', b'42.5 kb')
        >>> u.configbytes(s, b'val2')
        43520
        >>> u.configbytes(s, b'unknown', b'7 MB')
        7340032
        >>> u.setconfig(s, b'invalid', b'somevalue')
        >>> u.configbytes(s, b'invalid')
        Traceback (most recent call last):
            ...
        ConfigError: foo.invalid is not a byte quantity ('somevalue')
        """

        value = self._config(section, name, default, untrusted)
        if value is _unset:
            if default is _unset:
                default = 0
            value = default
        if not isinstance(value, bytes):
            return value
        try:
            return util.sizetoint(value)
        except error.ParseError:
            raise error.ConfigError(
                _(b"%s.%s is not a byte quantity ('%s')")
                % (section, name, value)
            )

    def configlist(self, section, name, default=_unset, untrusted=False):
        """parse a configuration element as a list of comma/space separated
        strings

        >>> u = ui(); s = b'foo'
        >>> u.setconfig(s, b'list1', b'this,is "a small" ,test')
        >>> u.configlist(s, b'list1')
        ['this', 'is', 'a small', 'test']
        >>> u.setconfig(s, b'list2', b'this, is "a small" , test ')
        >>> u.configlist(s, b'list2')
        ['this', 'is', 'a small', 'test']
        """
        # default is not always a list
        v = self.configwith(
            stringutil.parselist, section, name, default, b'list', untrusted
        )
        if isinstance(v, bytes):
            return stringutil.parselist(v)
        elif v is None:
            return []
        return v

    def configdate(self, section, name, default=_unset, untrusted=False):
        """parse a configuration element as a tuple of ints

        >>> u = ui(); s = b'foo'
        >>> u.setconfig(s, b'date', b'0 0')
        >>> u.configdate(s, b'date')
        (0, 0)
        """
        if self.config(section, name, default, untrusted):
            return self.configwith(
                dateutil.parsedate, section, name, default, b'date', untrusted
            )
        if default is _unset:
            return None
        return default

    def configdefault(self, section, name):
        """returns the default value of the config item"""
        item = self._knownconfig.get(section, {}).get(name)
        itemdefault = None
        if item is not None:
            if callable(item.default):
                itemdefault = item.default()
            else:
                itemdefault = item.default
        return itemdefault

    def hasconfig(self, section, name, untrusted=False):
        return self._data(untrusted).hasitem(section, name)

    def has_section(self, section, untrusted=False):
        '''tell whether section exists in config.'''
        return section in self._data(untrusted)

    def configitems(self, section, untrusted=False, ignoresub=False):
        items = self._data(untrusted).items(section)
        if ignoresub:
            items = [i for i in items if b':' not in i[0]]
        if self.debugflag and not untrusted and self._reportuntrusted:
            for k, v in self._ucfg.items(section):
                if self._tcfg.get(section, k) != v:
                    self.debug(
                        b"ignoring untrusted configuration option "
                        b"%s.%s = %s\n" % (section, k, v)
                    )
        return items

    def walkconfig(self, untrusted=False, all_known=False):
        defined = self._walk_config(untrusted)
        if not all_known:
            for d in defined:
                yield d
            return
        known = self._walk_known()
        current_defined = next(defined, None)
        current_known = next(known, None)
        while current_defined is not None or current_known is not None:
            if current_defined is None:
                yield current_known
                current_known = next(known, None)
            elif current_known is None:
                yield current_defined
                current_defined = next(defined, None)
            elif current_known[0:2] == current_defined[0:2]:
                yield current_defined
                current_defined = next(defined, None)
                current_known = next(known, None)
            elif current_known[0:2] < current_defined[0:2]:
                yield current_known
                current_known = next(known, None)
            else:
                yield current_defined
                current_defined = next(defined, None)

    def _walk_known(self):
        for section, items in sorted(self._knownconfig.items()):
            for k, i in sorted(items.items()):
                # We don't have a way to display generic well, so skip them
                if i.generic:
                    continue
                if callable(i.default):
                    default = i.default()
                elif i.default is configitems.dynamicdefault:
                    default = b'<DYNAMIC>'
                else:
                    default = i.default
                yield section, i.name, default

    def _walk_config(self, untrusted):
        cfg = self._data(untrusted)
        for section in cfg.sections():
            for name, value in self.configitems(section, untrusted):
                yield section, name, value

    def plain(self, feature=None):
        """is plain mode active?

        Plain mode means that all configuration variables which affect
        the behavior and output of Mercurial should be
        ignored. Additionally, the output should be stable,
        reproducible and suitable for use in scripts or applications.

        The only way to trigger plain mode is by setting either the
        `HGPLAIN' or `HGPLAINEXCEPT' environment variables.

        The return value can either be
        - False if HGPLAIN is not set, or feature is in HGPLAINEXCEPT
        - False if feature is disabled by default and not included in HGPLAIN
        - True otherwise
        """
        if (
            b'HGPLAIN' not in encoding.environ
            and b'HGPLAINEXCEPT' not in encoding.environ
        ):
            return False
        exceptions = (
            encoding.environ.get(b'HGPLAINEXCEPT', b'').strip().split(b',')
        )
        # TODO: add support for HGPLAIN=+feature,-feature syntax
        if b'+strictflags' not in encoding.environ.get(b'HGPLAIN', b'').split(
            b','
        ):
            exceptions.append(b'strictflags')
        if feature and exceptions:
            return feature not in exceptions
        return True

    def username(self, acceptempty=False):
        """Return default username to be used in commits.

        Searched in this order: $HGUSER, [ui] section of hgrcs, $EMAIL
        and stop searching if one of these is set.
        If not found and acceptempty is True, returns None.
        If not found and ui.askusername is True, ask the user, else use
        ($LOGNAME or $USER or $LNAME or $USERNAME) + "@full.hostname".
        If no username could be found, raise an Abort error.
        """
        user = encoding.environ.get(b"HGUSER")
        if user is None:
            user = self.config(b"ui", b"username")
            if user is not None:
                user = os.path.expandvars(user)
        if user is None:
            user = encoding.environ.get(b"EMAIL")
        if user is None and acceptempty:
            return user
        if user is None and self.configbool(b"ui", b"askusername"):
            user = self.prompt(_(b"enter a commit username:"), default=None)
        if user is None and not self.interactive():
            try:
                user = b'%s@%s' % (
                    procutil.getuser(),
                    encoding.strtolocal(socket.getfqdn()),
                )
                self.warn(_(b"no username found, using '%s' instead\n") % user)
            except KeyError:
                pass
        if not user:
            raise error.Abort(
                _(b'no username supplied'),
                hint=_(b"use 'hg config --edit' " b'to set your username'),
            )
        if b"\n" in user:
            raise error.Abort(
                _(b"username %r contains a newline\n") % pycompat.bytestr(user)
            )
        return user

    def shortuser(self, user):
        """Return a short representation of a user name or email address."""
        if not self.verbose:
            user = stringutil.shortuser(user)
        return user

    def expandpath(self, loc, default=None):
        """Return repository location relative to cwd or from [paths]"""
        msg = b'ui.expandpath is deprecated, use `get_*` functions from urlutil'
        self.deprecwarn(msg, b'6.0')
        try:
            p = self.getpath(loc)
            if p:
                return p.rawloc
        except error.RepoError:
            pass

        if default:
            try:
                p = self.getpath(default)
                if p:
                    return p.rawloc
            except error.RepoError:
                pass

        return loc

    @util.propertycache
    def paths(self):
        return urlutil.paths(self)

    def getpath(self, *args, **kwargs):
        """see paths.getpath for details

        This method exist as `getpath` need a ui for potential warning message.
        """
        msg = b'ui.getpath is deprecated, use `get_*` functions from urlutil'
        self.deprecwarn(msg, b'6.0')
        return self.paths.getpath(self, *args, **kwargs)

    @property
    def fout(self):
        return self._fout

    @fout.setter
    def fout(self, f):
        self._fout = f
        self._fmsgout, self._fmsgerr = _selectmsgdests(self)

    @property
    def ferr(self):
        return self._ferr

    @ferr.setter
    def ferr(self, f):
        self._ferr = f
        self._fmsgout, self._fmsgerr = _selectmsgdests(self)

    @property
    def fin(self):
        return self._fin

    @fin.setter
    def fin(self, f):
        self._fin = f

    @property
    def fmsg(self):
        """Stream dedicated for status/error messages; may be None if
        fout/ferr are used"""
        return self._fmsg

    @fmsg.setter
    def fmsg(self, f):
        self._fmsg = f
        self._fmsgout, self._fmsgerr = _selectmsgdests(self)

    @contextlib.contextmanager
    def silent(self, error=False, subproc=False, labeled=False):
        self.pushbuffer(error=error, subproc=subproc, labeled=labeled)
        try:
            yield
        finally:
            self.popbuffer()

    def pushbuffer(self, error=False, subproc=False, labeled=False):
        """install a buffer to capture standard output of the ui object

        If error is True, the error output will be captured too.

        If subproc is True, output from subprocesses (typically hooks) will be
        captured too.

        If labeled is True, any labels associated with buffered
        output will be handled. By default, this has no effect
        on the output returned, but extensions and GUI tools may
        handle this argument and returned styled output. If output
        is being buffered so it can be captured and parsed or
        processed, labeled should not be set to True.
        """
        self._buffers.append([])
        self._bufferstates.append((error, subproc, labeled))
        self._bufferapplylabels = labeled

    def popbuffer(self):
        '''pop the last buffer and return the buffered output'''
        self._bufferstates.pop()
        if self._bufferstates:
            self._bufferapplylabels = self._bufferstates[-1][2]
        else:
            self._bufferapplylabels = None

        return b"".join(self._buffers.pop())

    def _isbuffered(self, dest):
        if dest is self._fout:
            return bool(self._buffers)
        if dest is self._ferr:
            return bool(self._bufferstates and self._bufferstates[-1][0])
        return False

    def canwritewithoutlabels(self):
        '''check if write skips the label'''
        if self._buffers and not self._bufferapplylabels:
            return True
        return self._colormode is None

    def canbatchlabeledwrites(self):
        '''check if write calls with labels are batchable'''
        # Windows color printing is special, see ``write``.
        return self._colormode != b'win32'

    def write(self, *args, **opts):
        """write args to output

        By default, this method simply writes to the buffer or stdout.
        Color mode can be set on the UI class to have the output decorated
        with color modifier before being written to stdout.

        The color used is controlled by an optional keyword argument, "label".
        This should be a string containing label names separated by space.
        Label names take the form of "topic.type". For example, ui.debug()
        issues a label of "ui.debug".

        Progress reports via stderr are normally cleared before writing as
        stdout and stderr go to the same terminal. This can be skipped with
        the optional keyword argument "keepprogressbar". The progress bar
        will continue to occupy a partial line on stderr in that case.
        This functionality is intended when Mercurial acts as data source
        in a pipe.

        When labeling output for a specific command, a label of
        "cmdname.type" is recommended. For example, status issues
        a label of "status.modified" for modified files.
        """
        dest = self._fout

        # inlined _write() for speed
        if self._buffers:
            label = opts.get('label', b'')
            if label and self._bufferapplylabels:
                self._buffers[-1].extend(self.label(a, label) for a in args)
            else:
                self._buffers[-1].extend(args)
            return

        # inlined _writenobuf() for speed
        if not opts.get('keepprogressbar', False):
            self._progclear()
        msg = b''.join(args)

        # opencode timeblockedsection because this is a critical path
        starttime = util.timer()
        try:
            if self._colormode == b'win32':
                # windows color printing is its own can of crab, defer to
                # the color module and that is it.
                color.win32print(self, dest.write, msg, **opts)
            else:
                if self._colormode is not None:
                    label = opts.get('label', b'')
                    msg = self.label(msg, label)
                dest.write(msg)
        except IOError as err:
            raise error.StdioError(err)
        finally:
            self._blockedtimes[b'stdio_blocked'] += (
                util.timer() - starttime
            ) * 1000

    def write_err(self, *args, **opts):
        self._write(self._ferr, *args, **opts)

    def _write(self, dest, *args, **opts):
        # update write() as well if you touch this code
        if self._isbuffered(dest):
            label = opts.get('label', b'')
            if label and self._bufferapplylabels:
                self._buffers[-1].extend(self.label(a, label) for a in args)
            else:
                self._buffers[-1].extend(args)
        else:
            self._writenobuf(dest, *args, **opts)

    def _writenobuf(self, dest, *args, **opts):
        # update write() as well if you touch this code
        if not opts.get('keepprogressbar', False):
            self._progclear()
        msg = b''.join(args)

        # opencode timeblockedsection because this is a critical path
        starttime = util.timer()
        try:
            if dest is self._ferr and not getattr(self._fout, 'closed', False):
                self._fout.flush()
            if getattr(dest, 'structured', False):
                # channel for machine-readable output with metadata, where
                # no extra colorization is necessary.
                dest.write(msg, **opts)
            elif self._colormode == b'win32':
                # windows color printing is its own can of crab, defer to
                # the color module and that is it.
                color.win32print(self, dest.write, msg, **opts)
            else:
                if self._colormode is not None:
                    label = opts.get('label', b'')
                    msg = self.label(msg, label)
                dest.write(msg)
            # stderr may be buffered under win32 when redirected to files,
            # including stdout.
            if dest is self._ferr and not getattr(dest, 'closed', False):
                dest.flush()
        except IOError as err:
            if dest is self._ferr and err.errno in (
                errno.EPIPE,
                errno.EIO,
                errno.EBADF,
            ):
                # no way to report the error, so ignore it
                return
            raise error.StdioError(err)
        finally:
            self._blockedtimes[b'stdio_blocked'] += (
                util.timer() - starttime
            ) * 1000

    def _writemsg(self, dest, *args, **opts):
        timestamp = self.showtimestamp and opts.get('type') in {
            b'debug',
            b'error',
            b'note',
            b'status',
            b'warning',
        }
        if timestamp:
            args = (
                b'[%s] '
                % pycompat.bytestr(datetime.datetime.now().isoformat()),
            ) + args
        _writemsgwith(self._write, dest, *args, **opts)
        if timestamp:
            dest.flush()

    def _writemsgnobuf(self, dest, *args, **opts):
        _writemsgwith(self._writenobuf, dest, *args, **opts)

    def flush(self):
        # opencode timeblockedsection because this is a critical path
        starttime = util.timer()
        try:
            try:
                self._fout.flush()
            except IOError as err:
                if err.errno not in (errno.EPIPE, errno.EIO, errno.EBADF):
                    raise error.StdioError(err)
            finally:
                try:
                    self._ferr.flush()
                except IOError as err:
                    if err.errno not in (errno.EPIPE, errno.EIO, errno.EBADF):
                        raise error.StdioError(err)
        finally:
            self._blockedtimes[b'stdio_blocked'] += (
                util.timer() - starttime
            ) * 1000

    def _isatty(self, fh):
        if self.configbool(b'ui', b'nontty'):
            return False
        return procutil.isatty(fh)

    def protectfinout(self):
        """Duplicate ui streams and redirect original if they are stdio

        Returns (fin, fout) which point to the original ui fds, but may be
        copy of them. The returned streams can be considered "owned" in that
        print(), exec(), etc. never reach to them.
        """
        if self._finoutredirected:
            # if already redirected, protectstdio() would just create another
            # nullfd pair, which is equivalent to returning self._fin/_fout.
            return self._fin, self._fout
        fin, fout = procutil.protectstdio(self._fin, self._fout)
        self._finoutredirected = (fin, fout) != (self._fin, self._fout)
        return fin, fout

    def restorefinout(self, fin, fout):
        """Restore ui streams from possibly duplicated (fin, fout)"""
        if (fin, fout) == (self._fin, self._fout):
            return
        procutil.restorestdio(self._fin, self._fout, fin, fout)
        # protectfinout() won't create more than one duplicated streams,
        # so we can just turn the redirection flag off.
        self._finoutredirected = False

    @contextlib.contextmanager
    def protectedfinout(self):
        """Run code block with protected standard streams"""
        fin, fout = self.protectfinout()
        try:
            yield fin, fout
        finally:
            self.restorefinout(fin, fout)

    def disablepager(self):
        self._disablepager = True

    def pager(self, command):
        """Start a pager for subsequent command output.

        Commands which produce a long stream of output should call
        this function to activate the user's preferred pagination
        mechanism (which may be no pager). Calling this function
        precludes any future use of interactive functionality, such as
        prompting the user or activating curses.

        Args:
          command: The full, non-aliased name of the command. That is, "log"
                   not "history, "summary" not "summ", etc.
        """
        if self._disablepager or self.pageractive:
            # how pager should do is already determined
            return

        if not command.startswith(b'internal-always-') and (
            # explicit --pager=on (= 'internal-always-' prefix) should
            # take precedence over disabling factors below
            command in self.configlist(b'pager', b'ignore')
            or not self.configbool(b'ui', b'paginate')
            or not self.configbool(b'pager', b'attend-' + command, True)
            or encoding.environ.get(b'TERM') == b'dumb'
            # TODO: if we want to allow HGPLAINEXCEPT=pager,
            # formatted() will need some adjustment.
            or not self.formatted()
            or self.plain()
            or self._buffers
            # TODO: expose debugger-enabled on the UI object
            or b'--debugger' in pycompat.sysargv
        ):
            # We only want to paginate if the ui appears to be
            # interactive, the user didn't say HGPLAIN or
            # HGPLAINEXCEPT=pager, and the user didn't specify --debug.
            return

        pagercmd = self.config(b'pager', b'pager', rcutil.fallbackpager)
        if not pagercmd:
            return

        pagerenv = {}
        for name, value in rcutil.defaultpagerenv().items():
            if name not in encoding.environ:
                pagerenv[name] = value

        self.debug(
            b'starting pager for command %s\n' % stringutil.pprint(command)
        )
        self.flush()

        wasformatted = self.formatted()
        if util.safehasattr(signal, b"SIGPIPE"):
            signal.signal(signal.SIGPIPE, _catchterm)
        if self._runpager(pagercmd, pagerenv):
            self.pageractive = True
            # Preserve the formatted-ness of the UI. This is important
            # because we mess with stdout, which might confuse
            # auto-detection of things being formatted.
            self.setconfig(b'ui', b'formatted', wasformatted, b'pager')
            self.setconfig(b'ui', b'interactive', False, b'pager')

            # If pagermode differs from color.mode, reconfigure color now that
            # pageractive is set.
            cm = self._colormode
            if cm != self.config(b'color', b'pagermode', cm):
                color.setup(self)
        else:
            # If the pager can't be spawned in dispatch when --pager=on is
            # given, don't try again when the command runs, to avoid a duplicate
            # warning about a missing pager command.
            self.disablepager()

    def _runpager(self, command, env=None):
        """Actually start the pager and set up file descriptors.

        This is separate in part so that extensions (like chg) can
        override how a pager is invoked.
        """
        if command == b'cat':
            # Save ourselves some work.
            return False
        # If the command doesn't contain any of these characters, we
        # assume it's a binary and exec it directly. This means for
        # simple pager command configurations, we can degrade
        # gracefully and tell the user about their broken pager.
        shell = any(c in command for c in b"|&;<>()$`\\\"' \t\n*?[#~=%")

        if pycompat.iswindows and not shell:
            # Window's built-in `more` cannot be invoked with shell=False, but
            # its `more.com` can.  Hide this implementation detail from the
            # user so we can also get sane bad PAGER behavior.  MSYS has
            # `more.exe`, so do a cmd.exe style resolution of the executable to
            # determine which one to use.
            fullcmd = procutil.findexe(command)
            if not fullcmd:
                self.warn(
                    _(b"missing pager command '%s', skipping pager\n") % command
                )
                return False

            command = fullcmd

        try:
            pager = subprocess.Popen(
                procutil.tonativestr(command),
                shell=shell,
                bufsize=-1,
                close_fds=procutil.closefds,
                stdin=subprocess.PIPE,
                stdout=procutil.stdout,
                stderr=procutil.stderr,
                env=procutil.tonativeenv(procutil.shellenviron(env)),
            )
        except OSError as e:
            if e.errno == errno.ENOENT and not shell:
                self.warn(
                    _(b"missing pager command '%s', skipping pager\n") % command
                )
                return False
            raise

        # back up original file descriptors
        stdoutfd = os.dup(procutil.stdout.fileno())
        stderrfd = os.dup(procutil.stderr.fileno())

        os.dup2(pager.stdin.fileno(), procutil.stdout.fileno())
        if self._isatty(procutil.stderr):
            os.dup2(pager.stdin.fileno(), procutil.stderr.fileno())

        @self.atexit
        def killpager():
            if util.safehasattr(signal, b"SIGINT"):
                signal.signal(signal.SIGINT, signal.SIG_IGN)
            # restore original fds, closing pager.stdin copies in the process
            os.dup2(stdoutfd, procutil.stdout.fileno())
            os.dup2(stderrfd, procutil.stderr.fileno())
            pager.stdin.close()
            pager.wait()

        return True

    @property
    def _exithandlers(self):
        return _reqexithandlers

    def atexit(self, func, *args, **kwargs):
        """register a function to run after dispatching a request

        Handlers do not stay registered across request boundaries."""
        self._exithandlers.append((func, args, kwargs))
        return func

    def interface(self, feature):
        """what interface to use for interactive console features?

        The interface is controlled by the value of `ui.interface` but also by
        the value of feature-specific configuration. For example:

        ui.interface.histedit = text
        ui.interface.chunkselector = curses

        Here the features are "histedit" and "chunkselector".

        The configuration above means that the default interfaces for commands
        is curses, the interface for histedit is text and the interface for
        selecting chunk is crecord (the best curses interface available).

        Consider the following example:
        ui.interface = curses
        ui.interface.histedit = text

        Then histedit will use the text interface and chunkselector will use
        the default curses interface (crecord at the moment).
        """
        alldefaults = frozenset([b"text", b"curses"])

        featureinterfaces = {
            b"chunkselector": [
                b"text",
                b"curses",
            ],
            b"histedit": [
                b"text",
                b"curses",
            ],
        }

        # Feature-specific interface
        if feature not in featureinterfaces.keys():
            # Programming error, not user error
            raise ValueError(b"Unknown feature requested %s" % feature)

        availableinterfaces = frozenset(featureinterfaces[feature])
        if alldefaults > availableinterfaces:
            # Programming error, not user error. We need a use case to
            # define the right thing to do here.
            raise ValueError(
                b"Feature %s does not handle all default interfaces" % feature
            )

        if self.plain() or encoding.environ.get(b'TERM') == b'dumb':
            return b"text"

        # Default interface for all the features
        defaultinterface = b"text"
        i = self.config(b"ui", b"interface")
        if i in alldefaults:
            defaultinterface = i

        choseninterface = defaultinterface
        f = self.config(b"ui", b"interface.%s" % feature)
        if f in availableinterfaces:
            choseninterface = f

        if i is not None and defaultinterface != i:
            if f is not None:
                self.warn(_(b"invalid value for ui.interface: %s\n") % (i,))
            else:
                self.warn(
                    _(b"invalid value for ui.interface: %s (using %s)\n")
                    % (i, choseninterface)
                )
        if f is not None and choseninterface != f:
            self.warn(
                _(b"invalid value for ui.interface.%s: %s (using %s)\n")
                % (feature, f, choseninterface)
            )

        return choseninterface

    def interactive(self):
        """is interactive input allowed?

        An interactive session is a session where input can be reasonably read
        from `sys.stdin'. If this function returns false, any attempt to read
        from stdin should fail with an error, unless a sensible default has been
        specified.

        Interactiveness is triggered by the value of the `ui.interactive'
        configuration variable or - if it is unset - when `sys.stdin' points
        to a terminal device.

        This function refers to input only; for output, see `ui.formatted()'.
        """
        i = self.configbool(b"ui", b"interactive")
        if i is None:
            # some environments replace stdin without implementing isatty
            # usually those are non-interactive
            return self._isatty(self._fin)

        return i

    def termwidth(self):
        """how wide is the terminal in columns?"""
        if b'COLUMNS' in encoding.environ:
            try:
                return int(encoding.environ[b'COLUMNS'])
            except ValueError:
                pass
        return scmutil.termsize(self)[0]

    def formatted(self):
        """should formatted output be used?

        It is often desirable to format the output to suite the output medium.
        Examples of this are truncating long lines or colorizing messages.
        However, this is not often not desirable when piping output into other
        utilities, e.g. `grep'.

        Formatted output is triggered by the value of the `ui.formatted'
        configuration variable or - if it is unset - when `sys.stdout' points
        to a terminal device. Please note that `ui.formatted' should be
        considered an implementation detail; it is not intended for use outside
        Mercurial or its extensions.

        This function refers to output only; for input, see `ui.interactive()'.
        This function always returns false when in plain mode, see `ui.plain()'.
        """
        if self.plain():
            return False

        i = self.configbool(b"ui", b"formatted")
        if i is None:
            # some environments replace stdout without implementing isatty
            # usually those are non-interactive
            return self._isatty(self._fout)

        return i

    def _readline(self, prompt=b' ', promptopts=None):
        # Replacing stdin/stdout temporarily is a hard problem on Python 3
        # because they have to be text streams with *no buffering*. Instead,
        # we use rawinput() only if call_readline() will be invoked by
        # PyOS_Readline(), so no I/O will be made at Python layer.
        usereadline = (
            self._isatty(self._fin)
            and self._isatty(self._fout)
            and procutil.isstdin(self._fin)
            and procutil.isstdout(self._fout)
        )
        if usereadline:
            try:
                # magically add command line editing support, where
                # available
                import readline

                # force demandimport to really load the module
                readline.read_history_file
                # windows sometimes raises something other than ImportError
            except Exception:
                usereadline = False

        if self._colormode == b'win32' or not usereadline:
            if not promptopts:
                promptopts = {}
            self._writemsgnobuf(
                self._fmsgout, prompt, type=b'prompt', **promptopts
            )
            self.flush()
            prompt = b' '
        else:
            prompt = self.label(prompt, b'ui.prompt') + b' '

        # prompt ' ' must exist; otherwise readline may delete entire line
        # - http://bugs.python.org/issue12833
        with self.timeblockedsection(b'stdio'):
            if usereadline:
                self.flush()
                prompt = encoding.strfromlocal(prompt)
                line = encoding.strtolocal(pycompat.rawinput(prompt))
                # When stdin is in binary mode on Windows, it can cause
                # raw_input() to emit an extra trailing carriage return
                if pycompat.oslinesep == b'\r\n' and line.endswith(b'\r'):
                    line = line[:-1]
            else:
                self._fout.write(pycompat.bytestr(prompt))
                self._fout.flush()
                line = self._fin.readline()
                if not line:
                    raise EOFError
                line = line.rstrip(pycompat.oslinesep)

        return line

    def prompt(self, msg, default=b"y"):
        """Prompt user with msg, read response.
        If ui is not interactive, the default is returned.
        """
        return self._prompt(msg, default=default)

    def _prompt(self, msg, **opts):
        default = opts['default']
        if not self.interactive():
            self._writemsg(self._fmsgout, msg, b' ', type=b'prompt', **opts)
            self._writemsg(
                self._fmsgout, default or b'', b"\n", type=b'promptecho'
            )
            return default
        try:
            r = self._readline(prompt=msg, promptopts=opts)
            if not r:
                r = default
            if self.configbool(b'ui', b'promptecho'):
                self._writemsg(
                    self._fmsgout, r or b'', b"\n", type=b'promptecho'
                )
            return r
        except EOFError:
            raise error.ResponseExpected()

    @staticmethod
    def extractchoices(prompt):
        """Extract prompt message and list of choices from specified prompt.

        This returns tuple "(message, choices)", and "choices" is the
        list of tuple "(response character, text without &)".

        >>> ui.extractchoices(b"awake? $$ &Yes $$ &No")
        ('awake? ', [('y', 'Yes'), ('n', 'No')])
        >>> ui.extractchoices(b"line\\nbreak? $$ &Yes $$ &No")
        ('line\\nbreak? ', [('y', 'Yes'), ('n', 'No')])
        >>> ui.extractchoices(b"want lots of $$money$$?$$Ye&s$$N&o")
        ('want lots of $$money$$?', [('s', 'Yes'), ('o', 'No')])
        """

        # Sadly, the prompt string may have been built with a filename
        # containing "$$" so let's try to find the first valid-looking
        # prompt to start parsing. Sadly, we also can't rely on
        # choices containing spaces, ASCII, or basically anything
        # except an ampersand followed by a character.
        m = re.match(br'(?s)(.+?)\$\$([^$]*&[^ $].*)', prompt)
        msg = m.group(1)
        choices = [p.strip(b' ') for p in m.group(2).split(b'$$')]

        def choicetuple(s):
            ampidx = s.index(b'&')
            return s[ampidx + 1 : ampidx + 2].lower(), s.replace(b'&', b'', 1)

        return (msg, [choicetuple(s) for s in choices])

    def promptchoice(self, prompt, default=0):
        """Prompt user with a message, read response, and ensure it matches
        one of the provided choices. The prompt is formatted as follows:

           "would you like fries with that (Yn)? $$ &Yes $$ &No"

        The index of the choice is returned. Responses are case
        insensitive. If ui is not interactive, the default is
        returned.
        """

        msg, choices = self.extractchoices(prompt)
        resps = [r for r, t in choices]
        while True:
            r = self._prompt(msg, default=resps[default], choices=choices)
            if r.lower() in resps:
                return resps.index(r.lower())
            # TODO: shouldn't it be a warning?
            self._writemsg(self._fmsgout, _(b"unrecognized response\n"))

    def getpass(self, prompt=None, default=None):
        if not self.interactive():
            return default
        try:
            self._writemsg(
                self._fmsgerr,
                prompt or _(b'password: '),
                type=b'prompt',
                password=True,
            )
            # disable getpass() only if explicitly specified. it's still valid
            # to interact with tty even if fin is not a tty.
            with self.timeblockedsection(b'stdio'):
                if self.configbool(b'ui', b'nontty'):
                    l = self._fin.readline()
                    if not l:
                        raise EOFError
                    return l.rstrip(b'\n')
                else:
                    return util.get_password()
        except EOFError:
            raise error.ResponseExpected()

    def status(self, *msg, **opts):
        """write status message to output (if ui.quiet is False)

        This adds an output label of "ui.status".
        """
        if not self.quiet:
            self._writemsg(self._fmsgout, type=b'status', *msg, **opts)

    def warn(self, *msg, **opts):
        """write warning message to output (stderr)

        This adds an output label of "ui.warning".
        """
        self._writemsg(self._fmsgerr, type=b'warning', *msg, **opts)

    def error(self, *msg, **opts):
        """write error message to output (stderr)

        This adds an output label of "ui.error".
        """
        self._writemsg(self._fmsgerr, type=b'error', *msg, **opts)

    def note(self, *msg, **opts):
        """write note to output (if ui.verbose is True)

        This adds an output label of "ui.note".
        """
        if self.verbose:
            self._writemsg(self._fmsgout, type=b'note', *msg, **opts)

    def debug(self, *msg, **opts):
        """write debug message to output (if ui.debugflag is True)

        This adds an output label of "ui.debug".
        """
        if self.debugflag:
            self._writemsg(self._fmsgout, type=b'debug', *msg, **opts)
            self.log(b'debug', b'%s', b''.join(msg))

    # Aliases to defeat check-code.
    statusnoi18n = status
    notenoi18n = note
    warnnoi18n = warn
    writenoi18n = write

    def edit(
        self,
        text,
        user,
        extra=None,
        editform=None,
        pending=None,
        repopath=None,
        action=None,
    ):
        if action is None:
            self.develwarn(
                b'action is None but will soon be a required '
                b'parameter to ui.edit()'
            )
        extra_defaults = {
            b'prefix': b'editor',
            b'suffix': b'.txt',
        }
        if extra is not None:
            if extra.get(b'suffix') is not None:
                self.develwarn(
                    b'extra.suffix is not None but will soon be '
                    b'ignored by ui.edit()'
                )
            extra_defaults.update(extra)
        extra = extra_defaults

        if action == b'diff':
            suffix = b'.diff'
        elif action:
            suffix = b'.%s.hg.txt' % action
        else:
            suffix = extra[b'suffix']

        rdir = None
        if self.configbool(b'experimental', b'editortmpinhg'):
            rdir = repopath
        (fd, name) = pycompat.mkstemp(
            prefix=b'hg-' + extra[b'prefix'] + b'-', suffix=suffix, dir=rdir
        )
        try:
            with os.fdopen(fd, 'wb') as f:
                f.write(util.tonativeeol(text))

            environ = {b'HGUSER': user}
            if b'transplant_source' in extra:
                environ.update(
                    {b'HGREVISION': hex(extra[b'transplant_source'])}
                )
            for label in (b'intermediate-source', b'source', b'rebase_source'):
                if label in extra:
                    environ.update({b'HGREVISION': extra[label]})
                    break
            if editform:
                environ.update({b'HGEDITFORM': editform})
            if pending:
                environ.update({b'HG_PENDING': pending})

            editor = self.geteditor()

            self.system(
                b"%s \"%s\"" % (editor, name),
                environ=environ,
                onerr=error.CanceledError,
                errprefix=_(b"edit failed"),
                blockedtag=b'editor',
            )

            with open(name, 'rb') as f:
                t = util.fromnativeeol(f.read())
        finally:
            os.unlink(name)

        return t

    def system(
        self,
        cmd,
        environ=None,
        cwd=None,
        onerr=None,
        errprefix=None,
        blockedtag=None,
    ):
        """execute shell command with appropriate output stream. command
        output will be redirected if fout is not stdout.

        if command fails and onerr is None, return status, else raise onerr
        object as exception.
        """
        if blockedtag is None:
            # Long cmds tend to be because of an absolute path on cmd. Keep
            # the tail end instead
            cmdsuffix = cmd.translate(None, _keepalnum)[-85:]
            blockedtag = b'unknown_system_' + cmdsuffix
        out = self._fout
        if any(s[1] for s in self._bufferstates):
            out = self
        with self.timeblockedsection(blockedtag):
            rc = self._runsystem(cmd, environ=environ, cwd=cwd, out=out)
        if rc and onerr:
            errmsg = b'%s %s' % (
                procutil.shellsplit(cmd)[0],
                procutil.explainexit(rc),
            )
            if errprefix:
                errmsg = b'%s: %s' % (errprefix, errmsg)
            raise onerr(errmsg)
        return rc

    def _runsystem(self, cmd, environ, cwd, out):
        """actually execute the given shell command (can be overridden by
        extensions like chg)"""
        return procutil.system(cmd, environ=environ, cwd=cwd, out=out)

    def traceback(self, exc=None, force=False):
        """print exception traceback if traceback printing enabled or forced.
        only to call in exception handler. returns true if traceback
        printed."""
        if self.tracebackflag or force:
            if exc is None:
                exc = sys.exc_info()
            cause = getattr(exc[1], 'cause', None)

            if cause is not None:
                causetb = traceback.format_tb(cause[2])
                exctb = traceback.format_tb(exc[2])
                exconly = traceback.format_exception_only(cause[0], cause[1])

                # exclude frame where 'exc' was chained and rethrown from exctb
                self.write_err(
                    b'Traceback (most recent call last):\n',
                    encoding.strtolocal(''.join(exctb[:-1])),
                    encoding.strtolocal(''.join(causetb)),
                    encoding.strtolocal(''.join(exconly)),
                )
            else:
                output = traceback.format_exception(exc[0], exc[1], exc[2])
                self.write_err(encoding.strtolocal(''.join(output)))
        return self.tracebackflag or force

    def geteditor(self):
        '''return editor to use'''
        if pycompat.sysplatform == b'plan9':
            # vi is the MIPS instruction simulator on Plan 9. We
            # instead default to E to plumb commit messages to
            # avoid confusion.
            editor = b'E'
        elif pycompat.isdarwin:
            # vi on darwin is POSIX compatible to a fault, and that includes
            # exiting non-zero if you make any mistake when running an ex
            # command. Proof: `vi -c ':unknown' -c ':qa'; echo $?` produces 1,
            # while s/vi/vim/ doesn't.
            editor = b'vim'
        else:
            editor = b'vi'
        return encoding.environ.get(b"HGEDITOR") or self.config(
            b"ui", b"editor", editor
        )

    @util.propertycache
    def _progbar(self):
        """setup the progbar singleton to the ui object"""
        if (
            self.quiet
            or self.debugflag
            or self.configbool(b'progress', b'disable')
            or not progress.shouldprint(self)
        ):
            return None
        return getprogbar(self)

    def _progclear(self):
        """clear progress bar output if any. use it before any output"""
        if not haveprogbar():  # nothing loaded yet
            return
        if self._progbar is not None and self._progbar.printed:
            self._progbar.clear()

    def makeprogress(self, topic, unit=b"", total=None):
        """Create a progress helper for the specified topic"""
        if getattr(self._fmsgerr, 'structured', False):
            # channel for machine-readable output with metadata, just send
            # raw information
            # TODO: consider porting some useful information (e.g. estimated
            # time) from progbar. we might want to support update delay to
            # reduce the cost of transferring progress messages.
            def updatebar(topic, pos, item, unit, total):
                self._fmsgerr.write(
                    None,
                    type=b'progress',
                    topic=topic,
                    pos=pos,
                    item=item,
                    unit=unit,
                    total=total,
                )

        elif self._progbar is not None:
            updatebar = self._progbar.progress
        else:

            def updatebar(topic, pos, item, unit, total):
                pass

        return scmutil.progress(self, updatebar, topic, unit, total)

    def getlogger(self, name):
        """Returns a logger of the given name; or None if not registered"""
        return self._loggers.get(name)

    def setlogger(self, name, logger):
        """Install logger which can be identified later by the given name

        More than one loggers can be registered. Use extension or module
        name to uniquely identify the logger instance.
        """
        self._loggers[name] = logger

    def log(self, event, msgfmt, *msgargs, **opts):
        """hook for logging facility extensions

        event should be a readily-identifiable subsystem, which will
        allow filtering.

        msgfmt should be a newline-terminated format string to log, and
        *msgargs are %-formatted into it.

        **opts currently has no defined meanings.
        """
        if not self._loggers:
            return
        activeloggers = [
            l for l in pycompat.itervalues(self._loggers) if l.tracked(event)
        ]
        if not activeloggers:
            return
        msg = msgfmt % msgargs
        opts = pycompat.byteskwargs(opts)
        # guard against recursion from e.g. ui.debug()
        registeredloggers = self._loggers
        self._loggers = {}
        try:
            for logger in activeloggers:
                logger.log(self, event, msg, opts)
        finally:
            self._loggers = registeredloggers

    def label(self, msg, label):
        """style msg based on supplied label

        If some color mode is enabled, this will add the necessary control
        characters to apply such color. In addition, 'debug' color mode adds
        markup showing which label affects a piece of text.

        ui.write(s, 'label') is equivalent to
        ui.write(ui.label(s, 'label')).
        """
        if self._colormode is not None:
            return color.colorlabel(self, msg, label)
        return msg

    def develwarn(self, msg, stacklevel=1, config=None):
        """issue a developer warning message

        Use 'stacklevel' to report the offender some layers further up in the
        stack.
        """
        if not self.configbool(b'devel', b'all-warnings'):
            if config is None or not self.configbool(b'devel', config):
                return
        msg = b'devel-warn: ' + msg
        stacklevel += 1  # get in develwarn
        if self.tracebackflag:
            util.debugstacktrace(msg, stacklevel, self._ferr, self._fout)
            self.log(
                b'develwarn',
                b'%s at:\n%s'
                % (msg, b''.join(util.getstackframes(stacklevel))),
            )
        else:
            curframe = inspect.currentframe()
            calframe = inspect.getouterframes(curframe, 2)
            fname, lineno, fmsg = calframe[stacklevel][1:4]
            fname, fmsg = pycompat.sysbytes(fname), pycompat.sysbytes(fmsg)
            self.write_err(b'%s at: %s:%d (%s)\n' % (msg, fname, lineno, fmsg))
            self.log(
                b'develwarn', b'%s at: %s:%d (%s)\n', msg, fname, lineno, fmsg
            )

            # avoid cycles
            del curframe
            del calframe

    def deprecwarn(self, msg, version, stacklevel=2):
        """issue a deprecation warning

        - msg: message explaining what is deprecated and how to upgrade,
        - version: last version where the API will be supported,
        """
        if not (
            self.configbool(b'devel', b'all-warnings')
            or self.configbool(b'devel', b'deprec-warn')
        ):
            return
        msg += (
            b"\n(compatibility will be dropped after Mercurial-%s,"
            b" update your code.)"
        ) % version
        self.develwarn(msg, stacklevel=stacklevel, config=b'deprec-warn')

    def exportableenviron(self):
        """The environment variables that are safe to export, e.g. through
        hgweb.
        """
        return self._exportableenviron

    @contextlib.contextmanager
    def configoverride(self, overrides, source=b""):
        """Context manager for temporary config overrides
        `overrides` must be a dict of the following structure:
        {(section, name) : value}"""
        backups = {}
        try:
            for (section, name), value in overrides.items():
                backups[(section, name)] = self.backupconfig(section, name)
                self.setconfig(section, name, value, source)
            yield
        finally:
            for __, backup in backups.items():
                self.restoreconfig(backup)
            # just restoring ui.quiet config to the previous value is not enough
            # as it does not update ui.quiet class member
            if (b'ui', b'quiet') in overrides:
                self.fixconfig(section=b'ui')

    def estimatememory(self):
        """Provide an estimate for the available system memory in Bytes.

        This can be overriden via ui.available-memory. It returns None, if
        no estimate can be computed.
        """
        value = self.config(b'ui', b'available-memory')
        if value is not None:
            try:
                return util.sizetoint(value)
            except error.ParseError:
                raise error.ConfigError(
                    _(b"ui.available-memory value is invalid ('%s')") % value
                )
        return util._estimatememory()


# we instantiate one globally shared progress bar to avoid
# competing progress bars when multiple UI objects get created
_progresssingleton = None


def getprogbar(ui):
    global _progresssingleton
    if _progresssingleton is None:
        # passing 'ui' object to the singleton is fishy,
        # this is how the extension used to work but feel free to rework it.
        _progresssingleton = progress.progbar(ui)
    return _progresssingleton


def haveprogbar():
    return _progresssingleton is not None


def _selectmsgdests(ui):
    name = ui.config(b'ui', b'message-output')
    if name == b'channel':
        if ui.fmsg:
            return ui.fmsg, ui.fmsg
        else:
            # fall back to ferr if channel isn't ready so that status/error
            # messages can be printed
            return ui.ferr, ui.ferr
    if name == b'stdio':
        return ui.fout, ui.ferr
    if name == b'stderr':
        return ui.ferr, ui.ferr
    raise error.Abort(b'invalid ui.message-output destination: %s' % name)


def _writemsgwith(write, dest, *args, **opts):
    """Write ui message with the given ui._write*() function

    The specified message type is translated to 'ui.<type>' label if the dest
    isn't a structured channel, so that the message will be colorized.
    """
    # TODO: maybe change 'type' to a mandatory option
    if 'type' in opts and not getattr(dest, 'structured', False):
        opts['label'] = opts.get('label', b'') + b' ui.%s' % opts.pop('type')
    write(dest, *args, **opts)
