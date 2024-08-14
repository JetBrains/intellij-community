import os
import time


# work around check-code complains
#
# This is a simple log level module doing simple test related work, we can't
# import more things, and we do not need it.
environ = getattr(os, 'environ')


def wait_on_cfg(ui, cfg, timeout=10):
    """synchronize on the `cfg` config path

    Use this to synchronize commands during race tests.
    """
    full_config = b'sync.' + cfg
    wait_config = full_config + b'-timeout'
    sync_path = ui.config(b'devel', full_config)
    if sync_path is not None:
        timeout = ui.config(b'devel', wait_config)
        ready_path = sync_path + b'.waiting'
        write_file(ready_path)
        wait_file(sync_path, timeout=timeout)


def _timeout_factor():
    """return the current modification to timeout"""
    default = int(environ.get('HGTEST_TIMEOUT_DEFAULT', 360))
    current = int(environ.get('HGTEST_TIMEOUT', default))
    if current == 0:
        return 1
    return current / float(default)


def wait_file(path, timeout=10):
    timeout *= _timeout_factor()
    start = time.time()
    while not os.path.exists(path):
        if timeout and time.time() - start > timeout:
            raise RuntimeError(b"timed out waiting for file: %s" % path)
        time.sleep(0.01)


def write_file(path, content=b''):
    if content:
        write_path = b'%s.tmp' % path
    else:
        write_path = path
    with open(write_path, 'wb') as f:
        f.write(content)
    if path != write_path:
        os.rename(write_path, path)
