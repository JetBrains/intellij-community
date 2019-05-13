import os
import osutil
import util
import _winreg

def systemrcpath():
    '''return default os-specific hgrc search path'''
    rcpath = []
    filename = util.executablepath()
    # Use mercurial.ini found in directory with hg.exe
    progrc = os.path.join(os.path.dirname(filename), 'mercurial.ini')
    if os.path.isfile(progrc):
        rcpath.append(progrc)
        return rcpath
    # Use hgrc.d found in directory with hg.exe
    progrcd = os.path.join(os.path.dirname(filename), 'hgrc.d')
    if os.path.isdir(progrcd):
        for f, kind in osutil.listdir(progrcd):
            if f.endswith('.rc'):
                rcpath.append(os.path.join(progrcd, f))
        return rcpath
    # else look for a system rcpath in the registry
    value = util.lookupreg('SOFTWARE\\Mercurial', None,
                           _winreg.HKEY_LOCAL_MACHINE)
    if not isinstance(value, str) or not value:
        return rcpath
    value = util.localpath(value)
    for p in value.split(os.pathsep):
        if p.lower().endswith('mercurial.ini'):
            rcpath.append(p)
        elif os.path.isdir(p):
            for f, kind in osutil.listdir(p):
                if f.endswith('.rc'):
                    rcpath.append(os.path.join(p, f))
    return rcpath

def userrcpath():
    '''return os-specific hgrc search path to the user dir'''
    home = os.path.expanduser('~')
    path = [os.path.join(home, 'mercurial.ini'),
            os.path.join(home, '.hgrc')]
    userprofile = os.environ.get('USERPROFILE')
    if userprofile:
        path.append(os.path.join(userprofile, 'mercurial.ini'))
        path.append(os.path.join(userprofile, '.hgrc'))
    return path
