# monotone.py - monotone support for the convert extension
#
#  Copyright 2008, 2009 Mikkel Fahnoe Jorgensen <mikkel@dvide.com> and
#  others
#
# This software may be used and distributed according to the terms of the
# GNU General Public License version 2 or any later version.

import os, re
from mercurial import util
from common import NoRepo, commit, converter_source, checktool
from common import commandline
from mercurial.i18n import _

class monotone_source(converter_source, commandline):
    def __init__(self, ui, path=None, rev=None):
        converter_source.__init__(self, ui, path, rev)
        commandline.__init__(self, ui, 'mtn')

        self.ui = ui
        self.path = path

        norepo = NoRepo(_("%s does not look like a monotone repository")
                        % path)
        if not os.path.exists(os.path.join(path, '_MTN')):
            # Could be a monotone repository (SQLite db file)
            try:
                header = file(path, 'rb').read(16)
            except:
                header = ''
            if header != 'SQLite format 3\x00':
                raise norepo

        # regular expressions for parsing monotone output
        space    = r'\s*'
        name     = r'\s+"((?:\\"|[^"])*)"\s*'
        value    = name
        revision = r'\s+\[(\w+)\]\s*'
        lines    = r'(?:.|\n)+'

        self.dir_re      = re.compile(space + "dir" + name)
        self.file_re     = re.compile(space + "file" + name +
                                      "content" + revision)
        self.add_file_re = re.compile(space + "add_file" + name +
                                      "content" + revision)
        self.patch_re    = re.compile(space + "patch" + name +
                                      "from" + revision + "to" + revision)
        self.rename_re   = re.compile(space + "rename" + name + "to" + name)
        self.delete_re   = re.compile(space + "delete" + name)
        self.tag_re      = re.compile(space + "tag" + name + "revision" +
                                      revision)
        self.cert_re     = re.compile(lines + space + "name" + name +
                                      "value" + value)

        attr = space + "file" + lines + space + "attr" + space
        self.attr_execute_re = re.compile(attr  + '"mtn:execute"' +
                                          space + '"true"')

        # cached data
        self.manifest_rev = None
        self.manifest = None
        self.files = None
        self.dirs  = None

        checktool('mtn', abort=False)

        # test if there are any revisions
        self.rev = None
        try:
            self.getheads()
        except:
            raise norepo
        self.rev = rev

    def mtnrun(self, *args, **kwargs):
        kwargs['d'] = self.path
        return self.run0('automate', *args, **kwargs)

    def mtnloadmanifest(self, rev):
        if self.manifest_rev == rev:
            return
        self.manifest = self.mtnrun("get_manifest_of", rev).split("\n\n")
        self.manifest_rev = rev
        self.files = {}
        self.dirs = {}

        for e in self.manifest:
            m = self.file_re.match(e)
            if m:
                attr = ""
                name = m.group(1)
                node = m.group(2)
                if self.attr_execute_re.match(e):
                    attr += "x"
                self.files[name] = (node, attr)
            m = self.dir_re.match(e)
            if m:
                self.dirs[m.group(1)] = True

    def mtnisfile(self, name, rev):
        # a non-file could be a directory or a deleted or renamed file
        self.mtnloadmanifest(rev)
        return name in self.files

    def mtnisdir(self, name, rev):
        self.mtnloadmanifest(rev)
        return name in self.dirs

    def mtngetcerts(self, rev):
        certs = {"author":"<missing>", "date":"<missing>",
            "changelog":"<missing>", "branch":"<missing>"}
        certlist = self.mtnrun("certs", rev)
        # mtn < 0.45:
        #   key "test@selenic.com"
        # mtn >= 0.45:
        #   key [ff58a7ffb771907c4ff68995eada1c4da068d328]
        certlist = re.split('\n\n      key ["\[]', certlist)
        for e in certlist:
            m = self.cert_re.match(e)
            if m:
                name, value = m.groups()
                value = value.replace(r'\"', '"')
                value = value.replace(r'\\', '\\')
                certs[name] = value
        # Monotone may have subsecond dates: 2005-02-05T09:39:12.364306
        # and all times are stored in UTC
        certs["date"] = certs["date"].split('.')[0] + " UTC"
        return certs

    # implement the converter_source interface:

    def getheads(self):
        if not self.rev:
            return self.mtnrun("leaves").splitlines()
        else:
            return [self.rev]

    def getchanges(self, rev):
        #revision = self.mtncmd("get_revision %s" % rev).split("\n\n")
        revision = self.mtnrun("get_revision", rev).split("\n\n")
        files = {}
        ignoremove = {}
        renameddirs = []
        copies = {}
        for e in revision:
            m = self.add_file_re.match(e)
            if m:
                files[m.group(1)] = rev
                ignoremove[m.group(1)] = rev
            m = self.patch_re.match(e)
            if m:
                files[m.group(1)] = rev
            # Delete/rename is handled later when the convert engine
            # discovers an IOError exception from getfile,
            # but only if we add the "from" file to the list of changes.
            m = self.delete_re.match(e)
            if m:
                files[m.group(1)] = rev
            m = self.rename_re.match(e)
            if m:
                toname = m.group(2)
                fromname = m.group(1)
                if self.mtnisfile(toname, rev):
                    ignoremove[toname] = 1
                    copies[toname] = fromname
                    files[toname] = rev
                    files[fromname] = rev
                elif self.mtnisdir(toname, rev):
                    renameddirs.append((fromname, toname))

        # Directory renames can be handled only once we have recorded
        # all new files
        for fromdir, todir in renameddirs:
            renamed = {}
            for tofile in self.files:
                if tofile in ignoremove:
                    continue
                if tofile.startswith(todir + '/'):
                    renamed[tofile] = fromdir + tofile[len(todir):]
                    # Avoid chained moves like:
                    # d1(/a) => d3/d1(/a)
                    # d2 => d3
                    ignoremove[tofile] = 1
            for tofile, fromfile in renamed.items():
                self.ui.debug (_("copying file in renamed directory "
                                 "from '%s' to '%s'")
                               % (fromfile, tofile), '\n')
                files[tofile] = rev
                copies[tofile] = fromfile
            for fromfile in renamed.values():
                files[fromfile] = rev

        return (files.items(), copies)

    def getmode(self, name, rev):
        self.mtnloadmanifest(rev)
        node, attr = self.files.get(name, (None, ""))
        return attr

    def getfile(self, name, rev):
        if not self.mtnisfile(name, rev):
            raise IOError() # file was deleted or renamed
        try:
            return self.mtnrun("get_file_of", name, r=rev)
        except:
            raise IOError() # file was deleted or renamed

    def getcommit(self, rev):
        certs   = self.mtngetcerts(rev)
        return commit(
            author=certs["author"],
            date=util.datestr(util.strdate(certs["date"], "%Y-%m-%dT%H:%M:%S")),
            desc=certs["changelog"],
            rev=rev,
            parents=self.mtnrun("parents", rev).splitlines(),
            branch=certs["branch"])

    def gettags(self):
        tags = {}
        for e in self.mtnrun("tags").split("\n\n"):
            m = self.tag_re.match(e)
            if m:
                tags[m.group(1)] = m.group(2)
        return tags

    def getchangedfiles(self, rev, i):
        # This function is only needed to support --filemap
        # ... and we don't support that
        raise NotImplementedError()
