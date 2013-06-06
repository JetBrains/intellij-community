# hgweb/__init__.py - web interface to a mercurial repository
#
# Copyright 21 May 2005 - (c) 2005 Jake Edge <jake@edge2.net>
# Copyright 2005 Matt Mackall <mpm@selenic.com>
#
# This software may be used and distributed according to the terms of the
# GNU General Public License version 2 or any later version.

import os
import hgweb_mod, hgwebdir_mod

def hgweb(config, name=None, baseui=None):
    '''create an hgweb wsgi object

    config can be one of:
    - repo object (single repo view)
    - path to repo (single repo view)
    - path to config file (multi-repo view)
    - dict of virtual:real pairs (multi-repo view)
    - list of virtual:real tuples (multi-repo view)
    '''

    if ((isinstance(config, str) and not os.path.isdir(config)) or
        isinstance(config, dict) or isinstance(config, list)):
        # create a multi-dir interface
        return hgwebdir_mod.hgwebdir(config, baseui=baseui)
    return hgweb_mod.hgweb(config, name=name, baseui=baseui)

def hgwebdir(config, baseui=None):
    return hgwebdir_mod.hgwebdir(config, baseui=baseui)

