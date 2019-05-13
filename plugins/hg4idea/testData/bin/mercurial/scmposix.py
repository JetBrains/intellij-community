import sys, os
import osutil

def _rcfiles(path):
    rcs = [os.path.join(path, 'hgrc')]
    rcdir = os.path.join(path, 'hgrc.d')
    try:
        rcs.extend([os.path.join(rcdir, f)
                    for f, kind in osutil.listdir(rcdir)
                    if f.endswith(".rc")])
    except OSError:
        pass
    return rcs

def systemrcpath():
    path = []
    if sys.platform == 'plan9':
        root = 'lib/mercurial'
    else:
        root = 'etc/mercurial'
    # old mod_python does not set sys.argv
    if len(getattr(sys, 'argv', [])) > 0:
        p = os.path.dirname(os.path.dirname(sys.argv[0]))
        path.extend(_rcfiles(os.path.join(p, root)))
    path.extend(_rcfiles('/' + root))
    return path

def userrcpath():
    if sys.platform == 'plan9':
        return [os.environ['home'] + '/lib/hgrc']
    else:
        return [os.path.expanduser('~/.hgrc')]
