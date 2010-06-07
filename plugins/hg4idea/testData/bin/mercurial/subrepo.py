# subrepo.py - sub-repository handling for Mercurial
#
# Copyright 2009-2010 Matt Mackall <mpm@selenic.com>
#
# This software may be used and distributed according to the terms of the
# GNU General Public License version 2 or any later version.

import errno, os, re, xml.dom.minidom, shutil
from i18n import _
import config, util, node, error
hg = None

nullstate = ('', '', 'empty')

def state(ctx):
    p = config.config()
    def read(f, sections=None, remap=None):
        if f in ctx:
            p.parse(f, ctx[f].data(), sections, remap, read)
        else:
            raise util.Abort(_("subrepo spec file %s not found") % f)

    if '.hgsub' in ctx:
        read('.hgsub')

    rev = {}
    if '.hgsubstate' in ctx:
        try:
            for l in ctx['.hgsubstate'].data().splitlines():
                revision, path = l.split(" ", 1)
                rev[path] = revision
        except IOError, err:
            if err.errno != errno.ENOENT:
                raise

    state = {}
    for path, src in p[''].items():
        kind = 'hg'
        if src.startswith('['):
            if ']' not in src:
                raise util.Abort(_('missing ] in subrepo source'))
            kind, src = src.split(']', 1)
            kind = kind[1:]
        state[path] = (src.strip(), rev.get(path, ''), kind)

    return state

def writestate(repo, state):
    repo.wwrite('.hgsubstate',
                ''.join(['%s %s\n' % (state[s][1], s)
                         for s in sorted(state)]), '')

def submerge(repo, wctx, mctx, actx):
    # working context, merging context, ancestor context
    if mctx == actx: # backwards?
        actx = wctx.p1()
    s1 = wctx.substate
    s2 = mctx.substate
    sa = actx.substate
    sm = {}

    repo.ui.debug("subrepo merge %s %s %s\n" % (wctx, mctx, actx))

    def debug(s, msg, r=""):
        if r:
            r = "%s:%s:%s" % r
        repo.ui.debug("  subrepo %s: %s %s\n" % (s, msg, r))

    for s, l in s1.items():
        if wctx != actx and wctx.sub(s).dirty():
            l = (l[0], l[1] + "+")
        a = sa.get(s, nullstate)
        if s in s2:
            r = s2[s]
            if l == r or r == a: # no change or local is newer
                sm[s] = l
                continue
            elif l == a: # other side changed
                debug(s, "other changed, get", r)
                wctx.sub(s).get(r)
                sm[s] = r
            elif l[0] != r[0]: # sources differ
                if repo.ui.promptchoice(
                    _(' subrepository sources for %s differ\n'
                      'use (l)ocal source (%s) or (r)emote source (%s)?')
                      % (s, l[0], r[0]),
                      (_('&Local'), _('&Remote')), 0):
                    debug(s, "prompt changed, get", r)
                    wctx.sub(s).get(r)
                    sm[s] = r
            elif l[1] == a[1]: # local side is unchanged
                debug(s, "other side changed, get", r)
                wctx.sub(s).get(r)
                sm[s] = r
            else:
                debug(s, "both sides changed, merge with", r)
                wctx.sub(s).merge(r)
                sm[s] = l
        elif l == a: # remote removed, local unchanged
            debug(s, "remote removed, remove")
            wctx.sub(s).remove()
        else:
            if repo.ui.promptchoice(
                _(' local changed subrepository %s which remote removed\n'
                  'use (c)hanged version or (d)elete?') % s,
                (_('&Changed'), _('&Delete')), 0):
                debug(s, "prompt remove")
                wctx.sub(s).remove()

    for s, r in s2.items():
        if s in s1:
            continue
        elif s not in sa:
            debug(s, "remote added, get", r)
            mctx.sub(s).get(r)
            sm[s] = r
        elif r != sa[s]:
            if repo.ui.promptchoice(
                _(' remote changed subrepository %s which local removed\n'
                  'use (c)hanged version or (d)elete?') % s,
                (_('&Changed'), _('&Delete')), 0) == 0:
                debug(s, "prompt recreate", r)
                wctx.sub(s).get(r)
                sm[s] = r

    # record merged .hgsubstate
    writestate(repo, sm)

