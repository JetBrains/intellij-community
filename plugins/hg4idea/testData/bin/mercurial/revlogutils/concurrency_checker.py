from ..i18n import _
from .. import error


def get_checker(ui, revlog_name=b'changelog'):
    """Get a function that checks file handle position is as expected.

    This is used to ensure that files haven't been modified outside of our
    knowledge (such as on a networked filesystem, if `hg debuglocks` was used,
    or writes to .hg that ignored locks happened).

    Due to revlogs supporting a concept of buffered, delayed, or diverted
    writes, we're allowing the files to be shorter than expected (the data may
    not have been written yet), but they can't be longer.

    Please note that this check is not perfect; it can't detect all cases (there
    may be false-negatives/false-OKs), but it should never claim there's an
    issue when there isn't (false-positives/false-failures).
    """

    vpos = ui.config(b'debug', b'revlog.verifyposition.' + revlog_name)
    # Avoid any `fh.tell` cost if this isn't enabled.
    if not vpos or vpos not in [b'log', b'warn', b'fail']:
        return None

    def _checker(fh, fn, expected):
        if fh.tell() <= expected:
            return

        msg = _(b'%s: file cursor at position %d, expected %d')
        # Always log if we're going to warn or fail.
        ui.log(b'debug', msg + b'\n', fn, fh.tell(), expected)
        if vpos == b'warn':
            ui.warn((msg + b'\n') % (fn, fh.tell(), expected))
        elif vpos == b'fail':
            raise error.RevlogError(msg % (fn, fh.tell(), expected))

    return _checker
