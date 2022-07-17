from __future__ import absolute_import

import struct

from mercurial.i18n import _

NETWORK_CAP_LEGACY_SSH_GETFILES = b'exp-remotefilelog-ssh-getfiles-1'

SHALLOWREPO_REQUIREMENT = b"exp-remotefilelog-repo-req-1"

BUNDLE2_CAPABLITY = b"exp-remotefilelog-b2cap-1"

FILENAMESTRUCT = b'!H'
FILENAMESIZE = struct.calcsize(FILENAMESTRUCT)

NODESIZE = 20
PACKREQUESTCOUNTSTRUCT = b'!I'

NODECOUNTSTRUCT = b'!I'
NODECOUNTSIZE = struct.calcsize(NODECOUNTSTRUCT)

PATHCOUNTSTRUCT = b'!I'
PATHCOUNTSIZE = struct.calcsize(PATHCOUNTSTRUCT)

FILEPACK_CATEGORY = b""
TREEPACK_CATEGORY = b"manifests"

ALL_CATEGORIES = [FILEPACK_CATEGORY, TREEPACK_CATEGORY]

# revision metadata keys. must be a single character.
METAKEYFLAG = b'f'  # revlog flag
METAKEYSIZE = b's'  # full rawtext size


def getunits(category):
    if category == FILEPACK_CATEGORY:
        return _(b"files")
    if category == TREEPACK_CATEGORY:
        return _(b"trees")


# Repack options passed to ``markledger``.
OPTION_PACKSONLY = b'packsonly'
