# peer.py - repository base classes for mercurial
#
# Copyright 2005, 2006 Matt Mackall <mpm@selenic.com>
# Copyright 2006 Vadim Gelfer <vadim.gelfer@gmail.com>
#
# This software may be used and distributed according to the terms of the
# GNU General Public License version 2 or any later version.

from i18n import _
import error

class peerrepository(object):

    def capable(self, name):
        '''tell whether repo supports named capability.
        return False if not supported.
        if boolean capability, return True.
        if string capability, return string.'''
        caps = self._capabilities()
        if name in caps:
            return True
        name_eq = name + '='
        for cap in caps:
            if cap.startswith(name_eq):
                return cap[len(name_eq):]
        return False

    def requirecap(self, name, purpose):
        '''raise an exception if the given capability is not present'''
        if not self.capable(name):
            raise error.CapabilityError(
                _('cannot %s; remote repository does not '
                  'support the %r capability') % (purpose, name))

    def local(self):
        '''return peer as a localrepo, or None'''
        return None

    def peer(self):
        return self

    def canpush(self):
        return True

    def close(self):
        pass
