# This software may be used and distributed according to the terms of the
# GNU General Public License version 2 or any later version.


import re

from mercurial.i18n import _
from mercurial import (
    error,
    hg,
    util,
)
from mercurial.utils import (
    urlutil,
)

from . import (
    lfutil,
    localstore,
    wirestore,
)


# During clone this function is passed the src's ui object
# but it needs the dest's ui object so it can read out of
# the config file. Use repo.ui instead.
def openstore(repo=None, remote=None, put=False, ui=None):
    if ui is None:
        ui = repo.ui

    if not remote:
        lfpullsource = getattr(repo, 'lfpullsource', None)
        if put:
            path = urlutil.get_unique_push_path(
                b'lfpullsource', repo, ui, lfpullsource
            )
        else:
            path = urlutil.get_unique_pull_path_obj(
                b'lfpullsource', ui, lfpullsource
            )

        # XXX we should not explicitly pass b'default', as this will result in
        # b'default' being returned if no `paths.default` was defined. We
        # should explicitely handle the lack of value instead.
        if repo is None:
            path = urlutil.get_unique_pull_path_obj(
                b'lfs',
                ui,
                b'default',
            )
            remote = hg.peer(repo or ui, {}, path)
        elif path.loc == b'default-push' or path.loc == b'default':
            remote = repo
        else:
            remote = hg.peer(repo or ui, {}, path)

    # The path could be a scheme so use Mercurial's normal functionality
    # to resolve the scheme to a repository and use its path
    path = hasattr(remote, 'url') and remote.url() or remote.path

    match = _scheme_re.match(path)
    if not match:  # regular filesystem path
        scheme = b'file'
    else:
        scheme = match.group(1)

    try:
        storeproviders = _storeprovider[scheme]
    except KeyError:
        raise error.Abort(_(b'unsupported URL scheme %r') % scheme)

    for classobj in storeproviders:
        try:
            return classobj(ui, repo, remote)
        except lfutil.storeprotonotcapable:
            pass

    raise error.Abort(
        _(b'%s does not appear to be a largefile store')
        % urlutil.hidepassword(path)
    )


_storeprovider = {
    b'file': [localstore.localstore],
    b'http': [wirestore.wirestore],
    b'https': [wirestore.wirestore],
    b'ssh': [wirestore.wirestore],
}

_scheme_re = re.compile(br'^([a-zA-Z0-9+-.]+)://')


def getlfile(ui, hash):
    return util.chunkbuffer(openstore(ui=ui)._get(hash))
