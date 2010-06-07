# ui.py - user interface bits for mercurial
#
# Copyright 2005-2007 Matt Mackall <mpm@selenic.com>
#
# This software may be used and distributed according to the terms of the
# GNU General Public License version 2 or any later version.

from i18n import _
import errno, getpass, os, socket, sys, tempfile, traceback
import config, util, error

_booleans = {'1': True, 'yes': True, 'true': True, 'on': True,
             '0': False, 'no': False, 'false': False, 'off': False}

class ui(object):
    def __init__(self, src=None):
        self._buffers = []
        self.quiet = self.verbose = self.debugflag = self.tracebackflag = False
        self._reportuntrusted = True
        self._ocfg = config.config() # overlay
        self._tcfg = config.config() # trusted
        self._ucfg = config.config() # untrusted
        self._trustusers = set()
        self._trustgroups = set()

        if src:
            self._tcfg = src._tcfg.copy()
            self._ucfg = src._ucfg.copy()
            self._ocfg = src._ocfg.copy()
            self._trustusers = src._trustusers.copy()
            self._trustgroups = src._trustgroups.copy()
            self.environ = src.environ
            self.fixconfig()
        else:
            # shared read-only environment
            self.environ = os.environ
            # we always trust global config files
            for f in util.rcpath():
                self.readconfig(f, trust=True)

    def copy(self):
        return self.__class__(self)

    def _is_trusted(self, fp, f):
        st = util.fstat(fp)
        if util.isowner(st):
            return True

        tusers, tgroups = self._trustusers, self._trustgroups
        if '*' in tusers or '*' in tgroups:
            return True

        user = util.username(st.st_uid)
        group = util.groupname(st.st_gid)
        if user in tusers or group in tgroups or user == util.username():
            return True

        if self._reportuntrusted:
            self.warn(_('Not trusting file %s from untrusted '
                        'user %s, group %s\n') % (f, user, group))
        return False

    def readconfig(self, filename, root=None, trust=False,
                   sections=None, remap=None):
        try:
            fp = open(filename)
        except IOError:
            if not sections: # ignore unless we were looking for something
                return
            raise

        cfg = config.config()
        trusted = sections or trust or self._is_trusted(fp, filename)

        try:
            cfg.read(filename, fp, sections=sections, remap=remap)
        except error.ConfigError, inst:
            if trusted:
                raise
            self.warn(_("Ignored: %s\n") % str(inst))

        if self.plain():
            for k in ('debug', 'fallbackencoding', 'quiet', 'slash',
                      'logtemplate', 'style',
                      'traceback', 'verbose'):
                if k in cfg['ui']:
                    del cfg['ui'][k]
            for k, v in cfg.items('alias'):
                del cfg['alias'][k]
            for k, v in cfg.items('defaults'):
                del cfg['defaults'][k]

        if trusted:
            self._tcfg.update(cfg)
            self._tcfg.update(self._ocfg)
        self._ucfg.update(cfg)
        self._ucfg.update(self._ocfg)

        if root is None:
            root = os.path.expanduser('~')
        self.fixconfig(root=root)

    def fixconfig(self, root=None):
        # translate paths relative to root (or home) into absolute paths
        root = root or os.getcwd()
        for c in self._tcfg, self._ucfg, self._ocfg:
            for n, p in c.items('paths'):
                if p and "://" not in p and not os.path.isabs(p):
                    c.set("paths", n, os.path.normpath(os.path.join(root, p)))

        # update ui options
        self.debugflag = self.configbool('ui', 'debug')
        self.verbose = self.debugflag or self.configbool('ui', 'verbose')
        self.quiet = not self.debugflag and self.configbool('ui', 'quiet')
        if self.verbose and self.quiet:
            self.quiet = self.verbose = False
        self._reportuntrusted = self.configbool("ui", "report_untrusted", True)
        self.tracebackflag = self.configbool('ui', 'traceback', False)

        # update trust information
        self._trustusers.update(self.configlist('trusted', 'users'))
        self._trustgroups.update(self.configlist('trusted', 'groups'))

    def setconfig(self, section, name, value):
        for cfg in (self._ocfg, self._tcfg, self._ucfg):
            cfg.set(section, name, value)
        self.fixconfig()

    def _data(self, untrusted):
        return untrusted and self._ucfg or self._tcfg

    def configsource(self, section, name, untrusted=False):
        return self._data(untrusted).source(section, name) or 'none'

    def config(self, section, name, default=None, untrusted=False):
        value = self._data(untrusted).get(section, name, default)
        if self.debugflag and not untrusted and self._reportuntrusted:
            uvalue = self._ucfg.get(section, name)
            if uvalue is not None and uvalue != value:
                self.debug(_("ignoring untrusted configuration option "
                             "%s.%s = %s\n") % (section, name, uvalue))
        return value

    def configbool(self, section, name, default=False, untrusted=False):
        v = self.config(section, name, None, untrusted)
        if v is None:
            return default
        if isinstance(v, bool):
            return v
        if v.lower() not in _booleans:
            raise error.ConfigError(_("%s.%s not a boolean ('%s')")
                                    % (section, name, v))
        return _booleans[v.lower()]

    def configlist(self, section, name, default=None, untrusted=False):
        """Return a list of comma/space separated strings"""
        result = self.config(section, name, untrusted=untrusted)
        if result is None:
            result = default or []
        if isinstance(result, basestring):
            result = result.replace(",", " ").split()
        return result

    def has_section(self, section, untrusted=False):
        '''tell whether section exists in config.'''
        return section in self._data(untrusted)

    def configitems(self, section, untrusted=False):
        items = self._data(untrusted).items(section)
        if self.debugflag and not untrusted and self._reportuntrusted:
            for k, v in self._ucfg.items(section):
                if self._tcfg.get(section, k) != v:
                    self.debug(_("ignoring untrusted configuration option "
                                "%s.%s = %s\n") % (section, k, v))
        return items

    def walkconfig(self, untrusted=False):
        cfg = self._data(untrusted)
        for section in cfg.sections():
            for name, value in self.configitems(section, untrusted):
                yield section, name, str(value).replace('\n', '\\n')

    def plain(self):
        return 'HGPLAIN' in os.environ

    def username(self):
        """Return default username to be used in commits.

        Searched in this order: $HGUSER, [ui] section of hgrcs, $EMAIL
        and stop searching if one of these is set.
        If not found and ui.askusername is True, ask the user, else use
        ($LOGNAME or $USER or $LNAME or $USERNAME) + "@full.hostname".
        """
        user = os.environ.get("HGUSER")
        if user is None:
            user = self.config("ui", "username")
        if user is None:
            user = os.environ.get("EMAIL")
        if user is None and self.configbool("ui", "askusername"):
            user = self.prompt(_("enter a commit username:"), default=None)
        if user is None and not self.interactive():
            try:
                user = '%s@%s' % (util.getuser(), socket.getfqdn())
                self.warn(_("No username found, using '%s' instead\n") % user)
            except KeyError:
                pass
        if not user:
            raise util.Abort(_('no username supplied (see "hg help config")'))
        if "\n" in user:
            raise util.Abort(_("username %s contains a newline\n") % repr(user))
        return user

    def shortuser(self, user):
        """Return a short representation of a user name or email address."""
        if not self.verbose:
            user = util.shortuser(user)
        return user

    def _path(self, loc):
        p = self.config('paths', loc)
        if p:
            if '%%' in p:
                self.warn("(deprecated '%%' in path %s=%s from %s)\n" %
                          (loc, p, self.configsource('paths', loc)))
                p = p.replace('%%', '%')
            p = util.expandpath(p)
        return p

    def expandpath(self, loc, default=None):
        """Return repository location relative to cwd or from [paths]"""
        if "://" in loc or os.path.isdir(os.path.join(loc, '.hg')):
            return loc

        path = self._path(loc)
        if not path and default is not None:
            path = self._path(default)
        return path or loc

    def pushbuffer(self):
        self._buffers.append([])

    def popbuffer(self):
        return "".join(self._buffers.pop())

    def write(self, *args):
        if self._buffers:
            self._buffers[-1].extend([str(a) for a in args])
        else:
            for a in args:
                sys.stdout.write(str(a))

    def write_err(self, *args):
        try:
            if not getattr(sys.stdout, 'closed', False):
                sys.stdout.flush()
            for a in args:
                sys.stderr.write(str(a))
            # stderr may be buffered under win32 when redirected to files,
            # including stdout.
            if not getattr(sys.stderr, 'closed', False):
                sys.stderr.flush()
        except IOError, inst:
            if inst.errno != errno.EPIPE:
                raise

    def flush(self):
        try: sys.stdout.flush()
        except: pass
        try: sys.stderr.flush()
        except: pass

    def interactive(self):
        i = self.configbool("ui", "interactive", None)
        if i is None:
            try:
                return sys.stdin.isatty()
            except AttributeError:
                # some environments replace stdin without implementing isatty
                # usually those are non-interactive
                return False

        return i

    def _readline(self, prompt=''):
        if sys.stdin.isatty():
            try:
                # magically add command line editing support, where
                # available
                import readline
                # force demandimport to really load the module
                readline.read_history_file
                # windows sometimes raises something other than ImportError
            except Exception:
                pass
        line = raw_input(prompt)
        # When stdin is in binary mode on Windows, it can cause
        # raw_input() to emit an extra trailing carriage return
        if os.linesep == '\r\n' and line and line[-1] == '\r':
            line = line[:-1]
        return line

    def prompt(self, msg, default="y"):
        """Prompt user with msg, read response.
        If ui is not interactive, the default is returned.
        """
        if not self.interactive():
            self.write(msg, ' ', default, "\n")
            return default
        try:
            r = self._readline(msg + ' ')
            if not r:
                return default
            return r
        except EOFError:
            raise util.Abort(_('response expected'))

    def promptchoice(self, msg, choices, default=0):
        """Prompt user with msg, read response, and ensure it matches
        one of the provided choices. The index of the choice is returned.
        choices is a sequence of acceptable responses with the format:
        ('&None', 'E&xec', 'Sym&link') Responses are case insensitive.
        If ui is not interactive, the default is returned.
        """
        resps = [s[s.index('&')+1].lower() for s in choices]
        while True:
            r = self.prompt(msg, resps[default])
            if r.lower() in resps:
                return resps.index(r.lower())
            self.write(_("unrecognized response\n"))

    def getpass(self, prompt=None, default=None):
        if not self.interactive():
            return default
        try:
            return getpass.getpass(prompt or _('password: '))
        except EOFError:
            raise util.Abort(_('response expected'))
    def status(self, *msg):
        if not self.quiet:
            self.write(*msg)
    def warn(self, *msg):
        self.write_err(*msg)
    def note(self, *msg):
        if self.verbose:
            self.write(*msg)
    def debug(self, *msg):
        if self.debugflag:
            self.write(*msg)
    def edit(self, text, user):
        (fd, name) = tempfile.mkstemp(prefix="hg-editor-", suffix=".txt",
                                      text=True)
        try:
            f = os.fdopen(fd, "w")
            f.write(text)
            f.close()

            editor = self.geteditor()

            util.system("%s \"%s\"" % (editor, name),
                        environ={'HGUSER': user},
                        onerr=util.Abort, errprefix=_("edit failed"))

            f = open(name)
            t = f.read()
            f.close()
        finally:
            os.unlink(name)

        return t

    def traceback(self, exc=None):
        '''print exception traceback if traceback printing enabled.
        only to call in exception handler. returns true if traceback
        printed.'''
        if self.tracebackflag:
            if exc:
                traceback.print_exception(exc[0], exc[1], exc[2])
            else:
                traceback.print_exc()
        return self.tracebackflag

    def geteditor(self):
        '''return editor to use'''
        return (os.environ.get("HGEDITOR") or
                self.config("ui", "editor") or
                os.environ.get("VISUAL") or
                os.environ.get("EDITOR", "vi"))

    def progress(self, topic, pos, item="", unit="", total=None):
        '''show a progress message

        With stock hg, this is simply a debug message that is hidden
        by default, but with extensions or GUI tools it may be
        visible. 'topic' is the current operation, 'item' is a
        non-numeric marker of the current position (ie the currently
        in-process file), 'pos' is the current numeric position (ie
        revision, bytes, etc.), unit is a corresponding unit label,
        and total is the highest expected pos.

        Multiple nested topics may be active at a time.

        All topics should be marked closed by setting pos to None at
        termination.
        '''

        if pos == None or not self.debugflag:
            return

        if unit:
            unit = ' ' + unit
        if item:
            item = ' ' + item

        if total:
            pct = 100.0 * pos / total
            self.debug('%s:%s %s/%s%s (%4.2f%%)\n'
                     % (topic, item, pos, total, unit, pct))
        else:
            self.debug('%s:%s %s%s\n' % (topic, item, pos, unit))
