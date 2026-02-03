# util.py - Utilities for declaring interfaces.
#
# Copyright 2018 Gregory Szorc <gregory.szorc@gmail.com>
#
# This software may be used and distributed according to the terms of the
# GNU General Public License version 2 or any later version.

# zope.interface imposes a run-time cost due to module import overhead and
# bookkeeping for declaring interfaces. So, we use stubs for various
# zope.interface primitives unless instructed otherwise.


from .. import encoding

if encoding.environ.get(b'HGREALINTERFACES'):
    from ..thirdparty.zope import interface as zi

    Attribute = zi.Attribute
    Interface = zi.Interface
    implementer = zi.implementer
else:

    class Attribute:
        def __init__(self, __name__, __doc__=b''):
            pass

    class Interface:
        def __init__(
            self, name, bases=(), attrs=None, __doc__=None, __module__=None
        ):
            pass

    def implementer(*ifaces):
        def wrapper(cls):
            return cls

        return wrapper
