# verify.py - repository integrity checking for Mercurial
#
# Copyright 2006, 2007 Matt Mackall <mpm@selenic.com>
#
# This software may be used and distributed according to the terms of the
# GNU General Public License version 2 or any later version.

from node import nullid, short
from i18n import _
import os
import revlog, util, error

def verify(repo):
    lock = repo.lock()
    try:
        return _verify(repo)
    finally:
        lock.release()

def _normpath(f):
    # under hg < 2.4, convert didn't sanitize paths properly, so a
    # converted repo may contain repeated slashes
    while '//' in f:
        f = f.replace('//', '/')
    return f

def _verify(repo):
    repo = repo.unfiltered()
    mflinkrevs = {}
    filelinkrevs = {}
    filenodes = {}
    revisions = 0
    badrevs = set()
    errors = [0]
    warnings = [0]
    ui = repo.ui
    cl = repo.changelog
    mf = repo.manifest
    lrugetctx = util.lrucachefunc(repo.changectx)

    if not repo.cancopy():
        raise util.Abort(_("cannot verify bundle or remote repos"))

    def err(linkrev, msg, filename=None):
        if linkrev is not None:
            badrevs.add(linkrev)
        else:
            linkrev = '?'
        msg = "%s: %s" % (linkrev, msg)
        if filename:
            msg = "%s@%s" % (filename, msg)
        ui.warn(" " + msg + "\n")
        errors[0] += 1

    def exc(linkrev, msg, inst, filename=None):
        if isinstance(inst, KeyboardInterrupt):
            ui.warn(_("interrupted"))
            raise
        if not str(inst):
            inst = repr(inst)
        err(linkrev, "%s: %s" % (msg, inst), filename)

    def warn(msg):
        ui.warn(msg + "\n")
        warnings[0] += 1

    def checklog(obj, name, linkrev):
        if not len(obj) and (havecl or havemf):
            err(linkrev, _("empty or missing %s") % name)
            return

        d = obj.checksize()
        if d[0]:
            err(None, _("data length off by %d bytes") % d[0], name)
        if d[1]:
            err(None, _("index contains %d extra bytes") % d[1], name)

        if obj.version != revlog.REVLOGV0:
            if not revlogv1:
                warn(_("warning: `%s' uses revlog format 1") % name)
        elif revlogv1:
            warn(_("warning: `%s' uses revlog format 0") % name)

    def checkentry(obj, i, node, seen, linkrevs, f):
        lr = obj.linkrev(obj.rev(node))
        if lr < 0 or (havecl and lr not in linkrevs):
            if lr < 0 or lr >= len(cl):
                msg = _("rev %d points to nonexistent changeset %d")
            else:
                msg = _("rev %d points to unexpected changeset %d")
            err(None, msg % (i, lr), f)
            if linkrevs:
                if f and len(linkrevs) > 1:
                    try:
                        # attempt to filter down to real linkrevs
                        linkrevs = [l for l in linkrevs
                                    if lrugetctx(l)[f].filenode() == node]
                    except Exception:
                        pass
                warn(_(" (expected %s)") % " ".join(map(str, linkrevs)))
            lr = None # can't be trusted

        try:
            p1, p2 = obj.parents(node)
            if p1 not in seen and p1 != nullid:
                err(lr, _("unknown parent 1 %s of %s") %
                    (short(p1), short(node)), f)
            if p2 not in seen and p2 != nullid:
                err(lr, _("unknown parent 2 %s of %s") %
                    (short(p2), short(node)), f)
        except Exception, inst:
            exc(lr, _("checking parents of %s") % short(node), inst, f)

        if node in seen:
            err(lr, _("duplicate revision %d (%d)") % (i, seen[node]), f)
        seen[node] = i
        return lr

    if os.path.exists(repo.sjoin("journal")):
        ui.warn(_("abandoned transaction found - run hg recover\n"))

    revlogv1 = cl.version != revlog.REVLOGV0
    if ui.verbose or not revlogv1:
        ui.status(_("repository uses revlog format %d\n") %
                       (revlogv1 and 1 or 0))

    havecl = len(cl) > 0
    havemf = len(mf) > 0

    ui.status(_("checking changesets\n"))
    refersmf = False
    seen = {}
    checklog(cl, "changelog", 0)
    total = len(repo)
    for i in repo:
        ui.progress(_('checking'), i, total=total, unit=_('changesets'))
        n = cl.node(i)
        checkentry(cl, i, n, seen, [i], "changelog")

        try:
            changes = cl.read(n)
            if changes[0] != nullid:
                mflinkrevs.setdefault(changes[0], []).append(i)
                refersmf = True
            for f in changes[3]:
                filelinkrevs.setdefault(_normpath(f), []).append(i)
        except Exception, inst:
            refersmf = True
            exc(i, _("unpacking changeset %s") % short(n), inst)
    ui.progress(_('checking'), None)

    ui.status(_("checking manifests\n"))
    seen = {}
    if refersmf:
        # Do not check manifest if there are only changelog entries with
        # null manifests.
        checklog(mf, "manifest", 0)
    total = len(mf)
    for i in mf:
        ui.progress(_('checking'), i, total=total, unit=_('manifests'))
        n = mf.node(i)
        lr = checkentry(mf, i, n, seen, mflinkrevs.get(n, []), "manifest")
        if n in mflinkrevs:
            del mflinkrevs[n]
        else:
            err(lr, _("%s not in changesets") % short(n), "manifest")

        try:
            for f, fn in mf.readdelta(n).iteritems():
                if not f:
                    err(lr, _("file without name in manifest"))
                elif f != "/dev/null":
                    filenodes.setdefault(_normpath(f), {}).setdefault(fn, lr)
        except Exception, inst:
            exc(lr, _("reading manifest delta %s") % short(n), inst)
    ui.progress(_('checking'), None)

    ui.status(_("crosschecking files in changesets and manifests\n"))

    total = len(mflinkrevs) + len(filelinkrevs) + len(filenodes)
    count = 0
    if havemf:
        for c, m in sorted([(c, m) for m in mflinkrevs
                            for c in mflinkrevs[m]]):
            count += 1
            if m == nullid:
                continue
            ui.progress(_('crosschecking'), count, total=total)
            err(c, _("changeset refers to unknown manifest %s") % short(m))
        mflinkrevs = None # del is bad here due to scope issues

        for f in sorted(filelinkrevs):
            count += 1
            ui.progress(_('crosschecking'), count, total=total)
            if f not in filenodes:
                lr = filelinkrevs[f][0]
                err(lr, _("in changeset but not in manifest"), f)

    if havecl:
        for f in sorted(filenodes):
            count += 1
            ui.progress(_('crosschecking'), count, total=total)
            if f not in filelinkrevs:
                try:
                    fl = repo.file(f)
                    lr = min([fl.linkrev(fl.rev(n)) for n in filenodes[f]])
                except Exception:
                    lr = None
                err(lr, _("in manifest but not in changeset"), f)

    ui.progress(_('crosschecking'), None)

    ui.status(_("checking files\n"))

    storefiles = set()
    for f, f2, size in repo.store.datafiles():
        if not f:
            err(None, _("cannot decode filename '%s'") % f2)
        elif size > 0 or not revlogv1:
            storefiles.add(_normpath(f))

    files = sorted(set(filenodes) | set(filelinkrevs))
    total = len(files)
    for i, f in enumerate(files):
        ui.progress(_('checking'), i, item=f, total=total)
        try:
            linkrevs = filelinkrevs[f]
        except KeyError:
            # in manifest but not in changelog
            linkrevs = []

        if linkrevs:
            lr = linkrevs[0]
        else:
            lr = None

        try:
            fl = repo.file(f)
        except error.RevlogError, e:
            err(lr, _("broken revlog! (%s)") % e, f)
            continue

        for ff in fl.files():
            try:
                storefiles.remove(ff)
            except KeyError:
                err(lr, _("missing revlog!"), ff)

        checklog(fl, f, lr)
        seen = {}
        rp = None
        for i in fl:
            revisions += 1
            n = fl.node(i)
            lr = checkentry(fl, i, n, seen, linkrevs, f)
            if f in filenodes:
                if havemf and n not in filenodes[f]:
                    err(lr, _("%s not in manifests") % (short(n)), f)
                else:
                    del filenodes[f][n]

            # verify contents
            try:
                l = len(fl.read(n))
                rp = fl.renamed(n)
                if l != fl.size(i):
                    if len(fl.revision(n)) != fl.size(i):
                        err(lr, _("unpacked size is %s, %s expected") %
                            (l, fl.size(i)), f)
            except Exception, inst:
                exc(lr, _("unpacking %s") % short(n), inst, f)

            # check renames
            try:
                if rp:
                    if lr is not None and ui.verbose:
                        ctx = lrugetctx(lr)
                        found = False
                        for pctx in ctx.parents():
                            if rp[0] in pctx:
                                found = True
                                break
                        if not found:
                            warn(_("warning: copy source of '%s' not"
                                   " in parents of %s") % (f, ctx))
                    fl2 = repo.file(rp[0])
                    if not len(fl2):
                        err(lr, _("empty or missing copy source revlog %s:%s")
                            % (rp[0], short(rp[1])), f)
                    elif rp[1] == nullid:
                        ui.note(_("warning: %s@%s: copy source"
                                  " revision is nullid %s:%s\n")
                            % (f, lr, rp[0], short(rp[1])))
                    else:
                        fl2.rev(rp[1])
            except Exception, inst:
                exc(lr, _("checking rename of %s") % short(n), inst, f)

        # cross-check
        if f in filenodes:
            fns = [(lr, n) for n, lr in filenodes[f].iteritems()]
            for lr, node in sorted(fns):
                err(lr, _("%s in manifests not found") % short(node), f)
    ui.progress(_('checking'), None)

    for f in storefiles:
        warn(_("warning: orphan revlog '%s'") % f)

    ui.status(_("%d files, %d changesets, %d total revisions\n") %
                   (len(files), len(cl), revisions))
    if warnings[0]:
        ui.warn(_("%d warnings encountered!\n") % warnings[0])
    if errors[0]:
        ui.warn(_("%d integrity errors encountered!\n") % errors[0])
        if badrevs:
            ui.warn(_("(first damaged changeset appears to be %d)\n")
                    % min(badrevs))
        return 1
