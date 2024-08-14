# sidedata.py - Logic around store extra data alongside revlog revisions
#
# Copyright 2019 Pierre-Yves David <pierre-yves.david@octobus.net)
#
# This software may be used and distributed according to the terms of the
# GNU General Public License version 2 or any later version.
"""core code for "sidedata" support

The "sidedata" are stored alongside the revision without actually being part of
its content and not affecting its hash. It's main use cases is to cache
important information related to a changesets.

The current implementation is experimental and subject to changes. Do not rely
on it in production.

Sidedata are stored in the revlog itself, thanks to a new version of the
revlog. The following format is currently used::

    initial header:
        <number of sidedata; 2 bytes>
    sidedata (repeated N times):
        <sidedata-key; 2 bytes>
        <sidedata-entry-length: 4 bytes>
        <sidedata-content-sha1-digest: 20 bytes>
        <sidedata-content; X bytes>
    normal raw text:
        <all bytes remaining in the rawtext>

This is a simple and effective format. It should be enough to experiment with
the concept.
"""


import collections
import struct

from .. import error, requirements as requirementsmod
from ..revlogutils import constants, flagutil
from ..utils import hashutil

## sidedata type constant
# reserve a block for testing purposes.
SD_TEST1 = 1
SD_TEST2 = 2
SD_TEST3 = 3
SD_TEST4 = 4
SD_TEST5 = 5
SD_TEST6 = 6
SD_TEST7 = 7

# key to store copies related information
SD_P1COPIES = 8
SD_P2COPIES = 9
SD_FILESADDED = 10
SD_FILESREMOVED = 11
SD_FILES = 12

# internal format constant
SIDEDATA_HEADER = struct.Struct('>H')
SIDEDATA_ENTRY = struct.Struct('>HL20s')


def serialize_sidedata(sidedata):
    sidedata = list(sidedata.items())
    sidedata.sort()
    buf = [SIDEDATA_HEADER.pack(len(sidedata))]
    for key, value in sidedata:
        digest = hashutil.sha1(value).digest()
        buf.append(SIDEDATA_ENTRY.pack(key, len(value), digest))
    for key, value in sidedata:
        buf.append(value)
    buf = b''.join(buf)
    return buf


def deserialize_sidedata(blob):
    sidedata = {}
    offset = 0
    (nbentry,) = SIDEDATA_HEADER.unpack(blob[: SIDEDATA_HEADER.size])
    offset += SIDEDATA_HEADER.size
    dataoffset = SIDEDATA_HEADER.size + (SIDEDATA_ENTRY.size * nbentry)
    for i in range(nbentry):
        nextoffset = offset + SIDEDATA_ENTRY.size
        key, size, storeddigest = SIDEDATA_ENTRY.unpack(blob[offset:nextoffset])
        offset = nextoffset
        # read the data associated with that entry
        nextdataoffset = dataoffset + size
        entrytext = bytes(blob[dataoffset:nextdataoffset])
        readdigest = hashutil.sha1(entrytext).digest()
        if storeddigest != readdigest:
            raise error.SidedataHashError(key, storeddigest, readdigest)
        sidedata[key] = entrytext
        dataoffset = nextdataoffset
    return sidedata


def get_sidedata_helpers(repo, remote_sd_categories, pull=False):
    """
    Returns a dictionary mapping revlog types to tuples of
    `(repo, computers, removers)`:
        * `repo` is used as an argument for computers
        * `computers` is a list of `(category, (keys, computer, flags)` that
           compute the missing sidedata categories that were asked:
           * `category` is the sidedata category
           * `keys` are the sidedata keys to be affected
           * `flags` is a bitmask (an integer) of flags to remove when
              removing the category.
           * `computer` is the function `(repo, store, rev, sidedata)` that
             returns a tuple of
             `(new sidedata dict, (flags to add, flags to remove))`.
             For example, it will return `({}, (0, 1 << 15))` to return no
             sidedata, with no flags to add and one flag to remove.
        * `removers` will remove the keys corresponding to the categories
          that are present, but not needed.
        If both `computers` and `removers` are empty, sidedata will simply not
        be transformed.
    """
    # Computers for computing sidedata on-the-fly
    sd_computers = collections.defaultdict(list)
    # Computers for categories to remove from sidedata
    sd_removers = collections.defaultdict(list)
    to_generate = remote_sd_categories - repo._wanted_sidedata
    to_remove = repo._wanted_sidedata - remote_sd_categories
    if pull:
        to_generate, to_remove = to_remove, to_generate

    for revlog_kind, computers in repo._sidedata_computers.items():
        for category, computer in computers.items():
            if category in to_generate:
                sd_computers[revlog_kind].append(computer)
            if category in to_remove:
                sd_removers[revlog_kind].append(computer)

    sidedata_helpers = (repo, sd_computers, sd_removers)
    return sidedata_helpers


def run_sidedata_helpers(store, sidedata_helpers, sidedata, rev):
    """Returns the sidedata for the given revision after running through
    the given helpers.
    - `store`: the revlog this applies to (changelog, manifest, or filelog
      instance)
    - `sidedata_helpers`: see `get_sidedata_helpers`
    - `sidedata`: previous sidedata at the given rev, if any
    - `rev`: affected rev of `store`
    """
    repo, sd_computers, sd_removers = sidedata_helpers
    kind = store.revlog_kind
    flags_to_add = 0
    flags_to_remove = 0
    for _keys, sd_computer, _flags in sd_computers.get(kind, []):
        sidedata, flags = sd_computer(repo, store, rev, sidedata)
        flags_to_add |= flags[0]
        flags_to_remove |= flags[1]
    for keys, _computer, flags in sd_removers.get(kind, []):
        for key in keys:
            sidedata.pop(key, None)
        flags_to_remove |= flags
    return sidedata, (flags_to_add, flags_to_remove)


def set_sidedata_spec_for_repo(repo):
    # prevent cycle metadata -> revlogutils.sidedata -> metadata
    from .. import metadata

    if requirementsmod.COPIESSDC_REQUIREMENT in repo.requirements:
        repo.register_wanted_sidedata(SD_FILES)
    repo.register_sidedata_computer(
        constants.KIND_CHANGELOG,
        SD_FILES,
        (SD_FILES,),
        metadata.copies_sidedata_computer,
        flagutil.REVIDX_HASCOPIESINFO,
    )
