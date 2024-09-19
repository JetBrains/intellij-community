# py3kcompat.py - compatibility definitions for running hg in py3k
#
# Copyright 2010 Renato Cunha <renatoc@gmail.com>
#
# This software may be used and distributed according to the terms of the
# GNU General Public License version 2 or any later version.

import os, builtins

from numbers import Number

def bytesformatter(format, args):
    '''Custom implementation of a formatter for bytestrings.

    This function currently relies on the string formatter to do the
    formatting and always returns bytes objects.

    >>> bytesformatter(20, 10)
    0
    >>> bytesformatter('unicode %s, %s!', ('string', 'foo'))
    b'unicode string, foo!'
    >>> bytesformatter(b'test %s', 'me')
    b'test me'
    >>> bytesformatter('test %s', 'me')
    b'test me'
    >>> bytesformatter(b'test %s', b'me')
    b'test me'
    >>> bytesformatter('test %s', b'me')
    b'test me'
    >>> bytesformatter('test %d: %s', (1, b'result'))
    b'test 1: result'
    '''
    # The current implementation just converts from bytes to unicode, do
    # what's needed and then convert the results back to bytes.
    # Another alternative is to use the Python C API implementation.
    if isinstance(format, Number):
        # If the fixer erroneously passes a number remainder operation to
        # bytesformatter, we just return the correct operation
        return format % args
    if isinstance(format, bytes):
        format = format.decode('utf-8', 'surrogateescape')
    if isinstance(args, bytes):
        args = args.decode('utf-8', 'surrogateescape')
    if isinstance(args, tuple):
        newargs = []
        for arg in args:
            if isinstance(arg, bytes):
                arg = arg.decode('utf-8', 'surrogateescape')
            newargs.append(arg)
        args = tuple(newargs)
    ret = format % args
    return ret.encode('utf-8', 'surrogateescape')
builtins.bytesformatter = bytesformatter

# Create bytes equivalents for os.environ values
for key in list(os.environ.keys()):
    # UTF-8 is fine for us
    bkey = key.encode('utf-8', 'surrogateescape')
    bvalue = os.environ[key].encode('utf-8', 'surrogateescape')
    os.environ[bkey] = bvalue

origord = builtins.ord
def fakeord(char):
    if isinstance(char, int):
        return char
    return origord(char)
builtins.ord = fakeord

if __name__ == '__main__':
    import doctest
    doctest.testmod()

