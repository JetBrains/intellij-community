# Copyright 2005, 2006 Benoit Boissinot <benoit.boissinot@ens-lyon.org>
#
# This software may be used and distributed according to the terms of the
# GNU General Public License version 2 or any later version.

'''commands to sign and verify changesets'''


import binascii
import os

from mercurial.i18n import _
from mercurial.node import (
    bin,
    hex,
    short,
)
from mercurial import (
    cmdutil,
    error,
    help,
    match,
    pycompat,
    registrar,
)
from mercurial.utils import (
    dateutil,
    procutil,
)

cmdtable = {}
command = registrar.command(cmdtable)
# Note for extension authors: ONLY specify testedwith = 'ships-with-hg-core' for
# extensions which SHIP WITH MERCURIAL. Non-mainline extensions should
# be specifying the version(s) of Mercurial they are tested with, or
# leave the attribute unspecified.
testedwith = b'ships-with-hg-core'

configtable = {}
configitem = registrar.configitem(configtable)

configitem(
    b'gpg',
    b'cmd',
    default=b'gpg',
)
configitem(
    b'gpg',
    b'key',
    default=None,
)
configitem(
    b'gpg',
    b'.*',
    default=None,
    generic=True,
)

# Custom help category
_HELP_CATEGORY = b'gpg'
help.CATEGORY_ORDER.insert(
    help.CATEGORY_ORDER.index(registrar.command.CATEGORY_HELP), _HELP_CATEGORY
)
help.CATEGORY_NAMES[_HELP_CATEGORY] = b'Signing changes (GPG)'


class gpg:
    def __init__(self, path, key=None):
        self.path = path
        self.key = (key and b" --local-user \"%s\"" % key) or b""

    def sign(self, data):
        gpgcmd = b"%s --sign --detach-sign%s" % (self.path, self.key)
        return procutil.filter(data, gpgcmd)

    def verify(self, data, sig):
        """returns of the good and bad signatures"""
        sigfile = datafile = None
        try:
            # create temporary files
            fd, sigfile = pycompat.mkstemp(prefix=b"hg-gpg-", suffix=b".sig")
            fp = os.fdopen(fd, 'wb')
            fp.write(sig)
            fp.close()
            fd, datafile = pycompat.mkstemp(prefix=b"hg-gpg-", suffix=b".txt")
            fp = os.fdopen(fd, 'wb')
            fp.write(data)
            fp.close()
            gpgcmd = (
                b"%s --logger-fd 1 --status-fd 1 --verify \"%s\" \"%s\""
                % (
                    self.path,
                    sigfile,
                    datafile,
                )
            )
            ret = procutil.filter(b"", gpgcmd)
        finally:
            for f in (sigfile, datafile):
                try:
                    if f:
                        os.unlink(f)
                except OSError:
                    pass
        keys = []
        key, fingerprint = None, None
        for l in ret.splitlines():
            # see DETAILS in the gnupg documentation
            # filter the logger output
            if not l.startswith(b"[GNUPG:]"):
                continue
            l = l[9:]
            if l.startswith(b"VALIDSIG"):
                # fingerprint of the primary key
                fingerprint = l.split()[10]
            elif l.startswith(b"ERRSIG"):
                key = l.split(b" ", 3)[:2]
                key.append(b"")
                fingerprint = None
            elif (
                l.startswith(b"GOODSIG")
                or l.startswith(b"EXPSIG")
                or l.startswith(b"EXPKEYSIG")
                or l.startswith(b"BADSIG")
            ):
                if key is not None:
                    keys.append(key + [fingerprint])
                key = l.split(b" ", 2)
                fingerprint = None
        if key is not None:
            keys.append(key + [fingerprint])
        return keys


def newgpg(ui, **opts):
    """create a new gpg instance"""
    gpgpath = ui.config(b"gpg", b"cmd")
    gpgkey = opts.get('key')
    if not gpgkey:
        gpgkey = ui.config(b"gpg", b"key")
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
            yield (l.split(b" ", 2), (context, ln))
            ln += 1

    # read the heads
    fl = repo.file(b".hgsigs")
    for r in reversed(fl.heads()):
        fn = b".hgsigs|%s" % short(r)
        for item in parsefile(fl.read(r).splitlines(), fn):
            yield item
    try:
        # read local signatures
        fn = b"localsigs"
        for item in parsefile(repo.vfs(fn), fn):
            yield item
    except IOError:
        pass


def getkeys(ui, repo, mygpg, sigdata, context):
    """get the keys who signed a data"""
    fn, ln = context
    node, version, sig = sigdata
    prefix = b"%s:%d" % (fn, ln)
    node = bin(node)

    data = node2txt(repo, node, version)
    sig = binascii.a2b_base64(sig)
    keys = mygpg.verify(data, sig)

    validkeys = []
    # warn for expired key and/or sigs
    for key in keys:
        if key[0] == b"ERRSIG":
            ui.write(_(b"%s Unknown key ID \"%s\"\n") % (prefix, key[1]))
            continue
        if key[0] == b"BADSIG":
            ui.write(_(b"%s Bad signature from \"%s\"\n") % (prefix, key[2]))
            continue
        if key[0] == b"EXPSIG":
            ui.write(
                _(b"%s Note: Signature has expired (signed by: \"%s\")\n")
                % (prefix, key[2])
            )
        elif key[0] == b"EXPKEYSIG":
            ui.write(
                _(b"%s Note: This key has expired (signed by: \"%s\")\n")
                % (prefix, key[2])
            )
        validkeys.append((key[1], key[2], key[3]))
    return validkeys


