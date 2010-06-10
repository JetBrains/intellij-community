# node.py - basic nodeid manipulation for mercurial
#
# Copyright 2005, 2006 Matt Mackall <mpm@selenic.com>
#
# This software may be used and distributed according to the terms of the
# GNU General Public License version 2 or any later version.

import binascii

nullrev = -1
nullid = "\0" * 20

# This ugly style has a noticeable effect in manifest parsing
hex = binascii.hexlify
bin = binascii.unhexlify

def short(node):
    return hex(node[:6])
