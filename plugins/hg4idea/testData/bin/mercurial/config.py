# config.py - configuration parsing for Mercurial
#
#  Copyright 2009 Olivia Mackall <olivia@selenic.com> and others
#
# This software may be used and distributed according to the terms of the
# GNU General Public License version 2 or any later version.


import errno
import os

from typing import (
    List,
    Tuple,
)

from .i18n import _
from . import (
    encoding,
    error,
    util,
)


class config:
    def __init__(self, data=None):
        self._current_source_level = 0
        self._data = {}
        self._unset = []
        if data:
            for k in data._data:
                self._data[k] = data[k].copy()
            self._current_source_level = data._current_source_level + 1

    def new_source(self):
        """increment the source counter

        This is used to define source priority when reading"""
        self._current_source_level += 1

    def copy(self):
        return config(self)

    def __contains__(self, section):
        return section in self._data

    def hasitem(self, section, item):
        return item in self._data.get(section, {})

    def __getitem__(self, section):
        return self._data.get(section, {})

    def __iter__(self):
        for d in self.sections():
            yield d

    def update(self, src):
        current_level = self._current_source_level
        current_level += 1
        max_level = self._current_source_level
        for s, n in src._unset:
            ds = self._data.get(s, None)
            if ds is not None and n in ds:
                self._data[s] = ds.preparewrite()
                del self._data[s][n]
        for s in src:
            ds = self._data.get(s, None)
            if ds:
                self._data[s] = ds.preparewrite()
            else:
                self._data[s] = util.cowsortdict()
            for k, v in src._data[s].items():
                value, source, level = v
                level += current_level
                max_level = max(level, current_level)
                self._data[s][k] = (value, source, level)
        self._current_source_level = max_level

    def _get(self, section, item):
        return self._data.get(section, {}).get(item)

    def get(self, section, item, default=None):
        result = self._get(section, item)
        if result is None:
            return default
        return result[0]

    def backup(self, section, key):
        """return a tuple allowing restore to reinstall a previous value

        The main reason we need it is because it handles the "no data" case.
        """
        try:
            item = self._data[section][key]
        except KeyError:
            return (section, key)
        else:
            return (section, key) + item

    def source(self, section, item):
        result = self._get(section, item)
        if result is None:
            return b""
        return result[1]

    def level(self, section, item):
        result = self._get(section, item)
        if result is None:
            return None
        return result[2]

    def sections(self):
        return sorted(self._data.keys())

    def items(self, section: bytes) -> List[Tuple[bytes, bytes]]:
        items = self._data.get(section, {}).items()
        return [(k, v[0]) for (k, v) in items]

    def set(self, section, item, value, source=b""):
        assert not isinstance(
            section, str
        ), b'config section may not be unicode strings on Python 3'
        assert not isinstance(
            item, str
        ), b'config item may not be unicode strings on Python 3'
        assert not isinstance(
            value, str
        ), b'config values may not be unicode strings on Python 3'
        if section not in self:
            self._data[section] = util.cowsortdict()
        else:
            self._data[section] = self._data[section].preparewrite()
        self._data[section][item] = (value, source, self._current_source_level)

    def alter(self, section, key, new_value):
        """alter a value without altering its source or level

        This method is meant to be used by `ui.fixconfig` only."""
        item = self._data[section][key]
        size = len(item)
        new_item = (new_value,) + item[1:]
        assert len(new_item) == size
        self._data[section][key] = new_item

    def restore(self, data):
        """restore data returned by self.backup"""
        if len(data) != 2:
            # restore old data
            section, key = data[:2]
            item = data[2:]
            self._data[section] = self._data[section].preparewrite()
            self._data[section][key] = item
        else:
            # no data before, remove everything
            section, item = data
            if section in self._data:
                self._data[section].pop(item, None)

    def parse(self, src, data, sections=None, remap=None, include=None):
        sectionre = util.re.compile(br'\[([^\[]+)\]')
        itemre = util.re.compile(br'([^=\s][^=]*?)\s*=\s*(.*\S|)')
        contre = util.re.compile(br'\s+(\S|\S.*\S)\s*$')
        emptyre = util.re.compile(br'(;|#|\s*$)')
        commentre = util.re.compile(br'(;|#)')
        unsetre = util.re.compile(br'%unset\s+(\S+)')
        includere = util.re.compile(br'%include\s+(\S|\S.*\S)\s*$')
        section = b""
        item = None
        line = 0
        cont = False

        if remap:
            section = remap.get(section, section)

        for l in data.splitlines(True):
            line += 1
            if line == 1 and l.startswith(b'\xef\xbb\xbf'):
                # Someone set us up the BOM
                l = l[3:]
            if cont:
                if commentre.match(l):
                    continue
                m = contre.match(l)
                if m:
                    if sections and section not in sections:
                        continue
                    v = self.get(section, item) + b"\n" + m.group(1)
                    self.set(section, item, v, b"%s:%d" % (src, line))
                    continue
                item = None
                cont = False
            m = includere.match(l)

            if m and include:
                expanded = util.expandpath(m.group(1))
                try:
                    include(expanded, remap=remap, sections=sections)
                except IOError as inst:
                    if inst.errno != errno.ENOENT:
                        raise error.ConfigError(
                            _(b"cannot include %s (%s)")
                            % (expanded, encoding.strtolocal(inst.strerror)),
                            b"%s:%d" % (src, line),
                        )
                continue
            if emptyre.match(l):
                continue
            m = sectionre.match(l)
            if m:
                section = m.group(1)
                if remap:
                    section = remap.get(section, section)
                if section not in self:
                    self._data[section] = util.cowsortdict()
                continue
            m = itemre.match(l)
            if m:
                item = m.group(1)
                cont = True
                if sections and section not in sections:
                    continue
                self.set(section, item, m.group(2), b"%s:%d" % (src, line))
                continue
            m = unsetre.match(l)
            if m:
                name = m.group(1)
                if sections and section not in sections:
                    continue
                if self.get(section, name) is not None:
                    self._data[section] = self._data[section].preparewrite()
                    del self._data[section][name]
                self._unset.append((section, name))
                continue

            message = l.rstrip()
            if l.startswith(b' '):
                message = b"unexpected leading whitespace: %s" % message
            raise error.ConfigError(message, (b"%s:%d" % (src, line)))

    def read(self, path, fp=None, sections=None, remap=None):
        self.new_source()
        if not fp:
            fp = util.posixfile(path, b'rb')
        assert (
            getattr(fp, 'mode', 'rb') == 'rb'
        ), b'config files must be opened in binary mode, got fp=%r mode=%r' % (
            fp,
            fp.mode,
        )

        dir = os.path.dirname(path)

        def include(rel, remap, sections):
            abs = os.path.normpath(os.path.join(dir, rel))
            self.read(abs, remap=remap, sections=sections)
            # anything after the include has a higher level
            self.new_source()

        self.parse(
            path, fp.read(), sections=sections, remap=remap, include=include
        )
