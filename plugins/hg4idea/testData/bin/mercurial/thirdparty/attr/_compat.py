from __future__ import absolute_import, division, print_function

import sys
import types


PY2 = sys.version_info[0] == 2


if PY2:
    from UserDict import IterableUserDict

    # We 'bundle' isclass instead of using inspect as importing inspect is
    # fairly expensive (order of 10-15 ms for a modern machine in 2016)
    def isclass(klass):
        return isinstance(klass, (type, types.ClassType))

    # TYPE is used in exceptions, repr(int) is different on Python 2 and 3.
    TYPE = "type"

    def iteritems(d):
        return d.iteritems()

    def iterkeys(d):
        return d.iterkeys()

    # Python 2 is bereft of a read-only dict proxy, so we make one!
    class ReadOnlyDict(IterableUserDict):
        """
        Best-effort read-only dict wrapper.
        """

        def __setitem__(self, key, val):
            # We gently pretend we're a Python 3 mappingproxy.
            raise TypeError("'mappingproxy' object does not support item "
                            "assignment")

        def update(self, _):
            # We gently pretend we're a Python 3 mappingproxy.
            raise AttributeError("'mappingproxy' object has no attribute "
                                 "'update'")

        def __delitem__(self, _):
            # We gently pretend we're a Python 3 mappingproxy.
            raise TypeError("'mappingproxy' object does not support item "
                            "deletion")

        def clear(self):
            # We gently pretend we're a Python 3 mappingproxy.
            raise AttributeError("'mappingproxy' object has no attribute "
                                 "'clear'")

        def pop(self, key, default=None):
            # We gently pretend we're a Python 3 mappingproxy.
            raise AttributeError("'mappingproxy' object has no attribute "
                                 "'pop'")

        def popitem(self):
            # We gently pretend we're a Python 3 mappingproxy.
            raise AttributeError("'mappingproxy' object has no attribute "
                                 "'popitem'")

        def setdefault(self, key, default=None):
            # We gently pretend we're a Python 3 mappingproxy.
            raise AttributeError("'mappingproxy' object has no attribute "
                                 "'setdefault'")

        def __repr__(self):
            # Override to be identical to the Python 3 version.
            return "mappingproxy(" + repr(self.data) + ")"

    def metadata_proxy(d):
        res = ReadOnlyDict()
        res.data.update(d)  # We blocked update, so we have to do it like this.
        return res

else:
    def isclass(klass):
        return isinstance(klass, type)

    TYPE = "class"

    def iteritems(d):
        return d.items()

    def iterkeys(d):
        return d.keys()

    def metadata_proxy(d):
        return types.MappingProxyType(dict(d))
