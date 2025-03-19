"""strip changesets and their descendants from history (DEPRECATED)

The functionality of this extension has been included in core Mercurial
since version 5.7. Please use :hg:`debugstrip ...` instead.

This extension allows you to strip changesets and all their descendants from the
repository. See the command help for details.
"""

from mercurial import commands

# Note for extension authors: ONLY specify testedwith = 'ships-with-hg-core' for
# extensions which SHIP WITH MERCURIAL. Non-mainline extensions should
# be specifying the version(s) of Mercurial they are tested with, or
# leave the attribute unspecified.
testedwith = b'ships-with-hg-core'

# This is a bit ugly, but a uisetup function that defines strip as an
# alias for debugstrip would override any user alias for strip,
# including aliases like "strip = strip --no-backup".
commands.command.rename(old=b'debugstrip', new=b'debugstrip|strip')
