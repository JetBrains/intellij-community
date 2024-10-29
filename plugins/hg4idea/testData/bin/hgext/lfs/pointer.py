# pointer.py - Git-LFS pointer serialization
#
# Copyright 2017 Facebook, Inc.
#
# This software may be used and distributed according to the terms of the
# GNU General Public License version 2 or any later version.


import re

from mercurial.i18n import _

from mercurial import (
    error,
    pycompat,
)
from mercurial.utils import stringutil


class InvalidPointer(error.StorageError):
    pass


class gitlfspointer(dict):
    VERSION = b'https://git-lfs.github.com/spec/v1'

    def __init__(self, *args, **kwargs):
        self[b'version'] = self.VERSION
        super(gitlfspointer, self).__init__(*args)
        self.update(pycompat.byteskwargs(kwargs))

    @classmethod
    def deserialize(cls, text):
        try:
            return cls(l.split(b' ', 1) for l in text.splitlines()).validate()
        except ValueError:  # l.split returns 1 item instead of 2
            raise InvalidPointer(
                _(b'cannot parse git-lfs text: %s') % stringutil.pprint(text)
            )

    def serialize(self):
        sortkeyfunc = lambda x: (x[0] != b'version', x)
        items = sorted(self.validate().items(), key=sortkeyfunc)
        return b''.join(b'%s %s\n' % (k, v) for k, v in items)

    def oid(self):
        return self[b'oid'].split(b':')[-1]

    def size(self):
        return int(self[b'size'])

    # regular expressions used by _validate
    # see https://github.com/git-lfs/git-lfs/blob/master/docs/spec.md
    _keyre = re.compile(br'\A[a-z0-9.-]+\Z')
    _valuere = re.compile(br'\A[^\n]*\Z')
    _requiredre = {
        b'size': re.compile(br'\A[0-9]+\Z'),
        b'oid': re.compile(br'\Asha256:[0-9a-f]{64}\Z'),
        b'version': re.compile(br'\A%s\Z' % stringutil.reescape(VERSION)),
    }

    def validate(self):
        """raise InvalidPointer on error. return self if there is no error"""
        requiredcount = 0
        for k, v in self.items():
            if k in self._requiredre:
                if not self._requiredre[k].match(v):
                    raise InvalidPointer(
                        _(b'unexpected lfs pointer value: %s=%s')
                        % (k, stringutil.pprint(v))
                    )
                requiredcount += 1
            elif not self._keyre.match(k):
                raise InvalidPointer(_(b'unexpected lfs pointer key: %s') % k)
            if not self._valuere.match(v):
                raise InvalidPointer(
                    _(b'unexpected lfs pointer value: %s=%s')
                    % (k, stringutil.pprint(v))
                )
        if len(self._requiredre) != requiredcount:
            miss = sorted(set(self._requiredre.keys()).difference(self.keys()))
            raise InvalidPointer(
                _(b'missing lfs pointer keys: %s') % b', '.join(miss)
            )
        return self


deserialize = gitlfspointer.deserialize
