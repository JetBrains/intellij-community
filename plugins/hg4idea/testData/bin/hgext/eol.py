"""automatically manage newlines in repository files

This extension allows you to manage the type of line endings (CRLF or
LF) that are used in the repository and in the local working
directory. That way you can get CRLF line endings on Windows and LF on
Unix/Mac, thereby letting everybody use their OS native line endings.

The extension reads its configuration from a versioned ``.hgeol``
configuration file found in the root of the working copy. The
``.hgeol`` file use the same syntax as all other Mercurial
configuration files. It uses two sections, ``[patterns]`` and
``[repository]``.

The ``[patterns]`` section specifies how line endings should be
converted between the working copy and the repository. The format is
specified by a file pattern. The first match is used, so put more
specific patterns first. The available line endings are ``LF``,
``CRLF``, and ``BIN``.

Files with the declared format of ``CRLF`` or ``LF`` are always
checked out and stored in the repository in that format and files
declared to be binary (``BIN``) are left unchanged. Additionally,
``native`` is an alias for checking out in the platform's default line
ending: ``LF`` on Unix (including Mac OS X) and ``CRLF`` on
Windows. Note that ``BIN`` (do nothing to line endings) is Mercurial's
default behaviour; it is only needed if you need to override a later,
more general pattern.

The optional ``[repository]`` section specifies the line endings to
use for files stored in the repository. It has a single setting,
``native``, which determines the storage line endings for files
declared as ``native`` in the ``[patterns]`` section. It can be set to
``LF`` or ``CRLF``. The default is ``LF``. For example, this means
that on Windows, files configured as ``native`` (``CRLF`` by default)
will be converted to ``LF`` when stored in the repository. Files
declared as ``LF``, ``CRLF``, or ``BIN`` in the ``[patterns]`` section
are always stored as-is in the repository.

Example versioned ``.hgeol`` file::

  [patterns]
  **.py = native
  **.vcproj = CRLF
  **.txt = native
  Makefile = LF
  **.jpg = BIN

  [repository]
  native = LF

.. note::
   The rules will first apply when files are touched in the working
   copy, e.g. by updating to null and back to tip to touch all files.

The extension uses an optional ``[eol]`` section read from both the
normal Mercurial configuration files and the ``.hgeol`` file, with the
latter overriding the former. You can use that section to control the
overall behavior. There are three settings:

- ``eol.native`` (default ``os.linesep``) can be set to ``LF`` or
  ``CRLF`` to override the default interpretation of ``native`` for
  checkout. This can be used with :hg:`archive` on Unix, say, to
  generate an archive where files have line endings for Windows.

- ``eol.only-consistent`` (default True) can be set to False to make
  the extension convert files with inconsistent EOLs. Inconsistent
  means that there is both ``CRLF`` and ``LF`` present in the file.
  Such files are normally not touched under the assumption that they
  have mixed EOLs on purpose.

- ``eol.fix-trailing-newline`` (default False) can be set to True to
  ensure that converted files end with a EOL character (either ``\\n``
  or ``\\r\\n`` as per the configured patterns).

The extension provides ``cleverencode:`` and ``cleverdecode:`` filters
like the deprecated win32text extension does. This means that you can
disable win32text and enable eol and your filters will still work. You
only need to these filters until you have prepared a ``.hgeol`` file.

The ``win32text.forbid*`` hooks provided by the win32text extension
have been unified into a single hook named ``eol.checkheadshook``. The
hook will lookup the expected line endings from the ``.hgeol`` file,
which means you must migrate to a ``.hgeol`` file first before using
the hook. ``eol.checkheadshook`` only checks heads, intermediate
invalid revisions will be pushed. To forbid them completely, use the
``eol.checkallhook`` hook. These hooks are best used as
``pretxnchangegroup`` hooks.

See :hg:`help patterns` for more information about the glob patterns
used.
"""

from mercurial.i18n import _
from mercurial import util, config, extensions, match, error
import re, os

testedwith = 'internal'

