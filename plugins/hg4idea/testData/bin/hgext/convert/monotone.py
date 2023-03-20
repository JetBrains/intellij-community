# monotone.py - monotone support for the convert extension
#
#  Copyright 2008, 2009 Mikkel Fahnoe Jorgensen <mikkel@dvide.com> and
#  others
#
# This software may be used and distributed according to the terms of the
# GNU General Public License version 2 or any later version.
from __future__ import absolute_import

import os
import re

from mercurial.i18n import _
from mercurial.pycompat import open
from mercurial import (
    error,
    pycompat,
)
from mercurial.utils import dateutil

from . import common


class monotone_source(common.converter_source, common.commandline):
    def __init__(self, ui, repotype, path=None, revs=None):
        common.converter_source.__init__(self, ui, repotype, path, revs)
        if revs and len(revs) > 1:
            raise error.Abort(
                _(
                    b'monotone source does not support specifying '
                    b'multiple revs'
                )
            )
        common.commandline.__init__(self, ui, b'mtn')

        self.ui = ui
        self.path = path
        self.automatestdio = False
        self.revs = revs

        norepo = common.NoRepo(
            _(b"%s does not look like a monotone repository") % path
        )
        if not os.path.exists(os.path.join(path, b'_MTN')):
            # Could be a monotone repository (SQLite db file)
            try:
                f = open(path, b'rb')
                header = f.read(16)
                f.close()
            except IOError:
                header = b''
            if header != b'SQLite format 3\x00':
                raise norepo

        # regular expressions for parsing monotone output
        space = br'\s*'
        name = br'\s+"((?:\\"|[^"])*)"\s*'
        value = name
        revision = br'\s+\[(\w+)\]\s*'
        lines = br'(?:.|\n)+'

        self.dir_re = re.compile(space + b"dir" + name)
        self.file_re = re.compile(
            space + b"file" + name + b"content" + revision
        )
        self.add_file_re = re.compile(
            space + b"add_file" + name + b"content" + revision
        )
        self.patch_re = re.compile(
            space + b"patch" + name + b"from" + revision + b"to" + revision
        )
        self.rename_re = re.compile(space + b"rename" + name + b"to" + name)
        self.delete_re = re.compile(space + b"delete" + name)
        self.tag_re = re.compile(space + b"tag" + name + b"revision" + revision)
        self.cert_re = re.compile(
            lines + space + b"name" + name + b"value" + value
        )

        attr = space + b"file" + lines + space + b"attr" + space
        self.attr_execute_re = re.compile(
            attr + b'"mtn:execute"' + space + b'"true"'
        )

        # cached data
        self.manifest_rev = None
        self.manifest = None
        self.files = None
        self.dirs = None

        common.checktool(b'mtn', abort=False)

    def mtnrun(self, *args, **kwargs):
        if self.automatestdio:
            return self.mtnrunstdio(*args, **kwargs)
        else:
            return self.mtnrunsingle(*args, **kwargs)

    def mtnrunsingle(self, *args, **kwargs):
        kwargs['d'] = self.path
        return self.run0(b'automate', *args, **kwargs)

    def mtnrunstdio(self, *args, **kwargs):
        # Prepare the command in automate stdio format
        kwargs = pycompat.byteskwargs(kwargs)
        command = []
        for k, v in pycompat.iteritems(kwargs):
            command.append(b"%d:%s" % (len(k), k))
            if v:
                command.append(b"%d:%s" % (len(v), v))
        if command:
            command.insert(0, b'o')
            command.append(b'e')

        command.append(b'l')
        for arg in args:
            command.append(b"%d:%s" % (len(arg), arg))
        command.append(b'e')
        command = b''.join(command)

        self.ui.debug(b"mtn: sending '%s'\n" % command)
        self.mtnwritefp.write(command)
        self.mtnwritefp.flush()

        return self.mtnstdioreadcommandoutput(command)

    def mtnstdioreadpacket(self):
        read = None
        commandnbr = b''
        while read != b':':
            read = self.mtnreadfp.read(1)
            if not read:
                raise error.Abort(_(b'bad mtn packet - no end of commandnbr'))
            commandnbr += read
        commandnbr = commandnbr[:-1]

        stream = self.mtnreadfp.read(1)
        if stream not in b'mewptl':
            raise error.Abort(
                _(b'bad mtn packet - bad stream type %s') % stream
            )

        read = self.mtnreadfp.read(1)
        if read != b':':
            raise error.Abort(_(b'bad mtn packet - no divider before size'))

        read = None
        lengthstr = b''
        while read != b':':
            read = self.mtnreadfp.read(1)
            if not read:
                raise error.Abort(_(b'bad mtn packet - no end of packet size'))
            lengthstr += read
        try:
            length = pycompat.long(lengthstr[:-1])
        except TypeError:
            raise error.Abort(
                _(b'bad mtn packet - bad packet size %s') % lengthstr
            )

        read = self.mtnreadfp.read(length)
        if len(read) != length:
            raise error.Abort(
                _(
                    b"bad mtn packet - unable to read full packet "
                    b"read %s of %s"
                )
                % (len(read), length)
            )

        return (commandnbr, stream, length, read)

    def mtnstdioreadcommandoutput(self, command):
        retval = []
        while True:
            commandnbr, stream, length, output = self.mtnstdioreadpacket()
            self.ui.debug(
                b'mtn: read packet %s:%s:%d\n' % (commandnbr, stream, length)
            )

            if stream == b'l':
                # End of command
                if output != b'0':
                    raise error.Abort(
                        _(b"mtn command '%s' returned %s") % (command, output)
                    )
                break
            elif stream in b'ew':
                # Error, warning output
                self.ui.warn(_(b'%s error:\n') % self.command)
                self.ui.warn(output)
            elif stream == b'p':
                # Progress messages
                self.ui.debug(b'mtn: ' + output)
            elif stream == b'm':
                # Main stream - command output
                retval.append(output)

        return b''.join(retval)

    def mtnloadmanifest(self, rev):
        if self.manifest_rev == rev:
            return
        self.manifest = self.mtnrun(b"get_manifest_of", rev).split(b"\n\n")
        self.manifest_rev = rev
        self.files = {}
        self.dirs = {}

        for e in self.manifest:
            m = self.file_re.match(e)
            if m:
                attr = b""
                name = m.group(1)
                node = m.group(2)
                if self.attr_execute_re.match(e):
                    attr += b"x"
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
        certs = {
            b"author": b"<missing>",
            b"date": b"<missing>",
            b"changelog": b"<missing>",
            b"branch": b"<missing>",
        }
        certlist = self.mtnrun(b"certs", rev)
        # mtn < 0.45:
        #   key "test@selenic.com"
        # mtn >= 0.45:
        #   key [ff58a7ffb771907c4ff68995eada1c4da068d328]
        certlist = re.split(br'\n\n {6}key ["\[]', certlist)
        for e in certlist:
            m = self.cert_re.match(e)
            if m:
                name, value = m.groups()
                value = value.replace(br'\"', b'"')
                value = value.replace(br'\\', b'\\')
                certs[name] = value
        # Monotone may have subsecond dates: 2005-02-05T09:39:12.364306
        # and all times are stored in UTC
        certs[b"date"] = certs[b"date"].split(b'.')[0] + b" UTC"
        return certs

    # implement the converter_source interface:

    def getheads(self):
        if not self.revs:
            return self.mtnrun(b"leaves").splitlines()
        else:
            return self.revs

    def getchanges(self, rev, full):
        if full:
            raise error.Abort(
                _(b"convert from monotone does not support --full")
            )
        revision = self.mtnrun(b"get_revision", rev).split(b"\n\n")
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
                if tofile.startswith(todir + b'/'):
                    renamed[tofile] = fromdir + tofile[len(todir) :]
                    # Avoid chained moves like:
                    # d1(/a) => d3/d1(/a)
                    # d2 => d3
                    ignoremove[tofile] = 1
            for tofile, fromfile in renamed.items():
                self.ui.debug(
                    b"copying file in renamed directory from '%s' to '%s'"
                    % (fromfile, tofile),
                    b'\n',
                )
                files[tofile] = rev
                copies[tofile] = fromfile
            for fromfile in renamed.values():
                files[fromfile] = rev

        return (files.items(), copies, set())

    def getfile(self, name, rev):
        if not self.mtnisfile(name, rev):
            return None, None
        try:
            data = self.mtnrun(b"get_file_of", name, r=rev)
        except Exception:
            return None, None
        self.mtnloadmanifest(rev)
        node, attr = self.files.get(name, (None, b""))
        return data, attr

    def getcommit(self, rev):
        extra = {}
        certs = self.mtngetcerts(rev)
        if certs.get(b'suspend') == certs[b"branch"]:
            extra[b'close'] = b'1'
        dateformat = b"%Y-%m-%dT%H:%M:%S"
        return common.commit(
            author=certs[b"author"],
            date=dateutil.datestr(dateutil.strdate(certs[b"date"], dateformat)),
            desc=certs[b"changelog"],
            rev=rev,
            parents=self.mtnrun(b"parents", rev).splitlines(),
            branch=certs[b"branch"],
            extra=extra,
        )

    def gettags(self):
        tags = {}
        for e in self.mtnrun(b"tags").split(b"\n\n"):
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
        try:
            versionstr = self.mtnrunsingle(b"interface_version")
            version = float(versionstr)
        except Exception:
            raise error.Abort(
                _(b"unable to determine mtn automate interface version")
            )

        if version >= 12.0:
            self.automatestdio = True
            self.ui.debug(
                b"mtn automate version %f - using automate stdio\n" % version
            )

            # launch the long-running automate stdio process
            self.mtnwritefp, self.mtnreadfp = self._run2(
                b'automate', b'stdio', b'-d', self.path
            )
            # read the headers
            read = self.mtnreadfp.readline()
            if read != b'format-version: 2\n':
                raise error.Abort(
                    _(b'mtn automate stdio header unexpected: %s') % read
                )
            while read != b'\n':
                read = self.mtnreadfp.readline()
                if not read:
                    raise error.Abort(
                        _(
                            b"failed to reach end of mtn automate "
                            b"stdio headers"
                        )
                    )
        else:
            self.ui.debug(
                b"mtn automate version %s - not using automate stdio "
                b"(automate >= 12.0 - mtn >= 0.46 is needed)\n" % version
            )

    def after(self):
        if self.automatestdio:
            self.mtnwritefp.close()
            self.mtnwritefp = None
            self.mtnreadfp.close()
            self.mtnreadfp = None
