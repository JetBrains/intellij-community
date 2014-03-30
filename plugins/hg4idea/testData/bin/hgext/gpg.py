# Copyright 2005, 2006 Benoit Boissinot <benoit.boissinot@ens-lyon.org>
#
# This software may be used and distributed according to the terms of the
# GNU General Public License version 2 or any later version.

'''commands to sign and verify changesets'''

import os, tempfile, binascii
from mercurial import util, commands, match, cmdutil
from mercurial import node as hgnode
from mercurial.i18n import _

cmdtable = {}
command = cmdutil.command(cmdtable)
testedwith = 'internal'

class gpg(object):
    def __init__(self, path, key=None):
        self.path = path
        self.key = (key and " --local-user \"%s\"" % key) or ""

    def sign(self, data):
        gpgcmd = "%s --sign --detach-sign%s" % (self.path, self.key)
        return util.filter(data, gpgcmd)

    def verify(self, data, sig):
        """ returns of the good and bad signatures"""
        sigfile = datafile = None
        try:
            # create temporary files
            fd, sigfile = tempfile.mkstemp(prefix="hg-gpg-", suffix=".sig")
            fp = os.fdopen(fd, 'wb')
            fp.write(sig)
            fp.close()
            fd, datafile = tempfile.mkstemp(prefix="hg-gpg-", suffix=".txt")
            fp = os.fdopen(fd, 'wb')
            fp.write(data)
            fp.close()
            gpgcmd = ("%s --logger-fd 1 --status-fd 1 --verify "
                      "\"%s\" \"%s\"" % (self.path, sigfile, datafile))
            ret = util.filter("", gpgcmd)
        finally:
            for f in (sigfile, datafile):
                try:
                    if f:
                        os.unlink(f)
                except OSError:
                    pass
        keys = []
        key, fingerprint = None, None
        err = ""
        for l in ret.splitlines():
            # see DETAILS in the gnupg documentation
            # filter the logger output
            if not l.startswith("[GNUPG:]"):
                continue
            l = l[9:]
            if l.startswith("ERRSIG"):
                err = _("error while verifying signature")
                break
            elif l.startswith("VALIDSIG"):
                # fingerprint of the primary key
                fingerprint = l.split()[10]
            elif (l.startswith("GOODSIG") or
                  l.startswith("EXPSIG") or
                  l.startswith("EXPKEYSIG") or
                  l.startswith("BADSIG")):
                if key is not None:
                    keys.append(key + [fingerprint])
                key = l.split(" ", 2)
                fingerprint = None
        if err:
            return err, []
        if key is not None:
            keys.append(key + [fingerprint])
        return err, keys

def newgpg(ui, **opts):
    """create a new gpg instance"""
    gpgpath = ui.config("gpg", "cmd", "gpg")
    gpgkey = opts.get('key')
    if not gpgkey:
        gpgkey = ui.config("gpg", "key", None)
    return gpg(gpgpath, gpgkey)

def sigwalk(repo):
    """
    walk over every sigs, yields a couple
    ((node, version, sig), (filename, linenumber))
    """
    def parsefile(fileiter, context):
        ln = 1
        for l in fileiter:
            if not l:
                continue
            yield (l.split(" ", 2), (context, ln))
            ln += 1

    # read the heads
    fl = repo.file(".hgsigs")
    for r in reversed(fl.heads()):
        fn = ".hgsigs|%s" % hgnode.short(r)
        for item in parsefile(fl.read(r).splitlines(), fn):
            yield item
    try:
        # read local signatures
        fn = "localsigs"
        for item in parsefile(repo.opener(fn), fn):
            yield item
    except IOError:
        pass

def getkeys(ui, repo, mygpg, sigdata, context):
    """get the keys who signed a data"""
    fn, ln = context
    node, version, sig = sigdata
    prefix = "%s:%d" % (fn, ln)
    node = hgnode.bin(node)

    data = node2txt(repo, node, version)
    sig = binascii.a2b_base64(sig)
    err, keys = mygpg.verify(data, sig)
    if err:
        ui.warn("%s:%d %s\n" % (fn, ln , err))
        return None

    validkeys = []
    # warn for expired key and/or sigs
    for key in keys:
        if key[0] == "BADSIG":
            ui.write(_("%s Bad signature from \"%s\"\n") % (prefix, key[2]))
            continue
        if key[0] == "EXPSIG":
            ui.write(_("%s Note: Signature has expired"
                       " (signed by: \"%s\")\n") % (prefix, key[2]))
        elif key[0] == "EXPKEYSIG":
            ui.write(_("%s Note: This key has expired"
                       " (signed by: \"%s\")\n") % (prefix, key[2]))
        validkeys.append((key[1], key[2], key[3]))
    return validkeys

