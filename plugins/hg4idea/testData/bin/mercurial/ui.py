# ui.py - user interface bits for mercurial
#
# Copyright 2005-2007 Matt Mackall <mpm@selenic.com>
#
# This software may be used and distributed according to the terms of the
# GNU General Public License version 2 or any later version.

from i18n import _
import errno, getpass, os, re, socket, sys, tempfile, traceback
import config, scmutil, util, error, formatter

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
        self.callhooks = True

        if src:
            self.fout = src.fout
            self.ferr = src.ferr
            self.fin = src.fin

            self._tcfg = src._tcfg.copy()
            self._ucfg = src._ucfg.copy()
            self._ocfg = src._ocfg.copy()
            self._trustusers = src._trustusers.copy()
            self._trustgroups = src._trustgroups.copy()
            self.environ = src.environ
            self.callhooks = src.callhooks
            self.fixconfig()
        else:
            self.fout = sys.stdout
            self.ferr = sys.stderr
            self.fin = sys.stdin

            # shared read-only environment
            self.environ = os.environ
            # we always trust global config files
            for f in scmutil.rcpath():
                self.readconfig(f, trust=True)

    def copy(self):
        return self.__class__(self)

    def formatter(self, topic, opts):
        return formatter.formatter(self, topic, opts)

    def _trusted(self, fp, f):
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
            self.warn(_('not trusting file %s from untrusted '
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
        trusted = sections or trust or self._trusted(fp, filename)

        try:
            cfg.read(filename, fp, sections=sections, remap=remap)
            fp.close()
        except error.ConfigError, inst:
            if trusted:
                raise
            self.warn(_("ignored: %s\n") % str(inst))

        if self.plain():
            for k in ('debug', 'fallbackencoding', 'quiet', 'slash',
                      'logtemplate', 'style',
                      'traceback', 'verbose'):
                if k in cfg['ui']:
                    del cfg['ui'][k]
            for k, v in cfg.items('defaults'):
                del cfg['defaults'][k]
        # Don't remove aliases from the configuration if in the exceptionlist
        if self.plain('alias'):
            for k, v in cfg.items('alias'):
                del cfg['alias'][k]

        if trusted:
            self._tcfg.update(cfg)
            self._tcfg.update(self._ocfg)
        self._ucfg.update(cfg)
        self._ucfg.update(self._ocfg)

        if root is None:
            root = os.path.expanduser('~')
        self.fixconfig(root=root)

    def fixconfig(self, root=None, section=None):
        if section in (None, 'paths'):
            # expand vars and ~
            # translate paths relative to root (or home) into absolute paths
            root = root or os.getcwd()
            for c in self._tcfg, self._ucfg, self._ocfg:
                for n, p in c.items('paths'):
                    if not p:
                        continue
                    if '%%' in p:
                        self.warn(_("(deprecated '%%' in path %s=%s from %s)\n")
                                  % (n, p, self.configsource('paths', n)))
                        p = p.replace('%%', '%')
                    p = util.expandpath(p)
                    if not util.hasscheme(p) and not os.path.isabs(p):
                        p = os.path.normpath(os.path.join(root, p))
                    c.set("paths", n, p)

        if section in (None, 'ui'):
            # update ui options
            self.debugflag = self.configbool('ui', 'debug')
            self.verbose = self.debugflag or self.configbool('ui', 'verbose')
            self.quiet = not self.debugflag and self.configbool('ui', 'quiet')
            if self.verbose and self.quiet:
                self.quiet = self.verbose = False
            self._reportuntrusted = self.debugflag or self.configbool("ui",
                "report_untrusted", True)
            self.tracebackflag = self.configbool('ui', 'traceback', False)

        if section in (None, 'trusted'):
            # update trust information
            self._trustusers.update(self.configlist('trusted', 'users'))
            self._trustgroups.update(self.configlist('trusted', 'groups'))

    def backupconfig(self, section, item):
        return (self._ocfg.backup(section, item),
                self._tcfg.backup(section, item),
                self._ucfg.backup(section, item),)
    def restoreconfig(self, data):
        self._ocfg.restore(data[0])
        self._tcfg.restore(data[1])
        self._ucfg.restore(data[2])

    def setconfig(self, section, name, value, overlay=True):
        if overlay:
            self._ocfg.set(section, name, value)
        self._tcfg.set(section, name, value)
        self._ucfg.set(section, name, value)
        self.fixconfig(section=section)

    def _data(self, untrusted):
        return untrusted and self._ucfg or self._tcfg

    def configsource(self, section, name, untrusted=False):
        return self._data(untrusted).source(section, name) or 'none'

    def config(self, section, name, default=None, untrusted=False):
        if isinstance(name, list):
            alternates = name
        else:
            alternates = [name]

        for n in alternates:
            value = self._data(untrusted).get(section, name, None)
            if value is not None:
                name = n
                break
        else:
            value = default

        if self.debugflag and not untrusted and self._reportuntrusted:
            uvalue = self._ucfg.get(section, name)
            if uvalue is not None and uvalue != value:
                self.debug("ignoring untrusted configuration option "
                           "%s.%s = %s\n" % (section, name, uvalue))
        return value

    def configpath(self, section, name, default=None, untrusted=False):
        'get a path config item, expanded relative to repo root or config file'
        v = self.config(section, name, default, untrusted)
        if v is None:
            return None
        if not os.path.isabs(v) or "://" not in v:
            src = self.configsource(section, name, untrusted)
            if ':' in src:
                base = os.path.dirname(src.rsplit(':')[0])
                v = os.path.join(base, os.path.expanduser(v))
        return v

    def configbool(self, section, name, default=False, untrusted=False):
        """parse a configuration element as a boolean

        >>> u = ui(); s = 'foo'
        >>> u.setconfig(s, 'true', 'yes')
        >>> u.configbool(s, 'true')
        True
        >>> u.setconfig(s, 'false', 'no')
        >>> u.configbool(s, 'false')
        False
        >>> u.configbool(s, 'unknown')
        False
        >>> u.configbool(s, 'unknown', True)
        True
        >>> u.setconfig(s, 'invalid', 'somevalue')
        >>> u.configbool(s, 'invalid')
        Traceback (most recent call last):
            ...
        ConfigError: foo.invalid is not a boolean ('somevalue')
        """

        v = self.config(section, name, None, untrusted)
        if v is None:
            return default
        if isinstance(v, bool):
            return v
        b = util.parsebool(v)
        if b is None:
            raise error.ConfigError(_("%s.%s is not a boolean ('%s')")
                                    % (section, name, v))
        return b

    def configint(self, section, name, default=None, untrusted=False):
        """parse a configuration element as an integer

        >>> u = ui(); s = 'foo'
        >>> u.setconfig(s, 'int1', '42')
        >>> u.configint(s, 'int1')
        42
        >>> u.setconfig(s, 'int2', '-42')
        >>> u.configint(s, 'int2')
        -42
        >>> u.configint(s, 'unknown', 7)
        7
        >>> u.setconfig(s, 'invalid', 'somevalue')
        >>> u.configint(s, 'invalid')
        Traceback (most recent call last):
            ...
        ConfigError: foo.invalid is not an integer ('somevalue')
        """

        v = self.config(section, name, None, untrusted)
        if v is None:
            return default
        try:
            return int(v)
        except ValueError:
            raise error.ConfigError(_("%s.%s is not an integer ('%s')")
                                    % (section, name, v))

    def configbytes(self, section, name, default=0, untrusted=False):
        """parse a configuration element as a quantity in bytes

        Units can be specified as b (bytes), k or kb (kilobytes), m or
        mb (megabytes), g or gb (gigabytes).

        >>> u = ui(); s = 'foo'
        >>> u.setconfig(s, 'val1', '42')
        >>> u.configbytes(s, 'val1')
        42
        >>> u.setconfig(s, 'val2', '42.5 kb')
        >>> u.configbytes(s, 'val2')
        43520
        >>> u.configbytes(s, 'unknown', '7 MB')
        7340032
        >>> u.setconfig(s, 'invalid', 'somevalue')
        >>> u.configbytes(s, 'invalid')
        Traceback (most recent call last):
            ...
        ConfigError: foo.invalid is not a byte quantity ('somevalue')
        """

        orig = string = self.config(section, name)
        if orig is None:
            if not isinstance(default, str):
                return default
            orig = string = default
        multiple = 1
        m = re.match(r'([^kmbg]+?)\s*([kmg]?)b?$', string, re.I)
        if m:
            string, key = m.groups()
            key = key.lower()
            multiple = dict(k=1024, m=1048576, g=1073741824).get(key, 1)
        try:
            return int(float(string) * multiple)
        except ValueError:
            raise error.ConfigError(_("%s.%s is not a byte quantity ('%s')")
                                    % (section, name, orig))

    def configlist(self, section, name, default=None, untrusted=False):
        """parse a configuration element as a list of comma/space separated
        strings

        >>> u = ui(); s = 'foo'
        >>> u.setconfig(s, 'list1', 'this,is "a small" ,test')
        >>> u.configlist(s, 'list1')
        ['this', 'is', 'a small', 'test']
        """

        def _parse_plain(parts, s, offset):
            whitespace = False
            while offset < len(s) and (s[offset].isspace() or s[offset] == ','):
                whitespace = True
                offset += 1
            if offset >= len(s):
                return None, parts, offset
            if whitespace:
                parts.append('')
            if s[offset] == '"' and not parts[-1]:
                return _parse_quote, parts, offset + 1
            elif s[offset] == '"' and parts[-1][-1] == '\\':
                parts[-1] = parts[-1][:-1] + s[offset]
                return _parse_plain, parts, offset + 1
            parts[-1] += s[offset]
            return _parse_plain, parts, offset + 1

        def _parse_quote(parts, s, offset):
            if offset < len(s) and s[offset] == '"': # ""
                parts.append('')
                offset += 1
                while offset < len(s) and (s[offset].isspace() or
                        s[offset] == ','):
                    offset += 1
                return _parse_plain, parts, offset

            while offset < len(s) and s[offset] != '"':
                if (s[offset] == '\\' and offset + 1 < len(s)
                        and s[offset + 1] == '"'):
                    offset += 1
                    parts[-1] += '"'
                else:
                    parts[-1] += s[offset]
                offset += 1

            if offset >= len(s):
                real_parts = _configlist(parts[-1])
                if not real_parts:
                    parts[-1] = '"'
                else:
                    real_parts[0] = '"' + real_parts[0]
                    parts = parts[:-1]
                    parts.extend(real_parts)
                return None, parts, offset

            offset += 1
            while offset < len(s) and s[offset] in [' ', ',']:
                offset += 1

            if offset < len(s):
                if offset + 1 == len(s) and s[offset] == '"':
                    parts[-1] += '"'
                    offset += 1
                else:
                    parts.append('')
            else:
                return None, parts, offset

            return _parse_plain, parts, offset

        def _configlist(s):
            s = s.rstrip(' ,')
            if not s:
                return []
            parser, parts, offset = _parse_plain, [''], 0
            while parser:
                parser, parts, offset = parser(parts, s, offset)
            return parts

        result = self.config(section, name, untrusted=untrusted)
        if result is None:
            result = default or []
        if isinstance(result, basestring):
            result = _configlist(result.lstrip(' ,\n'))
            if result is None:
                result = default or []
        return result

    def has_section(self, section, untrusted=False):
        '''tell whether section exists in config.'''
        return section in self._data(untrusted)

    def configitems(self, section, untrusted=False):
        items = self._data(untrusted).items(section)
        if self.debugflag and not untrusted and self._reportuntrusted:
            for k, v in self._ucfg.items(section):
                if self._tcfg.get(section, k) != v:
                    self.debug("ignoring untrusted configuration option "
                               "%s.%s = %s\n" % (section, k, v))
        return items

    def walkconfig(self, untrusted=False):
        cfg = self._data(untrusted)
        for section in cfg.sections():
            for name, value in self.configitems(section, untrusted):
                yield section, name, value

    def plain(self, feature=None):
        '''is plain mode active?

        Plain mode means that all configuration variables which affect
        the behavior and output of Mercurial should be
        ignored. Additionally, the output should be stable,
        reproducible and suitable for use in scripts or applications.

        The only way to trigger plain mode is by setting either the
        `HGPLAIN' or `HGPLAINEXCEPT' environment variables.

        The return value can either be
        - False if HGPLAIN is not set, or feature is in HGPLAINEXCEPT
        - True otherwise
        '''
        if 'HGPLAIN' not in os.environ and 'HGPLAINEXCEPT' not in os.environ:
            return False
        exceptions = os.environ.get('HGPLAINEXCEPT', '').strip().split(',')
        if feature and exceptions:
            return feature not in exceptions
        return True

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
            if user is not None:
                user = os.path.expandvars(user)
        if user is None:
            user = os.environ.get("EMAIL")
        if user is None and self.configbool("ui", "askusername"):
            user = self.prompt(_("enter a commit username:"), default=None)
        if user is None and not self.interactive():
            try:
                user = '%s@%s' % (util.getuser(), socket.getfqdn())
                self.warn(_("no username found, using '%s' instead\n") % user)
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

    def expandpath(self, loc, default=None):
        """Return repository location relative to cwd or from [paths]"""
        if util.hasscheme(loc) or os.path.isdir(os.path.join(loc, '.hg')):
            return loc

        path = self.config('paths', loc)
        if not path and default is not None:
            path = self.config('paths', default)
        return path or loc

    def pushbuffer(self):
        self._buffers.append([])

    def popbuffer(self, labeled=False):
        '''pop the last buffer and return the buffered output

        If labeled is True, any labels associated with buffered
        output will be handled. By default, this has no effect
        on the output returned, but extensions and GUI tools may
        handle this argument and returned styled output. If output
        is being buffered so it can be captured and parsed or
        processed, labeled should not be set to True.
        '''
        return "".join(self._buffers.pop())

    def write(self, *args, **opts):
        '''write args to output

        By default, this method simply writes to the buffer or stdout,
        but extensions or GUI tools may override this method,
        write_err(), popbuffer(), and label() to style output from
        various parts of hg.

        An optional keyword argument, "label", can be passed in.
        This should be a string containing label names separated by
        space. Label names take the form of "topic.type". For example,
        ui.debug() issues a label of "ui.debug".

        When labeling output for a specific command, a label of
        "cmdname.type" is recommended. For example, status issues
        a label of "status.modified" for modified files.
        '''
        if self._buffers:
            self._buffers[-1].extend([str(a) for a in args])
        else:
            for a in args:
                self.fout.write(str(a))

    def write_err(self, *args, **opts):
        try:
            if not getattr(self.fout, 'closed', False):
                self.fout.flush()
            for a in args:
                self.ferr.write(str(a))
            # stderr may be buffered under win32 when redirected to files,
            # including stdout.
            if not getattr(self.ferr, 'closed', False):
                self.ferr.flush()
        except IOError, inst:
            if inst.errno not in (errno.EPIPE, errno.EIO, errno.EBADF):
                raise

    def flush(self):
        try: self.fout.flush()
        except (IOError, ValueError): pass
        try: self.ferr.flush()
        except (IOError, ValueError): pass

    def _isatty(self, fh):
        if self.configbool('ui', 'nontty', False):
            return False
        return util.isatty(fh)

    def interactive(self):
        '''is interactive input allowed?

        An interactive session is a session where input can be reasonably read
        from `sys.stdin'. If this function returns false, any attempt to read
        from stdin should fail with an error, unless a sensible default has been
        specified.

        Interactiveness is triggered by the value of the `ui.interactive'
        configuration variable or - if it is unset - when `sys.stdin' points
        to a terminal device.

        This function refers to input only; for output, see `ui.formatted()'.
        '''
        i = self.configbool("ui", "interactive", None)
        if i is None:
            # some environments replace stdin without implementing isatty
            # usually those are non-interactive
            return self._isatty(self.fin)

        return i

    def termwidth(self):
        '''how wide is the terminal in columns?
        '''
        if 'COLUMNS' in os.environ:
            try:
                return int(os.environ['COLUMNS'])
            except ValueError:
                pass
        return util.termwidth()

    def formatted(self):
        '''should formatted output be used?

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
        '''
        if self.plain():
            return False

        i = self.configbool("ui", "formatted", None)
        if i is None:
            # some environments replace stdout without implementing isatty
            # usually those are non-interactive
            return self._isatty(self.fout)

        return i

    def _readline(self, prompt=''):
        if self._isatty(self.fin):
            try:
                # magically add command line editing support, where
                # available
                import readline
                # force demandimport to really load the module
                readline.read_history_file
                # windows sometimes raises something other than ImportError
            except Exception:
                pass

        # call write() so output goes through subclassed implementation
        # e.g. color extension on Windows
        self.write(prompt)

        # instead of trying to emulate raw_input, swap (self.fin,
        # self.fout) with (sys.stdin, sys.stdout)
        oldin = sys.stdin
        oldout = sys.stdout
        sys.stdin = self.fin
        sys.stdout = self.fout
        line = raw_input(' ')
        sys.stdin = oldin
        sys.stdout = oldout

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
            r = self._readline(self.label(msg, 'ui.prompt'))
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
        resps = [s[s.index('&') + 1].lower() for s in choices]
        while True:
            r = self.prompt(msg, resps[default])
            if r.lower() in resps:
                return resps.index(r.lower())
            self.write(_("unrecognized response\n"))

    def getpass(self, prompt=None, default=None):
        if not self.interactive():
            return default
        try:
            self.write(self.label(prompt or _('password: '), 'ui.prompt'))
            return getpass.getpass('')
        except EOFError:
            raise util.Abort(_('response expected'))
    def status(self, *msg, **opts):
        '''write status message to output (if ui.quiet is False)

        This adds an output label of "ui.status".
        '''
        if not self.quiet:
            opts['label'] = opts.get('label', '') + ' ui.status'
            self.write(*msg, **opts)
    def warn(self, *msg, **opts):
        '''write warning message to output (stderr)

        This adds an output label of "ui.warning".
        '''
        opts['label'] = opts.get('label', '') + ' ui.warning'
        self.write_err(*msg, **opts)
    def note(self, *msg, **opts):
        '''write note to output (if ui.verbose is True)

        This adds an output label of "ui.note".
        '''
        if self.verbose:
            opts['label'] = opts.get('label', '') + ' ui.note'
            self.write(*msg, **opts)
    def debug(self, *msg, **opts):
        '''write debug message to output (if ui.debugflag is True)

        This adds an output label of "ui.debug".
        '''
        if self.debugflag:
            opts['label'] = opts.get('label', '') + ' ui.debug'
            self.write(*msg, **opts)
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
                        onerr=util.Abort, errprefix=_("edit failed"),
                        out=self.fout)

            f = open(name)
            t = f.read()
            f.close()
        finally:
            os.unlink(name)

        return t

    def traceback(self, exc=None, force=False):
        '''print exception traceback if traceback printing enabled or forced.
        only to call in exception handler. returns true if traceback
        printed.'''
        if self.tracebackflag or force:
            if exc is None:
                exc = sys.exc_info()
            cause = getattr(exc[1], 'cause', None)

            if cause is not None:
                causetb = traceback.format_tb(cause[2])
                exctb = traceback.format_tb(exc[2])
                exconly = traceback.format_exception_only(cause[0], cause[1])

                # exclude frame where 'exc' was chained and rethrown from exctb
                self.write_err('Traceback (most recent call last):\n',
                               ''.join(exctb[:-1]),
                               ''.join(causetb),
                               ''.join(exconly))
            else:
                traceback.print_exception(exc[0], exc[1], exc[2],
                                          file=self.ferr)
        return self.tracebackflag or force

    def geteditor(self):
        '''return editor to use'''
        if sys.platform == 'plan9':
            # vi is the MIPS instruction simulator on Plan 9. We
            # instead default to E to plumb commit messages to
            # avoid confusion.
            editor = 'E'
        else:
            editor = 'vi'
        return (os.environ.get("HGEDITOR") or
                self.config("ui", "editor") or
                os.environ.get("VISUAL") or
                os.environ.get("EDITOR", editor))

    def progress(self, topic, pos, item="", unit="", total=None):
        '''show a progress message

        With stock hg, this is simply a debug message that is hidden
        by default, but with extensions or GUI tools it may be
        visible. 'topic' is the current operation, 'item' is a
        non-numeric marker of the current position (i.e. the currently
        in-process file), 'pos' is the current numeric position (i.e.
        revision, bytes, etc.), unit is a corresponding unit label,
        and total is the highest expected pos.

        Multiple nested topics may be active at a time.

        All topics should be marked closed by setting pos to None at
        termination.
        '''

        if pos is None or not self.debugflag:
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

    def log(self, service, *msg, **opts):
        '''hook for logging facility extensions

        service should be a readily-identifiable subsystem, which will
        allow filtering.
        message should be a newline-terminated string to log.
        '''
        pass

    def label(self, msg, label):
        '''style msg based on supplied label

        Like ui.write(), this just returns msg unchanged, but extensions
        and GUI tools can override it to allow styling output without
        writing it.

        ui.write(s, 'label') is equivalent to
        ui.write(ui.label(s, 'label')).
        '''
        return msg
