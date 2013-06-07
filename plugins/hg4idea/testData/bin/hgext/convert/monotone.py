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
        self.automatestdio = False
        self.rev = rev

        norepo = NoRepo(_("%s does not look like a monotone repository")
                        % path)
        if not os.path.exists(os.path.join(path, '_MTN')):
            # Could be a monotone repository (SQLite db file)
            try:
                f = file(path, 'rb')
                header = f.read(16)
                f.close()
            except IOError:
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

    def mtnrun(self, *args, **kwargs):
        if self.automatestdio:
            return self.mtnrunstdio(*args, **kwargs)
        else:
            return self.mtnrunsingle(*args, **kwargs)

    def mtnrunsingle(self, *args, **kwargs):
        kwargs['d'] = self.path
        return self.run0('automate', *args, **kwargs)

    def mtnrunstdio(self, *args, **kwargs):
        # Prepare the command in automate stdio format
        command = []
        for k, v in kwargs.iteritems():
            command.append("%s:%s" % (len(k), k))
            if v:
                command.append("%s:%s" % (len(v), v))
        if command:
            command.insert(0, 'o')
            command.append('e')

        command.append('l')
        for arg in args:
            command += "%s:%s" % (len(arg), arg)
        command.append('e')
        command = ''.join(command)

        self.ui.debug("mtn: sending '%s'\n" % command)
        self.mtnwritefp.write(command)
        self.mtnwritefp.flush()

        return self.mtnstdioreadcommandoutput(command)

    def mtnstdioreadpacket(self):
        read = None
        commandnbr = ''
        while read != ':':
            read = self.mtnreadfp.read(1)
            if not read:
                raise util.Abort(_('bad mtn packet - no end of commandnbr'))
            commandnbr += read
        commandnbr = commandnbr[:-1]

        stream = self.mtnreadfp.read(1)
        if stream not in 'mewptl':
            raise util.Abort(_('bad mtn packet - bad stream type %s') % stream)

        read = self.mtnreadfp.read(1)
        if read != ':':
            raise util.Abort(_('bad mtn packet - no divider before size'))

        read = None
        lengthstr = ''
        while read != ':':
            read = self.mtnreadfp.read(1)
            if not read:
                raise util.Abort(_('bad mtn packet - no end of packet size'))
            lengthstr += read
        try:
            length = long(lengthstr[:-1])
        except TypeError:
            raise util.Abort(_('bad mtn packet - bad packet size %s')
                % lengthstr)

        read = self.mtnreadfp.read(length)
        if len(read) != length:
            raise util.Abort(_("bad mtn packet - unable to read full packet "
                "read %s of %s") % (len(read), length))

        return (commandnbr, stream, length, read)

    def mtnstdioreadcommandoutput(self, command):
        retval = []
        while True:
            commandnbr, stream, length, output = self.mtnstdioreadpacket()
            self.ui.debug('mtn: read packet %s:%s:%s\n' %
                (commandnbr, stream, length))

            if stream == 'l':
                # End of command
                if output != '0':
                    raise util.Abort(_("mtn command '%s' returned %s") %
                        (command, output))
                break
            elif stream in 'ew':
                # Error, warning output
                self.ui.warn(_('%s error:\n') % self.command)
                self.ui.warn(output)
            elif stream == 'p':
                # Progress messages
                self.ui.debug('mtn: ' + output)
            elif stream == 'm':
                # Main stream - command output
                retval.append(output)

        return ''.join(retval)

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

    def getfile(self, name, rev):
        if not self.mtnisfile(name, rev):
            raise IOError # file was deleted or renamed
        try:
            data = self.mtnrun("get_file_of", name, r=rev)
        except Exception:
            raise IOError # file was deleted or renamed
        self.mtnloadmanifest(rev)
        node, attr = self.files.get(name, (None, ""))
        return data, attr

    def getcommit(self, rev):
        extra = {}
        certs = self.mtngetcerts(rev)
        if certs.get('suspend') == certs["branch"]:
            extra['close'] = '1'
        return commit(
            author=certs["author"],
            date=util.datestr(util.strdate(certs["date"], "%Y-%m-%dT%H:%M:%S")),
            desc=certs["changelog"],
            rev=rev,
            parents=self.mtnrun("parents", rev).splitlines(),
            branch=certs["branch"],
            extra=extra)

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
        raise NotImplementedError

    def before(self):
        # Check if we have a new enough version to use automate stdio
        version = 0.0
        try:
            versionstr = self.mtnrunsingle("interface_version")
            version = float(versionstr)
        except Exception:
            raise util.Abort(_("unable to determine mtn automate interface "
                "version"))

        if version >= 12.0:
            self.automatestdio = True
            self.ui.debug("mtn automate version %s - using automate stdio\n" %
                version)

            # launch the long-running automate stdio process
            self.mtnwritefp, self.mtnreadfp = self._run2('automate', 'stdio',
                '-d', self.path)
            # read the headers
            read = self.mtnreadfp.readline()
            if read != 'format-version: 2\n':
                raise util.Abort(_('mtn automate stdio header unexpected: %s')
                    % read)
            while read != '\n':
                read = self.mtnreadfp.readline()
                if not read:
                    raise util.Abort(_("failed to reach end of mtn automate "
                        "stdio headers"))
        else:
            self.ui.debug("mtn automate version %s - not using automate stdio "
                "(automate >= 12.0 - mtn >= 0.46 is needed)\n" % version)

    def after(self):
        if self.automatestdio:
            self.mtnwritefp.close()
            self.mtnwritefp = None
            self.mtnreadfp.close()
            self.mtnreadfp = None

