# node.py - basic nodeid manipulation for mercurial
#
# Copyright 2005, 2006 Olivia Mackall <olivia@selenic.com>
#
# This software may be used and distributed according to the terms of the
# GNU General Public License version 2 or any later version.


import binascii

# This ugly style has a noticeable effect in manifest parsing
hex = binascii.hexlify
bin = binascii.unhexlify


def short(node):
    return hex(node[:6])


nullrev = -1

# pseudo identifier for working directory
# (experimental, so don't add too many dependencies on it)
wdirrev = 0x7FFFFFFF


class sha1nodeconstants:
    nodelen = 20

    # In hex, this is '0000000000000000000000000000000000000000'
    nullid = b"\0" * nodelen
    nullhex = hex(nullid)

    # Phony node value to stand-in for new files in some uses of
    # manifests.
    # In hex, this is '2121212121212121212121212121212121212121'
    newnodeid = b'!!!!!!!!!!!!!!!!!!!!'
    # In hex, this is '3030303030303030303030303030306164646564'
    addednodeid = b'000000000000000added'
    # In hex, this is '3030303030303030303030306d6f646966696564'
    modifiednodeid = b'000000000000modified'

    wdirfilenodeids = {newnodeid, addednodeid, modifiednodeid}

    # pseudo identifier for working directory
    # (experimental, so don't add too many dependencies on it)
    # In hex, this is 'ffffffffffffffffffffffffffffffffffffffff'
    wdirid = b"\xff" * nodelen
    wdirhex = hex(wdirid)


# legacy starting point for porting modules
nullid = sha1nodeconstants.nullid
nullhex = sha1nodeconstants.nullhex
newnodeid = sha1nodeconstants.newnodeid
addednodeid = sha1nodeconstants.addednodeid
modifiednodeid = sha1nodeconstants.modifiednodeid
wdirfilenodeids = sha1nodeconstants.wdirfilenodeids
wdirid = sha1nodeconstants.wdirid
wdirhex = sha1nodeconstants.wdirhex
