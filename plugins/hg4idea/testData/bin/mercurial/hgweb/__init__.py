# hgweb/__init__.py - web interface to a mercurial repository
#
# Copyright 21 May 2005 - (c) 2005 Jake Edge <jake@edge2.net>
# Copyright 2005 Matt Mackall <mpm@selenic.com>
#
# This software may be used and distributed according to the terms of the
# GNU General Public License version 2 or any later version.

import hgweb_mod, hgwebdir_mod

def hgweb(*args, **kwargs):
    return hgweb_mod.hgweb(*args, **kwargs)

def hgwebdir(*args, **kwargs):
    return hgwebdir_mod.hgwebdir(*args, **kwargs)