# Matches a lone LF, i.e., one that is not part of CRLF.
singlelf = re.compile('(^|[^\r])\n')
# Matches a single EOL which can either be a CRLF where repeated CR
# are removed or a LF. We do not care about old Macintosh files, so a
# stray CR is an error.
eolre = re.compile('\r*\n')


def inconsistenteol(data):
    return '\r\n' in data and singlelf.search(data)

def tolf(s, params, ui, **kwargs):
    """Filter to convert to LF EOLs."""
    if util.binary(s):
        return s
    if ui.configbool('eol', 'only-consistent', True) and inconsistenteol(s):
        return s
    if (ui.configbool('eol', 'fix-trailing-newline', False)
        and s and s[-1] != '\n'):
        s = s + '\n'
    return eolre.sub('\n', s)

def tocrlf(s, params, ui, **kwargs):
    """Filter to convert to CRLF EOLs."""
    if util.binary(s):
        return s
    if ui.configbool('eol', 'only-consistent', True) and inconsistenteol(s):
        return s
    if (ui.configbool('eol', 'fix-trailing-newline', False)
        and s and s[-1] != '\n'):
        s = s + '\n'
    return eolre.sub('\r\n', s)

def isbinary(s, params):
    """Filter to do nothing with the file."""
    return s

filters = {
    'to-lf': tolf,
    'to-crlf': tocrlf,
    'is-binary': isbinary,
    # The following provide backwards compatibility with win32text
    'cleverencode:': tolf,
    'cleverdecode:': tocrlf
}

class eolfile(object):
    def __init__(self, ui, root, data):
        self._decode = {'LF': 'to-lf', 'CRLF': 'to-crlf', 'BIN': 'is-binary'}
        self._encode = {'LF': 'to-lf', 'CRLF': 'to-crlf', 'BIN': 'is-binary'}

        self.cfg = config.config()
        # Our files should not be touched. The pattern must be
        # inserted first override a '** = native' pattern.
        self.cfg.set('patterns', '.hg*', 'BIN')
        # We can then parse the user's patterns.
        self.cfg.parse('.hgeol', data)

        isrepolf = self.cfg.get('repository', 'native') != 'CRLF'
        self._encode['NATIVE'] = isrepolf and 'to-lf' or 'to-crlf'
        iswdlf = ui.config('eol', 'native', os.linesep) in ('LF', '\n')
        self._decode['NATIVE'] = iswdlf and 'to-lf' or 'to-crlf'

        include = []
        exclude = []
        for pattern, style in self.cfg.items('patterns'):
            key = style.upper()
            if key == 'BIN':
                exclude.append(pattern)
            else:
                include.append(pattern)
        # This will match the files for which we need to care
        # about inconsistent newlines.
        self.match = match.match(root, '', [], include, exclude)

    def copytoui(self, ui):
        for pattern, style in self.cfg.items('patterns'):
            key = style.upper()
            try:
                ui.setconfig('decode', pattern, self._decode[key])
                ui.setconfig('encode', pattern, self._encode[key])
            except KeyError:
                ui.warn(_("ignoring unknown EOL style '%s' from %s\n")
                        % (style, self.cfg.source('patterns', pattern)))
        # eol.only-consistent can be specified in ~/.hgrc or .hgeol
        for k, v in self.cfg.items('eol'):
            ui.setconfig('eol', k, v)

    def checkrev(self, repo, ctx, files):
        failed = []
        for f in (files or ctx.files()):
            if f not in ctx:
                continue
            for pattern, style in self.cfg.items('patterns'):
                if not match.match(repo.root, '', [pattern])(f):
                    continue
                target = self._encode[style.upper()]
                data = ctx[f].data()
                if (target == "to-lf" and "\r\n" in data
                    or target == "to-crlf" and singlelf.search(data)):
                    failed.append((str(ctx), target, f))
                break
        return failed

def parseeol(ui, repo, nodes):
    try:
        for node in nodes:
            try:
                if node is None:
                    # Cannot use workingctx.data() since it would load
                    # and cache the filters before we configure them.
                    data = repo.wfile('.hgeol').read()
                else:
                    data = repo[node]['.hgeol'].data()
                return eolfile(ui, repo.root, data)
            except (IOError, LookupError):
                pass
    except error.ParseError, inst:
        ui.warn(_("warning: ignoring .hgeol file due to parse error "
                  "at %s: %s\n") % (inst.args[1], inst.args[0]))
    return None

