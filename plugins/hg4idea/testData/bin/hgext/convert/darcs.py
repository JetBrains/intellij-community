# darcs.py - darcs support for the convert extension
#
#  Copyright 2007-2009 Matt Mackall <mpm@selenic.com> and others
#
# This software may be used and distributed according to the terms of the
# GNU General Public License version 2 or any later version.

from common import NoRepo, checktool, commandline, commit, converter_source
from mercurial.i18n import _
from mercurial import util
import os, shutil, tempfile

# The naming drift of ElementTree is fun!

try:
    from xml.etree.cElementTree import ElementTree
except ImportError:
    try:
        from xml.etree.ElementTree import ElementTree
    except ImportError:
        try:
            from elementtree.cElementTree import ElementTree
        except ImportError:
            try:
                from elementtree.ElementTree import ElementTree
            except ImportError:
                ElementTree = None

class darcs_source(converter_source, commandline):
    def __init__(self, ui, path, rev=None):
        converter_source.__init__(self, ui, path, rev=rev)
        commandline.__init__(self, ui, 'darcs')

        # check for _darcs, ElementTree, _darcs/inventory so that we can
        # easily skip test-convert-darcs if ElementTree is not around
        if not os.path.exists(os.path.join(path, '_darcs', 'inventories')):
            raise NoRepo(_("%s does not look like a darcs repository") % path)

        if not os.path.exists(os.path.join(path, '_darcs')):
            raise NoRepo(_("%s does not look like a darcs repository") % path)

        checktool('darcs')
        version = self.run0('--version').splitlines()[0].strip()
        if version < '2.1':
            raise util.Abort(_('darcs version 2.1 or newer needed (found %r)') %
                             version)

        if ElementTree is None:
            raise util.Abort(_("Python ElementTree module is not available"))

        self.path = os.path.realpath(path)

        self.lastrev = None
        self.changes = {}
        self.parents = {}
        self.tags = {}

    def before(self):
        self.tmppath = tempfile.mkdtemp(
            prefix='convert-' + os.path.basename(self.path) + '-')
        output, status = self.run('init', repodir=self.tmppath)
        self.checkexit(status)

        tree = self.xml('changes', xml_output=True, summary=True,
                        repodir=self.path)
        tagname = None
        child = None
        for elt in tree.findall('patch'):
            node = elt.get('hash')
            name = elt.findtext('name', '')
            if name.startswith('TAG '):
                tagname = name[4:].strip()
            elif tagname is not None:
                self.tags[tagname] = node
                tagname = None
            self.changes[node] = elt
            self.parents[child] = [node]
            child = node
        self.parents[child] = []

    def after(self):
        self.ui.debug('cleaning up %s\n' % self.tmppath)
        shutil.rmtree(self.tmppath, ignore_errors=True)

    def xml(self, cmd, **kwargs):
        etree = ElementTree()
        fp = self._run(cmd, **kwargs)
        etree.parse(fp)
        self.checkexit(fp.close())
        return etree.getroot()

    def manifest(self):
        man = []
        output, status = self.run('show', 'files', no_directories=True,
                                  repodir=self.tmppath)
        self.checkexit(status)
        for line in output.split('\n'):
            path = line[2:]
            if path:
                man.append(path)
        return man

    def getheads(self):
        return self.parents[None]

    def getcommit(self, rev):
        elt = self.changes[rev]
        date = util.strdate(elt.get('local_date'), '%a %b %d %H:%M:%S %Z %Y')
        desc = elt.findtext('name') + '\n' + elt.findtext('comment', '')
        return commit(author=elt.get('author'), date=util.datestr(date),
                      desc=desc.strip(), parents=self.parents[rev])

    def pull(self, rev):
        output, status = self.run('pull', self.path, all=True,
                                  match='hash %s' % rev,
                                  no_test=True, no_posthook=True,
                                  external_merge='/bin/false',
                                  repodir=self.tmppath)
        if status:
            if output.find('We have conflicts in') == -1:
                self.checkexit(status, output)
            output, status = self.run('revert', all=True, repodir=self.tmppath)
            self.checkexit(status, output)

    def getchanges(self, rev):
        copies = {}
        changes = []
        man = None
        for elt in self.changes[rev].find('summary').getchildren():
            if elt.tag in ('add_directory', 'remove_directory'):
                continue
            if elt.tag == 'move':
                if man is None:
                    man = self.manifest()
                source, dest = elt.get('from'), elt.get('to')
                if source in man:
                    # File move
                    changes.append((source, rev))
                    changes.append((dest, rev))
                    copies[dest] = source
                else:
                    # Directory move, deduce file moves from manifest
                    source = source + '/'
                    for f in man:
                        if not f.startswith(source):
                            continue
                        fdest = dest + '/' + f[len(source):]
                        changes.append((f, rev))
                        changes.append((fdest, rev))
                        copies[fdest] = f
            else:
                changes.append((elt.text.strip(), rev))
        self.pull(rev)
        self.lastrev = rev
        return sorted(changes), copies

    def getfile(self, name, rev):
        if rev != self.lastrev:
            raise util.Abort(_('internal calling inconsistency'))
        return open(os.path.join(self.tmppath, name), 'rb').read()

    def getmode(self, name, rev):
        mode = os.lstat(os.path.join(self.tmppath, name)).st_mode
        return (mode & 0111) and 'x' or ''

    def gettags(self):
        return self.tags
