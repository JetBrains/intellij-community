# git.py - git support for the convert extension
#
#  Copyright 2005-2009 Olivia Mackall <olivia@selenic.com> and others
#
# This software may be used and distributed according to the terms of the
# GNU General Public License version 2 or any later version.

import os

from mercurial.i18n import _
from mercurial.node import sha1nodeconstants
from mercurial import (
    config,
    error,
    pycompat,
    util,
)

from . import common


class submodule:
    def __init__(self, path, node, url):
        self.path = path
        self.node = node
        self.url = url

    def hgsub(self):
        return b"%s = [git]%s" % (self.path, self.url)

    def hgsubstate(self):
        return b"%s %s" % (self.node, self.path)


# Keys in extra fields that should not be copied if the user requests.
bannedextrakeys = {
    # Git commit object built-ins.
    b'tree',
    b'parent',
    b'author',
    b'committer',
    # Mercurial built-ins.
    b'branch',
    b'close',
}


class convert_git(common.converter_source, common.commandline):
    # Windows does not support GIT_DIR= construct while other systems
    # cannot remove environment variable. Just assume none have
    # both issues.

    def _gitcmd(self, cmd, *args, **kwargs):
        return cmd(b'--git-dir=%s' % self.path, *args, **kwargs)

    def gitrun0(self, *args, **kwargs):
        return self._gitcmd(self.run0, *args, **kwargs)

    def gitrun(self, *args, **kwargs):
        return self._gitcmd(self.run, *args, **kwargs)

    def gitrunlines0(self, *args, **kwargs):
        return self._gitcmd(self.runlines0, *args, **kwargs)

    def gitrunlines(self, *args, **kwargs):
        return self._gitcmd(self.runlines, *args, **kwargs)

    def gitpipe(self, *args, **kwargs):
        return self._gitcmd(self._run3, *args, **kwargs)

    def __init__(self, ui, repotype, path, revs=None):
        super(convert_git, self).__init__(ui, repotype, path, revs=revs)
        common.commandline.__init__(self, ui, b'git')

        # Pass an absolute path to git to prevent from ever being interpreted
        # as a URL
        path = util.abspath(path)

        if os.path.isdir(path + b"/.git"):
            path += b"/.git"
        if not os.path.exists(path + b"/objects"):
            raise common.NoRepo(
                _(b"%s does not look like a Git repository") % path
            )

        # The default value (50) is based on the default for 'git diff'.
        similarity = ui.configint(b'convert', b'git.similarity')
        if similarity < 0 or similarity > 100:
            raise error.Abort(_(b'similarity must be between 0 and 100'))
        if similarity > 0:
            self.simopt = [b'-C%d%%' % similarity]
            findcopiesharder = ui.configbool(
                b'convert', b'git.findcopiesharder'
            )
            if findcopiesharder:
                self.simopt.append(b'--find-copies-harder')

            renamelimit = ui.configint(b'convert', b'git.renamelimit')
            self.simopt.append(b'-l%d' % renamelimit)
        else:
            self.simopt = []

        common.checktool(b'git', b'git')

        self.path = path
        self.submodules = []

        self.catfilepipe = self.gitpipe(b'cat-file', b'--batch')

        self.copyextrakeys = self.ui.configlist(b'convert', b'git.extrakeys')
        banned = set(self.copyextrakeys) & bannedextrakeys
        if banned:
            raise error.Abort(
                _(b'copying of extra key is forbidden: %s')
                % _(b', ').join(sorted(banned))
            )

        committeractions = self.ui.configlist(
            b'convert', b'git.committeractions'
        )

        messagedifferent = None
        messagealways = None
        for a in committeractions:
            if a.startswith((b'messagedifferent', b'messagealways')):
                k = a
                v = None
                if b'=' in a:
                    k, v = a.split(b'=', 1)

                if k == b'messagedifferent':
                    messagedifferent = v or b'committer:'
                elif k == b'messagealways':
                    messagealways = v or b'committer:'

        if messagedifferent and messagealways:
            raise error.Abort(
                _(
                    b'committeractions cannot define both '
                    b'messagedifferent and messagealways'
                )
            )

        dropcommitter = b'dropcommitter' in committeractions
        replaceauthor = b'replaceauthor' in committeractions

        if dropcommitter and replaceauthor:
            raise error.Abort(
                _(
                    b'committeractions cannot define both '
                    b'dropcommitter and replaceauthor'
                )
            )

        if dropcommitter and messagealways:
            raise error.Abort(
                _(
                    b'committeractions cannot define both '
                    b'dropcommitter and messagealways'
                )
            )

        if not messagedifferent and not messagealways:
            messagedifferent = b'committer:'

        self.committeractions = {
            b'dropcommitter': dropcommitter,
            b'replaceauthor': replaceauthor,
            b'messagedifferent': messagedifferent,
            b'messagealways': messagealways,
        }

    def after(self):
        for f in self.catfilepipe:
            f.close()

    def getheads(self):
        if not self.revs:
            output, status = self.gitrun(
                b'rev-parse', b'--branches', b'--remotes'
            )
            heads = output.splitlines()
            if status:
                raise error.Abort(_(b'cannot retrieve git heads'))
        else:
            heads = []
            for rev in self.revs:
                rawhead, ret = self.gitrun(b'rev-parse', b'--verify', rev)
                heads.append(rawhead[:-1])
                if ret:
                    raise error.Abort(_(b'cannot retrieve git head "%s"') % rev)
        return heads

    def catfile(self, rev, ftype):
        if rev == sha1nodeconstants.nullhex:
            raise IOError
        self.catfilepipe[0].write(rev + b'\n')
        self.catfilepipe[0].flush()
        info = self.catfilepipe[1].readline().split()
        if info[1] != ftype:
            raise error.Abort(
                _(b'cannot read %r object at %s')
                % (pycompat.bytestr(ftype), rev)
            )
        size = int(info[2])
        data = self.catfilepipe[1].read(size)
        if len(data) < size:
            raise error.Abort(
                _(b'cannot read %r object at %s: unexpected size')
                % (ftype, rev)
            )
        # read the trailing newline
        self.catfilepipe[1].read(1)
        return data

    def getfile(self, name, rev):
        if rev == sha1nodeconstants.nullhex:
            return None, None
        if name == b'.hgsub':
            data = b'\n'.join([m.hgsub() for m in self.submoditer()])
            mode = b''
        elif name == b'.hgsubstate':
            data = b'\n'.join([m.hgsubstate() for m in self.submoditer()])
            mode = b''
        else:
            data = self.catfile(rev, b"blob")
            mode = self.modecache[(name, rev)]
        return data, mode

    def submoditer(self):
        null = sha1nodeconstants.nullhex
        for m in sorted(self.submodules, key=lambda p: p.path):
            if m.node != null:
                yield m

    def parsegitmodules(self, content):
        """Parse the formatted .gitmodules file, example file format:
        [submodule "sub"]\n
        \tpath = sub\n
        \turl = git://giturl\n
        """
        self.submodules = []
        c = config.config()
        # Each item in .gitmodules starts with whitespace that cant be parsed
        c.parse(
            b'.gitmodules',
            b'\n'.join(line.strip() for line in content.split(b'\n')),
        )
        for sec in c.sections():
            # turn the config object into a real dict
            s = dict(c.items(sec))
            if b'url' in s and b'path' in s:
                self.submodules.append(submodule(s[b'path'], b'', s[b'url']))

    def retrievegitmodules(self, version):
        modules, ret = self.gitrun(
            b'show', b'%s:%s' % (version, b'.gitmodules')
        )
        if ret:
            # This can happen if a file is in the repo that has permissions
            # 160000, but there is no .gitmodules file.
            self.ui.warn(
                _(b"warning: cannot read submodules config file in %s\n")
                % version
            )
            return

        try:
            self.parsegitmodules(modules)
        except error.ParseError:
            self.ui.warn(
                _(b"warning: unable to parse .gitmodules in %s\n") % version
            )
            return

        for m in self.submodules:
            node, ret = self.gitrun(b'rev-parse', b'%s:%s' % (version, m.path))
            if ret:
                continue
            m.node = node.strip()

    def getchanges(self, version, full):
        if full:
            raise error.Abort(_(b"convert from git does not support --full"))
        self.modecache = {}
        cmd = (
            [b'diff-tree', b'-z', b'--root', b'-m', b'-r']
            + self.simopt
            + [version]
        )
        output, status = self.gitrun(*cmd)
        if status:
            raise error.Abort(_(b'cannot read changes in %s') % version)
        changes = []
        copies = {}
        seen = set()
        entry = None
        subexists = [False]
        subdeleted = [False]
        difftree = output.split(b'\x00')
        lcount = len(difftree)
        i = 0

        skipsubmodules = self.ui.configbool(b'convert', b'git.skipsubmodules')

        def add(entry, f, isdest):
            seen.add(f)
            h = entry[3]
            p = entry[1] == b"100755"
            s = entry[1] == b"120000"
            renamesource = not isdest and entry[4][0] == b'R'

            if f == b'.gitmodules':
                if skipsubmodules:
                    return

                subexists[0] = True
                if entry[4] == b'D' or renamesource:
                    subdeleted[0] = True
                    changes.append((b'.hgsub', sha1nodeconstants.nullhex))
                else:
                    changes.append((b'.hgsub', b''))
            elif entry[1] == b'160000' or entry[0] == b':160000':
                if not skipsubmodules:
                    subexists[0] = True
            else:
                if renamesource:
                    h = sha1nodeconstants.nullhex
                self.modecache[(f, h)] = (p and b"x") or (s and b"l") or b""
                changes.append((f, h))

        while i < lcount:
            l = difftree[i]
            i += 1
            if not entry:
                if not l.startswith(b':'):
                    continue
                entry = tuple(pycompat.bytestr(p) for p in l.split())
                continue
            f = l
            if entry[4][0] == b'C':
                copysrc = f
                copydest = difftree[i]
                i += 1
                f = copydest
                copies[copydest] = copysrc
            if f not in seen:
                add(entry, f, False)
            # A file can be copied multiple times, or modified and copied
            # simultaneously. So f can be repeated even if fdest isn't.
            if entry[4][0] == b'R':
                # rename: next line is the destination
                fdest = difftree[i]
                i += 1
                if fdest not in seen:
                    add(entry, fdest, True)
                    # .gitmodules isn't imported at all, so it being copied to
                    # and fro doesn't really make sense
                    if f != b'.gitmodules' and fdest != b'.gitmodules':
                        copies[fdest] = f
            entry = None

        if subexists[0]:
            if subdeleted[0]:
                changes.append((b'.hgsubstate', sha1nodeconstants.nullhex))
            else:
                self.retrievegitmodules(version)
                changes.append((b'.hgsubstate', b''))
        return (changes, copies, set())

    def getcommit(self, version):
        c = self.catfile(version, b"commit")  # read the commit hash
        end = c.find(b"\n\n")
        message = c[end + 2 :]
        message = self.recode(message)
        l = c[:end].splitlines()
        parents = []
        author = committer = None
        extra = {}
        for e in l[1:]:
            n, v = e.split(b" ", 1)
            if n == b"author":
                p = v.split()
                tm, tz = p[-2:]
                author = b" ".join(p[:-2])
                if author[0] == b"<":
                    author = author[1:-1]
                author = self.recode(author)
            if n == b"committer":
                p = v.split()
                tm, tz = p[-2:]
                committer = b" ".join(p[:-2])
                if committer[0] == b"<":
                    committer = committer[1:-1]
                committer = self.recode(committer)
            if n == b"parent":
                parents.append(v)
            if n in self.copyextrakeys:
                extra[n] = v

        if self.committeractions[b'dropcommitter']:
            committer = None
        elif self.committeractions[b'replaceauthor']:
            author = committer

        if committer:
            messagealways = self.committeractions[b'messagealways']
            messagedifferent = self.committeractions[b'messagedifferent']
            if messagealways:
                message += b'\n%s %s\n' % (messagealways, committer)
            elif messagedifferent and author != committer:
                message += b'\n%s %s\n' % (messagedifferent, committer)

        tzs, tzh, tzm = tz[-5:-4] + b"1", tz[-4:-2], tz[-2:]
        tz = -int(tzs) * (int(tzh) * 3600 + int(tzm))
        date = tm + b" " + (b"%d" % tz)
        saverev = self.ui.configbool(b'convert', b'git.saverev')

        c = common.commit(
            parents=parents,
            date=date,
            author=author,
            desc=message,
            rev=version,
            extra=extra,
            saverev=saverev,
        )
        return c

    def numcommits(self):
        output, ret = self.gitrunlines(b'rev-list', b'--all')
        if ret:
            raise error.Abort(
                _(b'cannot retrieve number of commits in %s') % self.path
            )
        return len(output)

    def gettags(self):
        tags = {}
        alltags = {}
        output, status = self.gitrunlines(b'ls-remote', b'--tags', self.path)

        if status:
            raise error.Abort(_(b'cannot read tags from %s') % self.path)
        prefix = b'refs/tags/'

        # Build complete list of tags, both annotated and bare ones
        for line in output:
            line = line.strip()
            if line.startswith(b"error:") or line.startswith(b"fatal:"):
                raise error.Abort(_(b'cannot read tags from %s') % self.path)
            node, tag = line.split(None, 1)
            if not tag.startswith(prefix):
                continue
            alltags[tag[len(prefix) :]] = node

        # Filter out tag objects for annotated tag refs
        for tag in alltags:
            if tag.endswith(b'^{}'):
                tags[tag[:-3]] = alltags[tag]
            else:
                if tag + b'^{}' in alltags:
                    continue
                else:
                    tags[tag] = alltags[tag]

        return tags

    def getchangedfiles(self, version, i):
        changes = []
        if i is None:
            output, status = self.gitrunlines(
                b'diff-tree', b'--root', b'-m', b'-r', version
            )
            if status:
                raise error.Abort(_(b'cannot read changes in %s') % version)
            for l in output:
                if b"\t" not in l:
                    continue
                m, f = l[:-1].split(b"\t")
                changes.append(f)
        else:
            output, status = self.gitrunlines(
                b'diff-tree',
                b'--name-only',
                b'--root',
                b'-r',
                version,
                b'%s^%d' % (version, i + 1),
                b'--',
            )
            if status:
                raise error.Abort(_(b'cannot read changes in %s') % version)
            changes = [f.rstrip(b'\n') for f in output]

        return changes

    def getbookmarks(self):
        bookmarks = {}

        # Handle local and remote branches
        remoteprefix = self.ui.config(b'convert', b'git.remoteprefix')
        reftypes = [
            # (git prefix, hg prefix)
            (b'refs/remotes/origin/', remoteprefix + b'/'),
            (b'refs/heads/', b''),
        ]

        exclude = {
            b'refs/remotes/origin/HEAD',
        }

        try:
            output, status = self.gitrunlines(b'show-ref')
            for line in output:
                line = line.strip()
                rev, name = line.split(None, 1)
                # Process each type of branch
                for gitprefix, hgprefix in reftypes:
                    if not name.startswith(gitprefix) or name in exclude:
                        continue
                    name = b'%s%s' % (hgprefix, name[len(gitprefix) :])
                    bookmarks[name] = rev
        except Exception:
            pass

        return bookmarks

    def checkrevformat(self, revstr, mapname=b'splicemap'):
        """git revision string is a 40 byte hex"""
        self.checkhexformat(revstr, mapname)