def _checkhook(ui, repo, node, headsonly):
    # Get revisions to check and touched files at the same time
    files = set()
    revs = set()
    for rev in xrange(repo[node].rev(), len(repo)):
        revs.add(rev)
        if headsonly:
            ctx = repo[rev]
            files.update(ctx.files())
            for pctx in ctx.parents():
                revs.discard(pctx.rev())
    failed = []
    for rev in revs:
        ctx = repo[rev]
        eol = parseeol(ui, repo, [ctx.node()])
        if eol:
            failed.extend(eol.checkrev(repo, ctx, files))

    if failed:
        eols = {'to-lf': 'CRLF', 'to-crlf': 'LF'}
        msgs = []
        for node, target, f in failed:
            msgs.append(_("  %s in %s should not have %s line endings") %
                        (f, node, eols[target]))
        raise util.Abort(_("end-of-line check failed:\n") + "\n".join(msgs))

def checkallhook(ui, repo, node, hooktype, **kwargs):
    """verify that files have expected EOLs"""
    _checkhook(ui, repo, node, False)

def checkheadshook(ui, repo, node, hooktype, **kwargs):
    """verify that files have expected EOLs"""
    _checkhook(ui, repo, node, True)

# "checkheadshook" used to be called "hook"
hook = checkheadshook

def preupdate(ui, repo, hooktype, parent1, parent2):
    repo.loadeol([parent1])
    return False

def uisetup(ui):
    ui.setconfig('hooks', 'preupdate.eol', preupdate)

def extsetup(ui):
    try:
        extensions.find('win32text')
        ui.warn(_("the eol extension is incompatible with the "
                  "win32text extension\n"))
    except KeyError:
        pass


def reposetup(ui, repo):
    uisetup(repo.ui)

    if not repo.local():
        return
    for name, fn in filters.iteritems():
        repo.adddatafilter(name, fn)

    ui.setconfig('patch', 'eol', 'auto')

    class eolrepo(repo.__class__):

        def loadeol(self, nodes):
            eol = parseeol(self.ui, self, nodes)
            if eol is None:
                return None
            eol.copytoui(self.ui)
            return eol.match

        def _hgcleardirstate(self):
            self._eolfile = self.loadeol([None, 'tip'])
            if not self._eolfile:
                self._eolfile = util.never
                return

            try:
                cachemtime = os.path.getmtime(self.join("eol.cache"))
            except OSError:
                cachemtime = 0

            try:
                eolmtime = os.path.getmtime(self.wjoin(".hgeol"))
            except OSError:
                eolmtime = 0

            if eolmtime > cachemtime:
                self.ui.debug("eol: detected change in .hgeol\n")
                wlock = None
                try:
                    wlock = self.wlock()
                    for f in self.dirstate:
                        if self.dirstate[f] == 'n':
                            # all normal files need to be looked at
                            # again since the new .hgeol file might no
                            # longer match a file it matched before
                            self.dirstate.normallookup(f)
                    # Create or touch the cache to update mtime
                    self.opener("eol.cache", "w").close()
                    wlock.release()
                except error.LockUnavailable:
                    # If we cannot lock the repository and clear the
                    # dirstate, then a commit might not see all files
                    # as modified. But if we cannot lock the
                    # repository, then we can also not make a commit,
                    # so ignore the error.
                    pass

        def commitctx(self, ctx, error=False):
            for f in sorted(ctx.added() + ctx.modified()):
                if not self._eolfile(f):
                    continue
                try:
                    data = ctx[f].data()
                except IOError:
                    continue
                if util.binary(data):
                    # We should not abort here, since the user should
                    # be able to say "** = native" to automatically
                    # have all non-binary files taken care of.
                    continue
                if inconsistenteol(data):
                    raise util.Abort(_("inconsistent newline style "
                                       "in %s\n" % f))
            return super(eolrepo, self).commitctx(ctx, error)
    repo.__class__ = eolrepo
    repo._hgcleardirstate()
