# txnutil.py - transaction related utilities
#
#  Copyright FUJIWARA Katsunori <foozy@lares.dti.ne.jp> and others
#
# This software may be used and distributed according to the terms of the
# GNU General Public License version 2 or any later version.


from . import encoding


def mayhavepending(root):
    """return whether 'root' may have pending changes, which are
    visible to this process.
    """
    return root == encoding.environ.get(b'HG_PENDING')


def trypending(root, vfs, filename, **kwargs):
    """Open  file to be read according to HG_PENDING environment variable

    This opens '.pending' of specified 'filename' only when HG_PENDING
    is equal to 'root'.

    This returns '(fp, is_pending_opened)' tuple.
    """
    if mayhavepending(root):
        try:
            return (vfs(b'%s.pending' % filename, **kwargs), True)
        except FileNotFoundError:
            pass
    return (vfs(filename, **kwargs), False)