@command("sigs", [], _('hg sigs'))
def sigs(ui, repo):
    """list signed changesets"""
    mygpg = newgpg(ui)
    revs = {}

    for data, context in sigwalk(repo):
        node, version, sig = data
        fn, ln = context
        try:
            n = repo.lookup(node)
        except KeyError:
            ui.warn(_("%s:%d node does not exist\n") % (fn, ln))
            continue
        r = repo.changelog.rev(n)
        keys = getkeys(ui, repo, mygpg, data, context)
        if not keys:
            continue
        revs.setdefault(r, [])
        revs[r].extend(keys)
    for rev in sorted(revs, reverse=True):
        for k in revs[rev]:
            r = "%5d:%s" % (rev, hgnode.hex(repo.changelog.node(rev)))
            ui.write("%-30s %s\n" % (keystr(ui, k), r))

@command("sigcheck", [], _('hg sigcheck REV'))
def check(ui, repo, rev):
    """verify all the signatures there may be for a particular revision"""
    mygpg = newgpg(ui)
    rev = repo.lookup(rev)
    hexrev = hgnode.hex(rev)
    keys = []

    for data, context in sigwalk(repo):
        node, version, sig = data
        if node == hexrev:
            k = getkeys(ui, repo, mygpg, data, context)
            if k:
                keys.extend(k)

    if not keys:
        ui.write(_("no valid signature for %s\n") % hgnode.short(rev))
        return

    # print summary
    ui.write("%s is signed by:\n" % hgnode.short(rev))
    for key in keys:
        ui.write(" %s\n" % keystr(ui, key))

def keystr(ui, key):
    """associate a string to a key (username, comment)"""
    keyid, user, fingerprint = key
    comment = ui.config("gpg", fingerprint, None)
    if comment:
        return "%s (%s)" % (user, comment)
    else:
        return user

@command("sign",
         [('l', 'local', None, _('make the signature local')),
          ('f', 'force', None, _('sign even if the sigfile is modified')),
          ('', 'no-commit', None, _('do not commit the sigfile after signing')),
          ('k', 'key', '',
           _('the key id to sign with'), _('ID')),
          ('m', 'message', '',
           _('commit message'), _('TEXT')),
         ] + commands.commitopts2,
         _('hg sign [OPTION]... [REV]...'))
def sign(ui, repo, *revs, **opts):
    """add a signature for the current or given revision

    If no revision is given, the parent of the working directory is used,
    or tip if no revision is checked out.

    See :hg:`help dates` for a list of formats valid for -d/--date.
    """

    mygpg = newgpg(ui, **opts)
    sigver = "0"
    sigmessage = ""

    date = opts.get('date')
    if date:
        opts['date'] = util.parsedate(date)

    if revs:
        nodes = [repo.lookup(n) for n in revs]
    else:
        nodes = [node for node in repo.dirstate.parents()
                 if node != hgnode.nullid]
        if len(nodes) > 1:
            raise util.Abort(_('uncommitted merge - please provide a '
                               'specific revision'))
        if not nodes:
            nodes = [repo.changelog.tip()]

    for n in nodes:
        hexnode = hgnode.hex(n)
        ui.write(_("signing %d:%s\n") % (repo.changelog.rev(n),
                                         hgnode.short(n)))
        # build data
        data = node2txt(repo, n, sigver)
        sig = mygpg.sign(data)
        if not sig:
            raise util.Abort(_("error while signing"))
        sig = binascii.b2a_base64(sig)
        sig = sig.replace("\n", "")
        sigmessage += "%s %s %s\n" % (hexnode, sigver, sig)

    # write it
    if opts['local']:
        repo.opener.append("localsigs", sigmessage)
        return

    msigs = match.exact(repo.root, '', ['.hgsigs'])
    s = repo.status(match=msigs, unknown=True, ignored=True)[:6]
    if util.any(s) and not opts["force"]:
        raise util.Abort(_("working copy of .hgsigs is changed "
                           "(please commit .hgsigs manually "
                           "or use --force)"))

    sigsfile = repo.wfile(".hgsigs", "ab")
    sigsfile.write(sigmessage)
    sigsfile.close()

    if '.hgsigs' not in repo.dirstate:
        repo[None].add([".hgsigs"])

    if opts["no_commit"]:
        return

    message = opts['message']
    if not message:
        # we don't translate commit messages
        message = "\n".join(["Added signature for changeset %s"
                             % hgnode.short(n)
                             for n in nodes])
    try:
        repo.commit(message, opts['user'], opts['date'], match=msigs)
    except ValueError, inst:
        raise util.Abort(str(inst))

def node2txt(repo, node, ver):
    """map a manifest into some text"""
    if ver == "0":
        return "%s\n" % hgnode.hex(node)
    else:
        raise util.Abort(_("unknown signature version"))
