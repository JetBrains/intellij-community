# __init__.py - Startup and module loading logic for Mercurial.
#
# Copyright 2015 Gregory Szorc <gregory.szorc@gmail.com>
#
# This software may be used and distributed according to the terms of the
# GNU General Public License version 2 or any later version.


# Allow 'from mercurial import demandimport' to keep working.
import hgdemandimport

demandimport = hgdemandimport
