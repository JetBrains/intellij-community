# config.py - configuration parsing for Mercurial
#
#  Copyright 2009 Matt Mackall <mpm@selenic.com> and others
#
# This software may be used and distributed according to the terms of the
# GNU General Public License version 2 or any later version.

from i18n import _
import error, util
import os, errno

class sortdict(dict):
    'a simple sorted dictionary'
    def __init__(self, data=None):
        self._list = []
        if data:
            self.update(data)
    def copy(self):
        return sortdict(self)
    def __setitem__(self, key, val):
        if key in self:
            self._list.remove(key)
        self._list.append(key)
        dict.__setitem__(self, key, val)
    def __iter__(self):
        return self._list.__iter__()
    def update(self, src):
        for k in src:
            self[k] = src[k]
    def clear(self):
        dict.clear(self)
        self._list = []
    def items(self):
        return [(k, self[k]) for k in self._list]
    def __delitem__(self, key):
        dict.__delitem__(self, key)
        self._list.remove(key)
    def keys(self):
        return self._list
    def iterkeys(self):
        return self._list.__iter__()

class config(object):
    def __init__(self, data=None):
        self._data = {}
        self._source = {}
        self._unset = []
        if data:
            for k in data._data:
                self._data[k] = data[k].copy()
            self._source = data._source.copy()
    def copy(self):
        return config(self)
    def __contains__(self, section):
        return section in self._data
    def __getitem__(self, section):
        return self._data.get(section, {})
    def __iter__(self):
        for d in self.sections():
            yield d
    def update(self, src):
        for s, n in src._unset:
            if s in self and n in self._data[s]:
                del self._data[s][n]
                del self._source[(s, n)]
        for s in src:
            if s not in self:
                self._data[s] = sortdict()
            self._data[s].update(src._data[s])
        self._source.update(src._source)
    def get(self, section, item, default=None):
        return self._data.get(section, {}).get(item, default)

    def backup(self, section, item):
        """return a tuple allowing restore to reinstall a previous value

        The main reason we need it is because it handles the "no data" case.
        """
        try:
            value = self._data[section][item]
            source = self.source(section, item)
            return (section, item, value, source)
        except KeyError:
            return (section, item)

    def source(self, section, item):
        return self._source.get((section, item), "")
    def sections(self):
        return sorted(self._data.keys())
    def items(self, section):
        return self._data.get(section, {}).items()
    def set(self, section, item, value, source=""):
        if section not in self:
            self._data[section] = sortdict()
        self._data[section][item] = value
        self._source[(section, item)] = source

    def restore(self, data):
        """restore data returned by self.backup"""
        if len(data) == 4:
            # restore old data
            section, item, value, source = data
            self._data[section][item] = value
            self._source[(section, item)] = source
        else:
            # no data before, remove everything
            section, item = data
            if section in self._data:
                del self._data[section][item]
            self._source.pop((section, item), None)

    def parse(self, src, data, sections=None, remap=None, include=None):
        sectionre = util.compilere(r'\[([^\[]+)\]')
        itemre = util.compilere(r'([^=\s][^=]*?)\s*=\s*(.*\S|)')
        contre = util.compilere(r'\s+(\S|\S.*\S)\s*$')
        emptyre = util.compilere(r'(;|#|\s*$)')
        commentre = util.compilere(r'(;|#)')
        unsetre = util.compilere(r'%unset\s+(\S+)')
        includere = util.compilere(r'%include\s+(\S|\S.*\S)\s*$')
        section = ""
        item = None
        line = 0
        cont = False

        for l in data.splitlines(True):
            line += 1
            if line == 1 and l.startswith('\xef\xbb\xbf'):
                # Someone set us up the BOM
                l = l[3:]
            if cont:
                if commentre.match(l):
                    continue
                m = contre.match(l)
                if m:
                    if sections and section not in sections:
                        continue
                    v = self.get(section, item) + "\n" + m.group(1)
                    self.set(section, item, v, "%s:%d" % (src, line))
                    continue
                item = None
                cont = False
            m = includere.match(l)
            if m:
                inc = util.expandpath(m.group(1))
                base = os.path.dirname(src)
                inc = os.path.normpath(os.path.join(base, inc))
                if include:
                    try:
                        include(inc, remap=remap, sections=sections)
                    except IOError, inst:
                        if inst.errno != errno.ENOENT:
                            raise error.ParseError(_("cannot include %s (%s)")
                                                   % (inc, inst.strerror),
                                                   "%s:%s" % (src, line))
                continue
            if emptyre.match(l):
                continue
            m = sectionre.match(l)
            if m:
                section = m.group(1)
                if remap:
                    section = remap.get(section, section)
                if section not in self:
                    self._data[section] = sortdict()
                continue
            m = itemre.match(l)
            if m:
                item = m.group(1)
                cont = True
                if sections and section not in sections:
                    continue
                self.set(section, item, m.group(2), "%s:%d" % (src, line))
                continue
            m = unsetre.match(l)
            if m:
                name = m.group(1)
                if sections and section not in sections:
                    continue
                if self.get(section, name) is not None:
                    del self._data[section][name]
                self._unset.append((section, name))
                continue

            raise error.ParseError(l.rstrip(), ("%s:%s" % (src, line)))

    def read(self, path, fp=None, sections=None, remap=None):
        if not fp:
            fp = util.posixfile(path)
        self.parse(path, fp.read(), sections, remap, self.read)