def _abssource(repo, push=False):
    if hasattr(repo, '_subparent'):
        source = repo._subsource
        if source.startswith('/') or '://' in source:
            return source
        parent = _abssource(repo._subparent, push)
        if '://' in parent:
            if parent[-1] == '/':
                parent = parent[:-1]
            return parent + '/' + source
        return os.path.join(parent, repo._subsource)
    if push and repo.ui.config('paths', 'default-push'):
        return repo.ui.config('paths', 'default-push', repo.root)
    return repo.ui.config('paths', 'default', repo.root)

def subrepo(ctx, path):
    # subrepo inherently violates our import layering rules
    # because it wants to make repo objects from deep inside the stack
    # so we manually delay the circular imports to not break
    # scripts that don't use our demand-loading
    global hg
    import hg as h
    hg = h

    util.path_auditor(ctx._repo.root)(path)
    state = ctx.substate.get(path, nullstate)
    if state[2] not in types:
        raise util.Abort(_('unknown subrepo type %s') % state[2])
    return types[state[2]](ctx, path, state[:2])

# subrepo classes need to implement the following methods:
# __init__(self, ctx, path, state)
# dirty(self): returns true if the dirstate of the subrepo
#   does not match current stored state
# commit(self, text, user, date): commit the current changes
#   to the subrepo with the given log message. Use given
#   user and date if possible. Return the new state of the subrepo.
# remove(self): remove the subrepo (should verify the dirstate
#   is not dirty first)
# get(self, state): run whatever commands are needed to put the
#   subrepo into this state
# merge(self, state): merge currently-saved state with the new state.
# push(self, force): perform whatever action is analagous to 'hg push'
#   This may be a no-op on some systems.

class hgsubrepo(object):
    def __init__(self, ctx, path, state):
        self._path = path
        self._state = state
        r = ctx._repo
        root = r.wjoin(path)
        create = False
        if not os.path.exists(os.path.join(root, '.hg')):
            create = True
            util.makedirs(root)
        self._repo = hg.repository(r.ui, root, create=create)
        self._repo._subparent = r
        self._repo._subsource = state[0]

        if create:
            fp = self._repo.opener("hgrc", "w", text=True)
            fp.write('[paths]\n')

            def addpathconfig(key, value):
                fp.write('%s = %s\n' % (key, value))
                self._repo.ui.setconfig('paths', key, value)

            defpath = _abssource(self._repo)
            defpushpath = _abssource(self._repo, True)
            addpathconfig('default', defpath)
            if defpath != defpushpath:
                addpathconfig('default-push', defpushpath)
            fp.close()

    def dirty(self):
        r = self._state[1]
        if r == '':
            return True
        w = self._repo[None]
        if w.p1() != self._repo[r]: # version checked out change
            return True
        return w.dirty() # working directory changed

    def commit(self, text, user, date):
        self._repo.ui.debug("committing subrepo %s\n" % self._path)
        n = self._repo.commit(text, user, date)
        if not n:
            return self._repo['.'].hex() # different version checked out
        return node.hex(n)

    def remove(self):
        # we can't fully delete the repository as it may contain
        # local-only history
        self._repo.ui.note(_('removing subrepo %s\n') % self._path)
        hg.clean(self._repo, node.nullid, False)

    def _get(self, state):
        source, revision, kind = state
        try:
            self._repo.lookup(revision)
        except error.RepoError:
            self._repo._subsource = source
            self._repo.ui.status(_('pulling subrepo %s\n') % self._path)
            srcurl = _abssource(self._repo)
            other = hg.repository(self._repo.ui, srcurl)
            self._repo.pull(other)

    def get(self, state):
        self._get(state)
        source, revision, kind = state
        self._repo.ui.debug("getting subrepo %s\n" % self._path)
        hg.clean(self._repo, revision, False)

    def merge(self, state):
        self._get(state)
        cur = self._repo['.']
        dst = self._repo[state[1]]
        anc = dst.ancestor(cur)
        if anc == cur:
            self._repo.ui.debug("updating subrepo %s\n" % self._path)
            hg.update(self._repo, state[1])
        elif anc == dst:
            self._repo.ui.debug("skipping subrepo %s\n" % self._path)
        else:
            self._repo.ui.debug("merging subrepo %s\n" % self._path)
            hg.merge(self._repo, state[1], remind=False)

    def push(self, force):
        # push subrepos depth-first for coherent ordering
        c = self._repo['']
        subs = c.substate # only repos that are committed
        for s in sorted(subs):
            if not c.sub(s).push(force):
                return False

        self._repo.ui.status(_('pushing subrepo %s\n') % self._path)
        dsturl = _abssource(self._repo, True)
        other = hg.repository(self._repo.ui, dsturl)
        return self._repo.push(other, force)

