# configitems.py - centralized declaration of configuration option
#
#  Copyright 2017 Pierre-Yves David <pierre-yves.david@octobus.net>
#
# This software may be used and distributed according to the terms of the
# GNU General Public License version 2 or any later version.


import functools
import re

from .utils import resourceutil

from . import (
    encoding,
    error,
)

try:
    import tomllib  # pytype: disable=import-error

    tomllib.load  # trigger lazy import
except ModuleNotFoundError:
    # Python <3.11 compat
    from .thirdparty import tomli as tomllib


def loadconfigtable(ui, extname, configtable):
    """update config item known to the ui with the extension ones"""
    for section, items in sorted(configtable.items()):
        knownitems = ui._knownconfig.setdefault(section, itemregister())
        knownkeys = set(knownitems)
        newkeys = set(items)
        for key in sorted(knownkeys & newkeys):
            msg = b"extension '%s' overwrites config item '%s.%s'"
            msg %= (extname, section, key)
            ui.develwarn(msg, config=b'warn-config')

        knownitems.update(items)


class configitem:
    """represent a known config item

    :section: the official config section where to find this item,
       :name: the official name within the section,
    :default: default value for this item,
    :alias: optional list of tuples as alternatives,
    :generic: this is a generic definition, match name using regular expression.
    """

    def __init__(
        self,
        section,
        name,
        default=None,
        alias=(),
        generic=False,
        priority=0,
        experimental=False,
        documentation="",
        in_core_extension=None,
    ):
        self.section = section
        self.name = name
        self.default = default
        self.documentation = documentation
        self.alias = list(alias)
        self.generic = generic
        self.priority = priority
        self.experimental = experimental
        self._re = None
        self.in_core_extension = in_core_extension
        if generic:
            self._re = re.compile(self.name)


class itemregister(dict):
    """A specialized dictionary that can handle wild-card selection"""

    def __init__(self):
        super(itemregister, self).__init__()
        self._generics = set()

    def update(self, other):  # pytype: disable=signature-mismatch
        super(itemregister, self).update(other)
        self._generics.update(other._generics)

    def __setitem__(self, key, item):
        super(itemregister, self).__setitem__(key, item)
        if item.generic:
            self._generics.add(item)

    def get(self, key):
        baseitem = super(itemregister, self).get(key)
        if baseitem is not None and not baseitem.generic:
            return baseitem

        # search for a matching generic item
        generics = sorted(self._generics, key=(lambda x: (x.priority, x.name)))
        for item in generics:
            # we use 'match' instead of 'search' to make the matching simpler
            # for people unfamiliar with regular expression. Having the match
            # rooted to the start of the string will produce less surprising
            # result for user writing simple regex for sub-attribute.
            #
            # For example using "color\..*" match produces an unsurprising
            # result, while using search could suddenly match apparently
            # unrelated configuration that happens to contains "color."
            # anywhere. This is a tradeoff where we favor requiring ".*" on
            # some match to avoid the need to prefix most pattern with "^".
            # The "^" seems more error prone.
            if item._re.match(key):
                return item

        return None


def sanitize_item(item):
    """Apply the transformations that are encoded on top of the pure data"""

    # Set the special defaults
    default_type_key = "default-type"
    default_type = item.pop(default_type_key, None)
    if default_type == "dynamic":
        item["default"] = dynamicdefault
    elif default_type == "list_type":
        item["default"] = list
    elif default_type == "lambda":
        assert isinstance(item["default"], list)
        default = [e.encode() for e in item["default"]]
        item["default"] = lambda: default
    elif default_type == "lazy_module":
        item["default"] = lambda: encoding.encoding
    else:
        if default_type is not None:
            msg = "invalid default config type %r for '%s.%s'"
            msg %= (default_type, item["section"], item["name"])
            raise error.ProgrammingError(msg)

    # config expects bytes
    alias = item.get("alias")
    if alias:
        item["alias"] = [(k.encode(), v.encode()) for (k, v) in alias]
    if isinstance(item.get("default"), str):
        item["default"] = item["default"].encode()
    item["section"] = item["section"].encode()
    item["name"] = item["name"].encode()


def read_configitems_file():
    """Returns the deserialized TOML structure from the configitems file"""
    with resourceutil.open_resource(b"mercurial", b"configitems.toml") as fp:
        return tomllib.load(fp)


def configitems_from_toml(items):
    """Register the configitems from the *deserialized* toml file"""
    for item in items["items"]:
        sanitize_item(item)
        coreconfigitem(**item)

    templates = items["templates"]

    for application in items["template-applications"]:
        template_items = templates[application["template"]]

        for template_item in template_items:
            item = template_item.copy()
            prefix = application.get("prefix", "")
            item["section"] = application["section"]
            if prefix:
                item["name"] = f'{prefix}.{item["suffix"]}'
            else:
                item["name"] = item["suffix"]

            sanitize_item(item)
            item.pop("suffix", None)
            coreconfigitem(**item)


def import_configitems_from_file():
    as_toml = read_configitems_file()
    configitems_from_toml(as_toml)


coreitems = {}


def _register(configtable, *args, **kwargs):
    item = configitem(*args, **kwargs)
    section = configtable.setdefault(item.section, itemregister())
    if item.name in section:
        msg = b"duplicated config item registration for '%s.%s'"
        raise error.ProgrammingError(msg % (item.section, item.name))
    section[item.name] = item


# special value for case where the default is derived from other values
dynamicdefault = object()

# Registering actual config items


def getitemregister(configtable):
    f = functools.partial(_register, configtable)
    # export pseudo enum as configitem.*
    f.dynamicdefault = dynamicdefault
    return f


coreconfigitem = getitemregister(coreitems)

import_configitems_from_file()