@command(b"sigs", [], _(b'hg sigs'), helpcategory=_HELP_CATEGORY)
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
            ui.warn(_(b"%s:%d node does not exist\n") % (fn, ln))
            continue
        r = repo.changelog.rev(n)
        keys = getkeys(ui, repo, mygpg, data, context)
        if not keys:
            continue
        revs.setdefault(r, [])
        revs[r].extend(keys)
    for rev in sorted(revs, reverse=True):
        for k in revs[rev]:
            r = b"%5d:%s" % (rev, hex(repo.changelog.node(rev)))
            ui.write(b"%-30s %s\n" % (keystr(ui, k), r))


@command(b"sigcheck", [], _(b'hg sigcheck REV'), helpcategory=_HELP_CATEGORY)
def sigcheck(ui, repo, rev):
    """verify all the signatures there may be for a particular revision"""
    mygpg = newgpg(ui)
    rev = repo.lookup(rev)
    hexrev = hex(rev)
    keys = []

    for data, context in sigwalk(repo):
        node, version, sig = data
        if node == hexrev:
            k = getkeys(ui, repo, mygpg, data, context)
            if k:
                keys.extend(k)

    if not keys:
        ui.write(_(b"no valid signature for %s\n") % short(rev))
        return

    # print summary
    ui.write(_(b"%s is signed by:\n") % short(rev))
    for key in keys:
        ui.write(b" %s\n" % keystr(ui, key))


def keystr(ui, key):
    """associate a string to a key (username, comment)"""
    keyid, user, fingerprint = key
    comment = ui.config(b"gpg", fingerprint)
    if comment:
        return b"%s (%s)" % (user, comment)
    else:
        return user


@command(
    b"sign",
    [
        (b'l', b'local', None, _(b'make the signature local')),
        (b'f', b'force', None, _(b'sign even if the sigfile is modified')),
        (
            b'',
            b'no-commit',
            None,
            _(b'do not commit the sigfile after signing'),
        ),
        (b'k', b'key', b'', _(b'the key id to sign with'), _(b'ID')),
        (b'm', b'message', b'', _(b'use text as commit message'), _(b'TEXT')),
        (b'e', b'edit', False, _(b'invoke editor on commit messages')),
    ]
    + cmdutil.commitopts2,
    _(b'hg sign [OPTION]... [REV]...'),
    helpcategory=_HELP_CATEGORY,
)
def sign(ui, repo, *revs, **opts):
    """add a signature for the current or given revision

    If no revision is given, the parent of the working directory is used,
    or tip if no revision is checked out.

    The ``gpg.cmd`` config setting can be used to specify the command
    to run. A default key can be specified with ``gpg.key``.

    See :hg:`help dates` for a list of formats valid for -d/--date.
    """
    with repo.wlock():
        return _dosign(ui, repo, *revs, **opts)


def _dosign(ui, repo, *revs, **opts):
    mygpg = newgpg(ui, **opts)

    sigver = b"0"
    sigmessage = b""

    date = opts.get('date')
    if date:
        opts['date'] = dateutil.parsedate(date)

    if revs:
        nodes = [repo.lookup(n) for n in revs]
    else:
        nodes = [
            node for node in repo.dirstate.parents() if node != repo.nullid
        ]
        if len(nodes) > 1:
            raise error.Abort(
                _(b'uncommitted merge - please provide a specific revision')
            )
        if not nodes:
            nodes = [repo.changelog.tip()]

    for n in nodes:
        hexnode = hex(n)
        ui.write(_(b"signing %d:%s\n") % (repo.changelog.rev(n), short(n)))
        # build data
        data = node2txt(repo, n, sigver)
        sig = mygpg.sign(data)
        if not sig:
            raise error.Abort(_(b"error while signing"))
        sig = binascii.b2a_base64(sig)
        sig = sig.replace(b"\n", b"")
        sigmessage += b"%s %s %s\n" % (hexnode, sigver, sig)

    # write it
    if opts['local']:
        repo.vfs.append(b"localsigs", sigmessage)
        return

    msigs = match.exact([b'.hgsigs'])

    if not opts["force"]:
        if any(repo.status(match=msigs, unknown=True, ignored=True)):
            raise error.Abort(
                _(b"working copy of .hgsigs is changed "),
                hint=_(b"please commit .hgsigs manually"),
            )

    with repo.wvfs(b".hgsigs", b"ab") as sigsfile:
        sigsfile.write(sigmessage)

    if b'.hgsigs' not in repo.dirstate:
        with repo.dirstate.changing_files(repo):
            repo[None].add([b".hgsigs"])

    if opts["no_commit"]:
        return

    message = opts['message']
    if not message:
        # we don't translate commit messages
        message = b"\n".join(
            [b"Added signature for changeset %s" % short(n) for n in nodes]
        )
    try:
        editor = cmdutil.getcommiteditor(editform=b'gpg.sign', **opts)
        repo.commit(
            message, opts['user'], opts['date'], match=msigs, editor=editor
        )
    except ValueError as inst:
        raise error.Abort(pycompat.bytestr(inst))


def node2txt(repo, node, ver):
    """map a manifest into some text"""
    if ver == b"0":
        return b"%s\n" % hex(node)
    else:
        raise error.Abort(_(b"unknown signature version"))


def extsetup(ui):
    # Add our category before "Repository maintenance".
    help.CATEGORY_ORDER.insert(
        help.CATEGORY_ORDER.index(command.CATEGORY_MAINTENANCE), _HELP_CATEGORY
    )
    help.CATEGORY_NAMES[_HELP_CATEGORY] = b'GPG signing'