class svnsubrepo(object):
    def __init__(self, ctx, path, state):
        self._path = path
        self._state = state
        self._ctx = ctx
        self._ui = ctx._repo.ui

    def _svncommand(self, commands):
        path = os.path.join(self._ctx._repo.origroot, self._path)
        cmd = ['svn'] + commands + [path]
        cmd = [util.shellquote(arg) for arg in cmd]
        cmd = util.quotecommand(' '.join(cmd))
        env = dict(os.environ)
        # Avoid localized output, preserve current locale for everything else.
        env['LC_MESSAGES'] = 'C'
        write, read, err = util.popen3(cmd, env=env, newlines=True)
        retdata = read.read()
        err = err.read().strip()
        if err:
            raise util.Abort(err)
        return retdata

    def _wcrev(self):
        output = self._svncommand(['info', '--xml'])
        doc = xml.dom.minidom.parseString(output)
        entries = doc.getElementsByTagName('entry')
        if not entries:
            return 0
        return int(entries[0].getAttribute('revision') or 0)

    def _wcchanged(self):
        """Return (changes, extchanges) where changes is True
        if the working directory was changed, and extchanges is
        True if any of these changes concern an external entry.
        """
        output = self._svncommand(['status', '--xml'])
        externals, changes = [], []
        doc = xml.dom.minidom.parseString(output)
        for e in doc.getElementsByTagName('entry'):
            s = e.getElementsByTagName('wc-status')
            if not s:
                continue
            item = s[0].getAttribute('item')
            props = s[0].getAttribute('props')
            path = e.getAttribute('path')
            if item == 'external':
                externals.append(path)
            if (item not in ('', 'normal', 'unversioned', 'external')
                or props not in ('', 'none')):
                changes.append(path)
        for path in changes:
            for ext in externals:
                if path == ext or path.startswith(ext + os.sep):
                    return True, True
        return bool(changes), False

    def dirty(self):
        if self._wcrev() == self._state[1] and not self._wcchanged()[0]:
            return False
        return True

    def commit(self, text, user, date):
        # user and date are out of our hands since svn is centralized
        changed, extchanged = self._wcchanged()
        if not changed:
            return self._wcrev()
        if extchanged:
            # Do not try to commit externals
            raise util.Abort(_('cannot commit svn externals'))
        commitinfo = self._svncommand(['commit', '-m', text])
        self._ui.status(commitinfo)
        newrev = re.search('Committed revision ([\d]+).', commitinfo)
        if not newrev:
            raise util.Abort(commitinfo.splitlines()[-1])
        newrev = newrev.groups()[0]
        self._ui.status(self._svncommand(['update', '-r', newrev]))
        return newrev

    def remove(self):
        if self.dirty():
            self._ui.warn(_('not removing repo %s because '
                            'it has changes.\n' % self._path))
            return
        self._ui.note(_('removing subrepo %s\n') % self._path)
        shutil.rmtree(self._ctx.repo.join(self._path))

    def get(self, state):
        status = self._svncommand(['checkout', state[0], '--revision', state[1]])
        if not re.search('Checked out revision [\d]+.', status):
            raise util.Abort(status.splitlines()[-1])
        self._ui.status(status)

    def merge(self, state):
        old = int(self._state[1])
        new = int(state[1])
        if new > old:
            self.get(state)

    def push(self, force):
        # nothing for svn
        pass

types = {
    'hg': hgsubrepo,
    'svn': svnsubrepo,
    }
