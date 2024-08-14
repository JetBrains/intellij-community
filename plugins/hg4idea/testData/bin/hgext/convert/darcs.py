# darcs.py - darcs support for the convert extension
#
#  Copyright 2007-2009 Olivia Mackall <olivia@selenic.com> and others
#
# This software may be used and distributed according to the terms of the
# GNU General Public License version 2 or any later version.

import os
import re
import shutil
from xml.etree.ElementTree import (
    ElementTree,
    XMLParser,
)

from mercurial.i18n import _
from mercurial import (
    error,
    pycompat,
    util,
)
from mercurial.utils import dateutil
from . import common

NoRepo = common.NoRepo


class darcs_source(common.converter_source, common.commandline):
    def __init__(self, ui, repotype, path, revs=None):
        common.converter_source.__init__(self, ui, repotype, path, revs=revs)
        common.commandline.__init__(self, ui, b'darcs')

        # check for _darcs, ElementTree so that we can easily skip
        # test-convert-darcs if ElementTree is not around
        if not os.path.exists(os.path.join(path, b'_darcs')):
            raise NoRepo(_(b"%s does not look like a darcs repository") % path)

        common.checktool(b'darcs')
        version = self.run0(b'--version').splitlines()[0].strip()
        if version < b'2.1':
            raise error.Abort(
                _(b'darcs version 2.1 or newer needed (found %r)') % version
            )

        if "ElementTree" not in globals():
            raise error.Abort(_(b"Python ElementTree module is not available"))

        self.path = os.path.realpath(path)

        self.lastrev = None
        self.changes = {}
        self.parents = {}
        self.tags = {}

        # Check darcs repository format
        format = self.format()
        if format:
            if format in (b'darcs-1.0', b'hashed'):
                raise NoRepo(
                    _(
                        b"%s repository format is unsupported, "
                        b"please upgrade"
                    )
                    % format
                )
        else:
            self.ui.warn(_(b'failed to detect repository format!'))

    def before(self):
        self.tmppath = pycompat.mkdtemp(
            prefix=b'convert-' + os.path.basename(self.path) + b'-'
        )
        output, status = self.run(b'init', repodir=self.tmppath)
        self.checkexit(status)

        tree = self.xml(
            b'changes', xml_output=True, summary=True, repodir=self.path
        )
        tagname = None
        child = None
        for elt in tree.findall('patch'):
            node = self.recode(elt.get('hash'))
            name = self.recode(elt.findtext('name', ''))
            if name.startswith(b'TAG '):
                tagname = name[4:].strip()
            elif tagname is not None:
                self.tags[tagname] = node
                tagname = None
            self.changes[node] = elt
            self.parents[child] = [node]
            child = node
        self.parents[child] = []

    def after(self):
        self.ui.debug(b'cleaning up %s\n' % self.tmppath)
        shutil.rmtree(self.tmppath, ignore_errors=True)

    def recode(self, s, encoding=None):
        if isinstance(s, str):
            # XMLParser returns unicode objects for anything it can't
            # encode into ASCII. We convert them back to str to get
            # recode's normal conversion behavior.
            s = s.encode('latin-1')
        return super(darcs_source, self).recode(s, encoding)

    def xml(self, cmd, **kwargs):
        # NOTE: darcs is currently encoding agnostic and will print
        # patch metadata byte-for-byte, even in the XML changelog.
        etree = ElementTree()
        # While we are decoding the XML as latin-1 to be as liberal as
        # possible, etree will still raise an exception if any
        # non-printable characters are in the XML changelog.
        parser = XMLParser(encoding='latin-1')
        p = self._run(cmd, **kwargs)
        etree.parse(p.stdout, parser=parser)
        p.wait()
        self.checkexit(p.returncode)
        return etree.getroot()

    def format(self):
        output, status = self.run(b'show', b'repo', repodir=self.path)
        self.checkexit(status)
        m = re.search(br'^\s*Format:\s*(.*)$', output, re.MULTILINE)
        if not m:
            return None
        return b','.join(sorted(f.strip() for f in m.group(1).split(b',')))

    def manifest(self):
        man = []
        output, status = self.run(
            b'show', b'files', no_directories=True, repodir=self.tmppath
        )
        self.checkexit(status)
        for line in output.split(b'\n'):
            path = line[2:]
            if path:
                man.append(path)
        return man

    def getheads(self):
        return self.parents[None]

    def getcommit(self, rev):
        elt = self.changes[rev]
        dateformat = b'%a %b %d %H:%M:%S %Z %Y'
        date = dateutil.strdate(self.recode(elt.get('local_date')), dateformat)
        desc = elt.findtext('name') + '\n' + elt.findtext('comment', '')
        # etree can return unicode objects for name, comment, and author,
        # so recode() is used to ensure str objects are emitted.
        newdateformat = b'%Y-%m-%d %H:%M:%S %1%2'
        return common.commit(
            author=self.recode(elt.get('author')),
            date=dateutil.datestr(date, newdateformat),
            desc=self.recode(desc).strip(),
            parents=self.parents[rev],
        )

    def pull(self, rev):
        output, status = self.run(
            b'pull',
            self.path,
            all=True,
            match=b'hash %s' % self.recode(rev),
            no_test=True,
            no_posthook=True,
            external_merge=b'/bin/false',
            repodir=self.tmppath,
        )
        if status:
            if output.find(b'We have conflicts in') == -1:
                self.checkexit(status, output)
            output, status = self.run(b'revert', all=True, repodir=self.tmppath)
            self.checkexit(status, output)

    def getchanges(self, rev, full):
        if full:
            raise error.Abort(_(b"convert from darcs does not support --full"))
        copies = {}
        changes = []
        man = None
        for elt in self.changes[rev].find('summary'):
            if elt.tag in ('add_directory', 'remove_directory'):
                continue
            if elt.tag == 'move':
                if man is None:
                    man = self.manifest()
                source = self.recode(elt.get('from'))
                dest = self.recode(elt.get('to'))
                if source in man:
                    # File move
                    changes.append((source, rev))
                    changes.append((dest, rev))
                    copies[dest] = source
                else:
                    # Directory move, deduce file moves from manifest
                    source = source + b'/'
                    for f in man:
                        if not f.startswith(source):
                            continue
                        fdest = dest + b'/' + f[len(source) :]
                        changes.append((f, rev))
                        changes.append((fdest, rev))
                        copies[fdest] = f
            else:
                changes.append((self.recode(elt.text.strip()), rev))
        self.pull(rev)
        self.lastrev = rev
        return sorted(changes), copies, set()

    def getfile(self, name, rev):
        if rev != self.lastrev:
            raise error.Abort(_(b'internal calling inconsistency'))
        path = os.path.join(self.tmppath, name)
        try:
            data = util.readfile(path)
            mode = os.lstat(path).st_mode
        except FileNotFoundError:
            return None, None
        mode = (mode & 0o111) and b'x' or b''
        return data, mode

    def gettags(self):
        return self.tags
