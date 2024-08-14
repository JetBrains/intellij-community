# debugcommands.py - command processing for debug* commands
#
# Copyright 2005-2016 Olivia Mackall <olivia@selenic.com>
#
# This software may be used and distributed according to the terms of the
# GNU General Public License version 2 or any later version.


import binascii
import codecs
import collections
import contextlib
import difflib
import errno
import glob
import operator
import os
import platform
import random
import re
import socket
import ssl
import stat
import subprocess
import sys
import time

from .i18n import _
from .node import (
    bin,
    hex,
    nullrev,
    short,
)
from .pycompat import (
    open,
)
from . import (
    bundle2,
    bundlerepo,
    changegroup,
    cmdutil,
    color,
    context,
    copies,
    dagparser,
    dirstateutils,
    encoding,
    error,
    exchange,
    extensions,
    filelog,
    filemerge,
    filesetlang,
    formatter,
    hg,
    httppeer,
    localrepo,
    lock as lockmod,
    logcmdutil,
    manifest,
    mergestate as mergestatemod,
    metadata,
    obsolete,
    obsutil,
    pathutil,
    phases,
    policy,
    pvec,
    pycompat,
    registrar,
    repair,
    repoview,
    requirements,
    revlog,
    revset,
    revsetlang,
    scmutil,
    setdiscovery,
    simplemerge,
    sshpeer,
    sslutil,
    streamclone,
    strip,
    tags as tagsmod,
    templater,
    treediscovery,
    upgrade,
    url as urlmod,
    util,
    verify,
    vfs as vfsmod,
    wireprotoframing,
    wireprotoserver,
)
from .interfaces import repository
from .stabletailgraph import stabletailsort
from .utils import (
    cborutil,
    compression,
    dateutil,
    procutil,
    stringutil,
    urlutil,
)

from .revlogutils import (
    debug as revlog_debug,
    nodemap,
    rewrite,
    sidedata,
)

release = lockmod.release

table = {}
table.update(strip.command._table)
command = registrar.command(table)


@command(b'debugancestor', [], _(b'[INDEX] REV1 REV2'), optionalrepo=True)
def debugancestor(ui, repo, *args):
    """find the ancestor revision of two revisions in a given index"""
    if len(args) == 3:
        index, rev1, rev2 = args
        r = revlog.revlog(vfsmod.vfs(encoding.getcwd(), audit=False), index)
        lookup = r.lookup
    elif len(args) == 2:
        if not repo:
            raise error.Abort(
                _(b'there is no Mercurial repository here (.hg not found)')
            )
        rev1, rev2 = args
        r = repo.changelog
        lookup = repo.lookup
    else:
        raise error.Abort(_(b'either two or three arguments required'))
    a = r.ancestor(lookup(rev1), lookup(rev2))
    ui.write(b'%d:%s\n' % (r.rev(a), hex(a)))


@command(b'debugantivirusrunning', [])
def debugantivirusrunning(ui, repo):
    """attempt to trigger an antivirus scanner to see if one is active"""
    with repo.cachevfs.open('eicar-test-file.com', b'wb') as f:
        f.write(
            util.b85decode(
                # This is a base85-armored version of the EICAR test file. See
                # https://en.wikipedia.org/wiki/EICAR_test_file for details.
                b'ST#=}P$fV?P+K%yP+C|uG$>GBDK|qyDK~v2MM*<JQY}+dK~6+LQba95P'
                b'E<)&Nm5l)EmTEQR4qnHOhq9iNGnJx'
            )
        )
    # Give an AV engine time to scan the file.
    time.sleep(2)
    util.unlink(repo.cachevfs.join('eicar-test-file.com'))


@command(b'debugapplystreamclonebundle', [], b'FILE')
def debugapplystreamclonebundle(ui, repo, fname):
    """apply a stream clone bundle file"""
    f = hg.openpath(ui, fname)
    gen = exchange.readbundle(ui, f, fname)
    gen.apply(repo)


@command(
    b'debugbuilddag',
    [
        (
            b'm',
            b'mergeable-file',
            None,
            _(b'add single file mergeable changes'),
        ),
        (
            b'o',
            b'overwritten-file',
            None,
            _(b'add single file all revs overwrite'),
        ),
        (b'n', b'new-file', None, _(b'add new file at each rev')),
        (
            b'',
            b'from-existing',
            None,
            _(b'continue from a non-empty repository'),
        ),
    ],
    _(b'[OPTION]... [TEXT]'),
)
def debugbuilddag(
    ui,
    repo,
    text=None,
    mergeable_file=False,
    overwritten_file=False,
    new_file=False,
    from_existing=False,
):
    """builds a repo with a given DAG from scratch in the current empty repo

    The description of the DAG is read from stdin if not given on the
    command line.

    Elements:

     - "+n" is a linear run of n nodes based on the current default parent
     - "." is a single node based on the current default parent
     - "$" resets the default parent to null (implied at the start);
           otherwise the default parent is always the last node created
     - "<p" sets the default parent to the backref p
     - "*p" is a fork at parent p, which is a backref
     - "*p1/p2" is a merge of parents p1 and p2, which are backrefs
     - "/p2" is a merge of the preceding node and p2
     - ":tag" defines a local tag for the preceding node
     - "@branch" sets the named branch for subsequent nodes
     - "#...\\n" is a comment up to the end of the line

    Whitespace between the above elements is ignored.

    A backref is either

     - a number n, which references the node curr-n, where curr is the current
       node, or
     - the name of a local tag you placed earlier using ":tag", or
     - empty to denote the default parent.

    All string valued-elements are either strictly alphanumeric, or must
    be enclosed in double quotes ("..."), with "\\" as escape character.
    """

    if text is None:
        ui.status(_(b"reading DAG from stdin\n"))
        text = ui.fin.read()

    cl = repo.changelog
    if len(cl) > 0 and not from_existing:
        raise error.Abort(_(b'repository is not empty'))

    # determine number of revs in DAG
    total = 0
    for type, data in dagparser.parsedag(text):
        if type == b'n':
            total += 1

    if mergeable_file:
        linesperrev = 2
        # make a file with k lines per rev
        initialmergedlines = [b'%d' % i for i in range(0, total * linesperrev)]
        initialmergedlines.append(b"")

    tags = []
    progress = ui.makeprogress(
        _(b'building'), unit=_(b'revisions'), total=total
    )
    with progress, repo.wlock(), repo.lock(), repo.transaction(b"builddag"):
        at = -1
        atbranch = b'default'
        nodeids = []
        id = 0
        progress.update(id)
        for type, data in dagparser.parsedag(text):
            if type == b'n':
                ui.note((b'node %s\n' % pycompat.bytestr(data)))
                id, ps = data

                files = []
                filecontent = {}

                p2 = None
                if mergeable_file:
                    fn = b"mf"
                    p1 = repo[ps[0]]
                    if len(ps) > 1:
                        p2 = repo[ps[1]]
                        pa = p1.ancestor(p2)
                        base, local, other = [
                            x[fn].data() for x in (pa, p1, p2)
                        ]
                        m3 = simplemerge.Merge3Text(base, local, other)
                        ml = [
                            l.strip()
                            for l in simplemerge.render_minimized(m3)[0]
                        ]
                        ml.append(b"")
                    elif at > 0:
                        ml = p1[fn].data().split(b"\n")
                    else:
                        ml = initialmergedlines
                    ml[id * linesperrev] += b" r%i" % id
                    mergedtext = b"\n".join(ml)
                    files.append(fn)
                    filecontent[fn] = mergedtext

                if overwritten_file:
                    fn = b"of"
                    files.append(fn)
                    filecontent[fn] = b"r%i\n" % id

                if new_file:
                    fn = b"nf%i" % id
                    files.append(fn)
                    filecontent[fn] = b"r%i\n" % id
                    if len(ps) > 1:
                        if not p2:
                            p2 = repo[ps[1]]
                        for fn in p2:
                            if fn.startswith(b"nf"):
                                files.append(fn)
                                filecontent[fn] = p2[fn].data()

                def fctxfn(repo, cx, path):
                    if path in filecontent:
                        return context.memfilectx(
                            repo, cx, path, filecontent[path]
                        )
                    return None

                if len(ps) == 0 or ps[0] < 0:
                    pars = [None, None]
                elif len(ps) == 1:
                    pars = [nodeids[ps[0]], None]
                else:
                    pars = [nodeids[p] for p in ps]
                cx = context.memctx(
                    repo,
                    pars,
                    b"r%i" % id,
                    files,
                    fctxfn,
                    date=(id, 0),
                    user=b"debugbuilddag",
                    extra={b'branch': atbranch},
                )
                nodeid = repo.commitctx(cx)
                nodeids.append(nodeid)
                at = id
            elif type == b'l':
                id, name = data
                ui.note((b'tag %s\n' % name))
                tags.append(b"%s %s\n" % (hex(repo.changelog.node(id)), name))
            elif type == b'a':
                ui.note((b'branch %s\n' % data))
                atbranch = data
            progress.update(id)

        if tags:
            repo.vfs.write(b"localtags", b"".join(tags))


def _debugchangegroup(ui, gen, all=None, indent=0, **opts):
    indent_string = b' ' * indent
    if all:
        ui.writenoi18n(
            b"%sformat: id, p1, p2, cset, delta base, len(delta)\n"
            % indent_string
        )

        def showchunks(named):
            ui.write(b"\n%s%s\n" % (indent_string, named))
            for deltadata in gen.deltaiter():
                node, p1, p2, cs, deltabase, delta, flags, sidedata = deltadata
                ui.write(
                    b"%s%s %s %s %s %s %d\n"
                    % (
                        indent_string,
                        hex(node),
                        hex(p1),
                        hex(p2),
                        hex(cs),
                        hex(deltabase),
                        len(delta),
                    )
                )

        gen.changelogheader()
        showchunks(b"changelog")
        gen.manifestheader()
        showchunks(b"manifest")
        for chunkdata in iter(gen.filelogheader, {}):
            fname = chunkdata[b'filename']
            showchunks(fname)
    else:
        if isinstance(gen, bundle2.unbundle20):
            raise error.Abort(_(b'use debugbundle2 for this file'))
        gen.changelogheader()
        for deltadata in gen.deltaiter():
            node, p1, p2, cs, deltabase, delta, flags, sidedata = deltadata
            ui.write(b"%s%s\n" % (indent_string, hex(node)))


def _debugobsmarkers(ui, part, indent=0, **opts):
    """display version and markers contained in 'data'"""
    data = part.read()
    indent_string = b' ' * indent
    try:
        version, markers = obsolete._readmarkers(data)
    except error.UnknownVersion as exc:
        msg = b"%sunsupported version: %s (%d bytes)\n"
        msg %= indent_string, exc.version, len(data)
        ui.write(msg)
    else:
        msg = b"%sversion: %d (%d bytes)\n"
        msg %= indent_string, version, len(data)
        ui.write(msg)
        fm = ui.formatter(b'debugobsolete', pycompat.byteskwargs(opts))
        for rawmarker in sorted(markers):
            m = obsutil.marker(None, rawmarker)
            fm.startitem()
            fm.plain(indent_string)
            cmdutil.showmarker(fm, m)
        fm.end()


def _debugphaseheads(ui, data, indent=0):
    """display version and markers contained in 'data'"""
    indent_string = b' ' * indent
    headsbyphase = phases.binarydecode(data)
    for phase in phases.allphases:
        for head in headsbyphase[phase]:
            ui.write(indent_string)
            ui.write(b'%s %s\n' % (hex(head), phases.phasenames[phase]))


def _quasirepr(thing):
    if isinstance(thing, (dict, util.sortdict, collections.OrderedDict)):
        return b'{%s}' % (
            b', '.join(b'%s: %s' % (k, thing[k]) for k in sorted(thing))
        )
    return pycompat.bytestr(repr(thing))


def _debugbundle2(ui, gen, all=None, **opts):
    """lists the contents of a bundle2"""
    if not isinstance(gen, bundle2.unbundle20):
        raise error.Abort(_(b'not a bundle2 file'))
    ui.write((b'Stream params: %s\n' % _quasirepr(gen.params)))
    parttypes = opts.get('part_type', [])
    for part in gen.iterparts():
        if parttypes and part.type not in parttypes:
            continue
        msg = b'%s -- %s (mandatory: %r)\n'
        ui.write((msg % (part.type, _quasirepr(part.params), part.mandatory)))
        if part.type == b'changegroup':
            version = part.params.get(b'version', b'01')
            cg = changegroup.getunbundler(version, part, b'UN')
            if not ui.quiet:
                _debugchangegroup(ui, cg, all=all, indent=4, **opts)
        if part.type == b'obsmarkers':
            if not ui.quiet:
                _debugobsmarkers(ui, part, indent=4, **opts)
        if part.type == b'phase-heads':
            if not ui.quiet:
                _debugphaseheads(ui, part, indent=4)


@command(
    b'debugbundle',
    [
        (b'a', b'all', None, _(b'show all details')),
        (b'', b'part-type', [], _(b'show only the named part type')),
        (b'', b'spec', None, _(b'print the bundlespec of the bundle')),
    ],
    _(b'FILE'),
    norepo=True,
)
def debugbundle(ui, bundlepath, all=None, spec=None, **opts):
    """lists the contents of a bundle"""
    with hg.openpath(ui, bundlepath) as f:
        if spec:
            spec = exchange.getbundlespec(ui, f)
            ui.write(b'%s\n' % spec)
            return

        gen = exchange.readbundle(ui, f, bundlepath)
        if isinstance(gen, bundle2.unbundle20):
            return _debugbundle2(ui, gen, all=all, **opts)
        _debugchangegroup(ui, gen, all=all, **opts)


@command(b'debugcapabilities', [], _(b'PATH'), norepo=True)
def debugcapabilities(ui, path, **opts):
    """lists the capabilities of a remote peer"""
    peer = hg.peer(ui, pycompat.byteskwargs(opts), path)
    try:
        caps = peer.capabilities()
        ui.writenoi18n(b'Main capabilities:\n')
        for c in sorted(caps):
            ui.write(b'  %s\n' % c)
        b2caps = bundle2.bundle2caps(peer)
        if b2caps:
            ui.writenoi18n(b'Bundle2 capabilities:\n')
            for key, values in sorted(b2caps.items()):
                ui.write(b'  %s\n' % key)
                for v in values:
                    ui.write(b'    %s\n' % v)
    finally:
        peer.close()


@command(
    b'debugchangedfiles',
    [
        (
            b'',
            b'compute',
            False,
            b"compute information instead of reading it from storage",
        ),
    ],
    b'REV',
)
def debugchangedfiles(ui, repo, rev, **opts):
    """list the stored files changes for a revision"""
    ctx = logcmdutil.revsingle(repo, rev, None)
    files = None

    if opts['compute']:
        files = metadata.compute_all_files_changes(ctx)
    else:
        sd = repo.changelog.sidedata(ctx.rev())
        files_block = sd.get(sidedata.SD_FILES)
        if files_block is not None:
            files = metadata.decode_files_sidedata(sd)
    if files is not None:
        for f in sorted(files.touched):
            if f in files.added:
                action = b"added"
            elif f in files.removed:
                action = b"removed"
            elif f in files.merged:
                action = b"merged"
            elif f in files.salvaged:
                action = b"salvaged"
            else:
                action = b"touched"

            copy_parent = b""
            copy_source = b""
            if f in files.copied_from_p1:
                copy_parent = b"p1"
                copy_source = files.copied_from_p1[f]
            elif f in files.copied_from_p2:
                copy_parent = b"p2"
                copy_source = files.copied_from_p2[f]

            data = (action, copy_parent, f, copy_source)
            template = b"%-8s %2s: %s, %s;\n"
            ui.write(template % data)


@command(b'debugcheckstate', [], b'')
def debugcheckstate(ui, repo):
    """validate the correctness of the current dirstate"""
    errors = verify.verifier(repo)._verify_dirstate()
    if errors:
        errstr = _(b"dirstate inconsistent with current parent's manifest")
        raise error.Abort(errstr)


@command(
    b'debugcolor',
    [(b'', b'style', None, _(b'show all configured styles'))],
    b'hg debugcolor',
)
def debugcolor(ui, repo, **opts):
    """show available color, effects or style"""
    ui.writenoi18n(b'color mode: %s\n' % stringutil.pprint(ui._colormode))
    if opts.get('style'):
        return _debugdisplaystyle(ui)
    else:
        return _debugdisplaycolor(ui)


def _debugdisplaycolor(ui):
    ui = ui.copy()
    ui._styles.clear()
    for effect in color._activeeffects(ui).keys():
        ui._styles[effect] = effect
    if ui._terminfoparams:
        for k, v in ui.configitems(b'color'):
            if k.startswith(b'color.'):
                ui._styles[k] = k[6:]
            elif k.startswith(b'terminfo.'):
                ui._styles[k] = k[9:]
    ui.write(_(b'available colors:\n'))
    # sort label with a '_' after the other to group '_background' entry.
    items = sorted(ui._styles.items(), key=lambda i: (b'_' in i[0], i[0], i[1]))
    for colorname, label in items:
        ui.write(b'%s\n' % colorname, label=label)


def _debugdisplaystyle(ui):
    ui.write(_(b'available style:\n'))
    if not ui._styles:
        return
    width = max(len(s) for s in ui._styles)
    for label, effects in sorted(ui._styles.items()):
        ui.write(b'%s' % label, label=label)
        if effects:
            # 50
            ui.write(b': ')
            ui.write(b' ' * (max(0, width - len(label))))
            ui.write(b', '.join(ui.label(e, e) for e in effects.split()))
        ui.write(b'\n')


@command(b'debugcreatestreamclonebundle', [], b'FILE')
def debugcreatestreamclonebundle(ui, repo, fname):
    """create a stream clone bundle file

    Stream bundles are special bundles that are essentially archives of
    revlog files. They are commonly used for cloning very quickly.

    This command creates a "version 1" stream clone, which is deprecated in
    favor of newer versions of the stream protocol. Bundles using such newer
     versions can be generated using the `hg bundle` command.
    """
    # TODO we may want to turn this into an abort when this functionality
    # is moved into `hg bundle`.
    if phases.hassecret(repo):
        ui.warn(
            _(
                b'(warning: stream clone bundle will contain secret '
                b'revisions)\n'
            )
        )

    requirements, gen = streamclone.generatebundlev1(repo)
    changegroup.writechunks(ui, gen, fname)

    ui.write(_(b'bundle requirements: %s\n') % b', '.join(sorted(requirements)))


@command(
    b'debugdag',
    [
        (b't', b'tags', None, _(b'use tags as labels')),
        (b'b', b'branches', None, _(b'annotate with branch names')),
        (b'', b'dots', None, _(b'use dots for runs')),
        (b's', b'spaces', None, _(b'separate elements by spaces')),
    ],
    _(b'[OPTION]... [FILE [REV]...]'),
    optionalrepo=True,
)
def debugdag(ui, repo, file_=None, *revs, **opts):
    """format the changelog or an index DAG as a concise textual description

    If you pass a revlog index, the revlog's DAG is emitted. If you list
    revision numbers, they get labeled in the output as rN.

    Otherwise, the changelog DAG of the current repo is emitted.
    """
    spaces = opts.get('spaces')
    dots = opts.get('dots')
    if file_:
        rlog = revlog.revlog(vfsmod.vfs(encoding.getcwd(), audit=False), file_)
        revs = {int(r) for r in revs}

        def events():
            for r in rlog:
                yield b'n', (r, list(p for p in rlog.parentrevs(r) if p != -1))
                if r in revs:
                    yield b'l', (r, b"r%i" % r)

    elif repo:
        cl = repo.changelog
        tags = opts.get('tags')
        branches = opts.get('branches')
        if tags:
            labels = {}
            for l, n in repo.tags().items():
                labels.setdefault(cl.rev(n), []).append(l)

        def events():
            b = b"default"
            for r in cl:
                if branches:
                    newb = cl.read(cl.node(r))[5][b'branch']
                    if newb != b:
                        yield b'a', newb
                        b = newb
                yield b'n', (r, list(p for p in cl.parentrevs(r) if p != -1))
                if tags:
                    ls = labels.get(r)
                    if ls:
                        for l in ls:
                            yield b'l', (r, l)

    else:
        raise error.Abort(_(b'need repo for changelog dag'))

    for line in dagparser.dagtextlines(
        events(),
        addspaces=spaces,
        wraplabels=True,
        wrapannotations=True,
        wrapnonlinear=dots,
        usedots=dots,
        maxlinewidth=70,
    ):
        ui.write(line)
        ui.write(b"\n")


@command(b'debugdata', cmdutil.debugrevlogopts, _(b'-c|-m|FILE REV'))
def debugdata(ui, repo, file_, rev=None, **opts):
    """dump the contents of a data file revision"""
    if opts.get('changelog') or opts.get('manifest') or opts.get('dir'):
        if rev is not None:
            raise error.InputError(
                _(b'cannot specify a revision with other arguments')
            )
        file_, rev = None, file_
    elif rev is None:
        raise error.InputError(_(b'please specify a revision'))
    r = cmdutil.openstorage(
        repo, b'debugdata', file_, pycompat.byteskwargs(opts)
    )
    try:
        ui.write(r.rawdata(r.lookup(rev)))
    except KeyError:
        raise error.Abort(_(b'invalid revision identifier %s') % rev)


@command(
    b'debugdate',
    [(b'e', b'extended', None, _(b'try extended date formats'))],
    _(b'[-e] DATE [RANGE]'),
    norepo=True,
    optionalrepo=True,
)
def debugdate(ui, date, range=None, **opts):
    """parse and display a date"""
    if opts["extended"]:
        d = dateutil.parsedate(date, dateutil.extendeddateformats)
    else:
        d = dateutil.parsedate(date)
    ui.writenoi18n(b"internal: %d %d\n" % d)
    ui.writenoi18n(b"standard: %s\n" % dateutil.datestr(d))
    if range:
        m = dateutil.matchdate(range)
        ui.writenoi18n(b"match: %s\n" % m(d[0]))


@command(
    b'debugdeltachain',
    [
        (
            b'r',
            b'rev',
            [],
            _('restrict processing to these revlog revisions'),
        ),
        (
            b'',
            b'all-info',
            False,
            _('compute all information unless specified otherwise'),
        ),
        (
            b'',
            b'size-info',
            None,
            _('compute information related to deltas size'),
        ),
        (
            b'',
            b'dist-info',
            None,
            _('compute information related to base distance'),
        ),
        (
            b'',
            b'sparse-info',
            None,
            _('compute information related to sparse read'),
        ),
    ]
    + cmdutil.debugrevlogopts
    + cmdutil.formatteropts,
    _(b'-c|-m|FILE'),
    optionalrepo=True,
)
def debugdeltachain(ui, repo, file_=None, **opts):
    """dump information about delta chains in a revlog

    Output can be templatized. Available template keywords are:

    :``rev``:       revision number
    :``p1``:        parent 1 revision number (for reference)
    :``p2``:        parent 2 revision number (for reference)

    :``chainid``:   delta chain identifier (numbered by unique base)
    :``chainlen``:  delta chain length to this revision

    :``prevrev``:   previous revision in delta chain
    :``deltatype``: role of delta / how it was computed
                    - base:  a full snapshot
                    - snap:  an intermediate snapshot
                    - p1:    a delta against the first parent
                    - p2:    a delta against the second parent
                    - skip1: a delta against the same base as p1
                              (when p1 has empty delta
                    - skip2: a delta against the same base as p2
                              (when p2 has empty delta
                    - prev:  a delta against the previous revision
                    - other: a delta against an arbitrary revision

    :``compsize``:  compressed size of revision
    :``uncompsize``: uncompressed size of revision
    :``chainsize``: total size of compressed revisions in chain
    :``chainratio``: total chain size divided by uncompressed revision size
                    (new delta chains typically start at ratio 2.00)

    :``lindist``:   linear distance from base revision in delta chain to end
                    of this revision
    :``extradist``: total size of revisions not part of this delta chain from
                    base of delta chain to end of this revision; a measurement
                    of how much extra data we need to read/seek across to read
                    the delta chain for this revision
    :``extraratio``: extradist divided by chainsize; another representation of
                    how much unrelated data is needed to load this delta chain

    If the repository is configured to use the sparse read, additional keywords
    are available:

    :``readsize``:     total size of data read from the disk for a revision
                       (sum of the sizes of all the blocks)
    :``largestblock``: size of the largest block of data read from the disk
    :``readdensity``:  density of useful bytes in the data read from the disk
    :``srchunks``:  in how many data hunks the whole revision would be read

    It is possible to select the information to be computed, this can provide a
    noticeable speedup to the command in some cases.

    Always computed:

    - ``rev``
    - ``p1``
    - ``p2``
    - ``chainid``
    - ``chainlen``
    - ``prevrev``
    - ``deltatype``

    Computed with --no-size-info

    - ``compsize``
    - ``uncompsize``
    - ``chainsize``
    - ``chainratio``

    Computed with --no-dist-info

    - ``lindist``
    - ``extradist``
    - ``extraratio``

    Skipped with --no-sparse-info

    - ``readsize``
    - ``largestblock``
    - ``readdensity``
    - ``srchunks``

    --

    The sparse read can be enabled with experimental.sparse-read = True
    """
    revs = None
    revs_opt = opts.pop('rev', [])
    if revs_opt:
        revs = [int(r) for r in revs_opt]

    all_info = opts.pop('all_info', False)
    size_info = opts.pop('size_info', None)
    if size_info is None:
        size_info = all_info
    dist_info = opts.pop('dist_info', None)
    if dist_info is None:
        dist_info = all_info
    sparse_info = opts.pop('sparse_info', None)
    if sparse_info is None:
        sparse_info = all_info

    revlog = cmdutil.openrevlog(
        repo, b'debugdeltachain', file_, pycompat.byteskwargs(opts)
    )
    fm = ui.formatter(b'debugdeltachain', pycompat.byteskwargs(opts))

    lines = revlog_debug.debug_delta_chain(
        revlog,
        revs=revs,
        size_info=size_info,
        dist_info=dist_info,
        sparse_info=sparse_info,
    )
    # first entry is the header
    header = next(lines)
    fm.plain(header)
    for entry in lines:
        label = b' '.join(e[0] for e in entry)
        format = b' '.join(e[1] for e in entry)
        values = [e[3] for e in entry]
        data = dict((e[2], e[3]) for e in entry)
        fm.startitem()
        fm.write(label, format, *values, **data)
        fm.plain(b'\n')
    fm.end()


@command(
    b'debug-delta-find',
    cmdutil.debugrevlogopts
    + cmdutil.formatteropts
    + [
        (
            b'',
            b'source',
            b'full',
            _(b'input data feed to the process (full, storage, p1, p2, prev)'),
        ),
    ],
    _(b'-c|-m|FILE REV'),
    optionalrepo=True,
)
def debugdeltafind(ui, repo, arg_1, arg_2=None, source=b'full', **opts):
    """display the computation to get to a valid delta for storing REV

    This command will replay the process used to find the "best" delta to store
    a revision and display information about all the steps used to get to that
    result.

    By default, the process is fed with a the full-text for the revision. This
    can be controlled with the --source flag.

    The revision use the revision number of the target storage (not changelog
    revision number).

    note: the process is initiated from a full text of the revision to store.
    """
    if arg_2 is None:
        file_ = None
        rev = arg_1
    else:
        file_ = arg_1
        rev = arg_2

    rev = int(rev)

    revlog = cmdutil.openrevlog(
        repo, b'debugdeltachain', file_, pycompat.byteskwargs(opts)
    )
    p1r, p2r = revlog.parentrevs(rev)

    if source == b'full':
        base_rev = nullrev
    elif source == b'storage':
        base_rev = revlog.deltaparent(rev)
    elif source == b'p1':
        base_rev = p1r
    elif source == b'p2':
        base_rev = p2r
    elif source == b'prev':
        base_rev = rev - 1
    else:
        raise error.InputError(b"invalid --source value: %s" % source)

    revlog_debug.debug_delta_find(ui, revlog, rev, base_rev=base_rev)


@command(
    b'debugdirstate|debugstate',
    [
        (
            b'',
            b'nodates',
            None,
            _(b'do not display the saved mtime (DEPRECATED)'),
        ),
        (b'', b'dates', True, _(b'display the saved mtime')),
        (b'', b'datesort', None, _(b'sort by saved mtime')),
        (
            b'',
            b'docket',
            False,
            _(b'display the docket (metadata file) instead'),
        ),
        (
            b'',
            b'all',
            False,
            _(b'display dirstate-v2 tree nodes that would not exist in v1'),
        ),
    ],
    _(b'[OPTION]...'),
)
def debugstate(ui, repo, **opts):
    """show the contents of the current dirstate"""

    if opts.get("docket"):
        if not repo.dirstate._use_dirstate_v2:
            raise error.Abort(_(b'dirstate v1 does not have a docket'))

        docket = repo.dirstate._map.docket
        (
            start_offset,
            root_nodes,
            nodes_with_entry,
            nodes_with_copy,
            unused_bytes,
            _unused,
            ignore_pattern,
        ) = dirstateutils.v2.TREE_METADATA.unpack(docket.tree_metadata)

        ui.write(_(b"size of dirstate data: %d\n") % docket.data_size)
        ui.write(_(b"data file uuid: %s\n") % docket.uuid)
        ui.write(_(b"start offset of root nodes: %d\n") % start_offset)
        ui.write(_(b"number of root nodes: %d\n") % root_nodes)
        ui.write(_(b"nodes with entries: %d\n") % nodes_with_entry)
        ui.write(_(b"nodes with copies: %d\n") % nodes_with_copy)
        ui.write(_(b"number of unused bytes: %d\n") % unused_bytes)
        ui.write(
            _(b"ignore pattern hash: %s\n") % binascii.hexlify(ignore_pattern)
        )
        return

    nodates = not opts['dates']
    if opts.get('nodates') is not None:
        nodates = True
    datesort = opts.get('datesort')

    if datesort:

        def keyfunc(entry):
            filename, _state, _mode, _size, mtime = entry
            return (mtime, filename)

    else:
        keyfunc = None  # sort by filename
    entries = list(repo.dirstate._map.debug_iter(all=opts['all']))
    entries.sort(key=keyfunc)
    for entry in entries:
        filename, state, mode, size, mtime = entry
        if mtime == -1:
            timestr = b'unset               '
        elif nodates:
            timestr = b'set                 '
        else:
            timestr = time.strftime("%Y-%m-%d %H:%M:%S ", time.localtime(mtime))
            timestr = encoding.strtolocal(timestr)
        if mode & 0o20000:
            mode = b'lnk'
        else:
            mode = b'%3o' % (mode & 0o777 & ~util.umask)
        ui.write(b"%c %s %10d %s%s\n" % (state, mode, size, timestr, filename))
    for f in repo.dirstate.copies():
        ui.write(_(b"copy: %s -> %s\n") % (repo.dirstate.copied(f), f))


@command(
    b'debugdirstateignorepatternshash',
    [],
    _(b''),
)
def debugdirstateignorepatternshash(ui, repo, **opts):
    """show the hash of ignore patterns stored in dirstate if v2,
    or nothing for dirstate-v2
    """
    if repo.dirstate._use_dirstate_v2:
        docket = repo.dirstate._map.docket
        hash_len = 20  # 160 bits for SHA-1
        hash_bytes = docket.tree_metadata[-hash_len:]
        ui.write(binascii.hexlify(hash_bytes) + b'\n')


@command(
    b'debugdiscovery',
    [
        (b'', b'old', None, _(b'use old-style discovery')),
        (
            b'',
            b'nonheads',
            None,
            _(b'use old-style discovery with non-heads included'),
        ),
        (b'', b'rev', [], b'restrict discovery to this set of revs'),
        (b'', b'seed', b'12323', b'specify the random seed use for discovery'),
        (
            b'',
            b'local-as-revs',
            b"",
            b'treat local has having these revisions only',
        ),
        (
            b'',
            b'remote-as-revs',
            b"",
            b'use local as remote, with only these revisions',
        ),
    ]
    + cmdutil.remoteopts
    + cmdutil.formatteropts,
    _(b'[--rev REV] [OTHER]'),
)
def debugdiscovery(ui, repo, remoteurl=b"default", **opts):
    """runs the changeset discovery protocol in isolation

    The local peer can be "replaced" by a subset of the local repository by
    using the `--local-as-revs` flag. In the same way, the usual `remote` peer
    can be "replaced" by a subset of the local repository using the
    `--remote-as-revs` flag. This is useful to efficiently debug pathological
    discovery situations.

    The following developer oriented config are relevant for people playing with this command:

    * devel.discovery.exchange-heads=True

      If False, the discovery will not start with
      remote head fetching and local head querying.

    * devel.discovery.grow-sample=True

      If False, the sample size used in set discovery will not be increased
      through the process

    * devel.discovery.grow-sample.dynamic=True

      When discovery.grow-sample.dynamic is True, the default, the sample size is
      adapted to the shape of the undecided set (it is set to the max of:
      <target-size>, len(roots(undecided)), len(heads(undecided)

    * devel.discovery.grow-sample.rate=1.05

      the rate at which the sample grow

    * devel.discovery.randomize=True

      If andom sampling during discovery are deterministic. It is meant for
      integration tests.

    * devel.discovery.sample-size=200

      Control the initial size of the discovery sample

    * devel.discovery.sample-size.initial=100

      Control the initial size of the discovery for initial change
    """
    unfi = repo.unfiltered()

    # setup potential extra filtering
    local_revs = opts["local_as_revs"]
    remote_revs = opts["remote_as_revs"]

    # make sure tests are repeatable
    random.seed(int(opts['seed']))

    if not remote_revs:
        path = urlutil.get_unique_pull_path_obj(
            b'debugdiscovery', ui, remoteurl
        )
        branches = (path.branch, [])
        remote = hg.peer(repo, pycompat.byteskwargs(opts), path)
        ui.status(_(b'comparing with %s\n') % urlutil.hidepassword(path.loc))
    else:
        branches = (None, [])
        remote_filtered_revs = logcmdutil.revrange(
            unfi, [b"not (::(%s))" % remote_revs]
        )
        remote_filtered_revs = frozenset(remote_filtered_revs)

        def remote_func(x):
            return remote_filtered_revs

        repoview.filtertable[b'debug-discovery-remote-filter'] = remote_func

        remote = repo.peer()
        remote._repo = remote._repo.filtered(b'debug-discovery-remote-filter')

    if local_revs:
        local_filtered_revs = logcmdutil.revrange(
            unfi, [b"not (::(%s))" % local_revs]
        )
        local_filtered_revs = frozenset(local_filtered_revs)

        def local_func(x):
            return local_filtered_revs

        repoview.filtertable[b'debug-discovery-local-filter'] = local_func
        repo = repo.filtered(b'debug-discovery-local-filter')

    data = {}
    if opts.get('old'):

        def doit(pushedrevs, remoteheads, remote=remote):
            if not hasattr(remote, 'branches'):
                # enable in-client legacy support
                remote = localrepo.locallegacypeer(remote.local())
                if remote_revs:
                    r = remote._repo.filtered(b'debug-discovery-remote-filter')
                    remote._repo = r
            common, _in, hds = treediscovery.findcommonincoming(
                repo, remote, force=True, audit=data
            )
            common = set(common)
            if not opts.get('nonheads'):
                ui.writenoi18n(
                    b"unpruned common: %s\n"
                    % b" ".join(sorted(short(n) for n in common))
                )

                clnode = repo.changelog.node
                common = repo.revs(b'heads(::%ln)', common)
                common = {clnode(r) for r in common}
            return common, hds

    else:

        def doit(pushedrevs, remoteheads, remote=remote):
            nodes = None
            if pushedrevs:
                revs = logcmdutil.revrange(repo, pushedrevs)
                nodes = [repo[r].node() for r in revs]
            common, any, hds = setdiscovery.findcommonheads(
                ui,
                repo,
                remote,
                ancestorsof=nodes,
                audit=data,
                abortwhenunrelated=False,
            )
            return common, hds

    remoterevs, _checkout = hg.addbranchrevs(repo, remote, branches, revs=None)
    localrevs = opts['rev']

    fm = ui.formatter(b'debugdiscovery', pycompat.byteskwargs(opts))
    if fm.strict_format:

        @contextlib.contextmanager
        def may_capture_output():
            ui.pushbuffer()
            yield
            data[b'output'] = ui.popbuffer()

    else:
        may_capture_output = util.nullcontextmanager
    with may_capture_output():
        with util.timedcm('debug-discovery') as t:
            common, hds = doit(localrevs, remoterevs)

    # compute all statistics
    if len(common) == 1 and repo.nullid in common:
        common = set()
    heads_common = set(common)
    heads_remote = set(hds)
    heads_local = set(repo.heads())
    # note: they cannot be a local or remote head that is in common and not
    # itself a head of common.
    heads_common_local = heads_common & heads_local
    heads_common_remote = heads_common & heads_remote
    heads_common_both = heads_common & heads_remote & heads_local

    all = repo.revs(b'all()')
    common = repo.revs(b'::%ln', common)
    roots_common = repo.revs(b'roots(::%ld)', common)
    missing = repo.revs(b'not ::%ld', common)
    heads_missing = repo.revs(b'heads(%ld)', missing)
    roots_missing = repo.revs(b'roots(%ld)', missing)
    assert len(common) + len(missing) == len(all)

    initial_undecided = repo.revs(
        b'not (::%ln or %ln::)', heads_common_remote, heads_common_local
    )
    heads_initial_undecided = repo.revs(b'heads(%ld)', initial_undecided)
    roots_initial_undecided = repo.revs(b'roots(%ld)', initial_undecided)
    common_initial_undecided = initial_undecided & common
    missing_initial_undecided = initial_undecided & missing

    data[b'elapsed'] = t.elapsed
    data[b'nb-common-heads'] = len(heads_common)
    data[b'nb-common-heads-local'] = len(heads_common_local)
    data[b'nb-common-heads-remote'] = len(heads_common_remote)
    data[b'nb-common-heads-both'] = len(heads_common_both)
    data[b'nb-common-roots'] = len(roots_common)
    data[b'nb-head-local'] = len(heads_local)
    data[b'nb-head-local-missing'] = len(heads_local) - len(heads_common_local)
    data[b'nb-head-remote'] = len(heads_remote)
    data[b'nb-head-remote-unknown'] = len(heads_remote) - len(
        heads_common_remote
    )
    data[b'nb-revs'] = len(all)
    data[b'nb-revs-common'] = len(common)
    data[b'nb-revs-missing'] = len(missing)
    data[b'nb-missing-heads'] = len(heads_missing)
    data[b'nb-missing-roots'] = len(roots_missing)
    data[b'nb-ini_und'] = len(initial_undecided)
    data[b'nb-ini_und-heads'] = len(heads_initial_undecided)
    data[b'nb-ini_und-roots'] = len(roots_initial_undecided)
    data[b'nb-ini_und-common'] = len(common_initial_undecided)
    data[b'nb-ini_und-missing'] = len(missing_initial_undecided)

    fm.startitem()
    fm.data(**pycompat.strkwargs(data))
    # display discovery summary
    fm.plain(b"elapsed time:  %(elapsed)f seconds\n" % data)
    fm.plain(b"round-trips:           %(total-roundtrips)9d\n" % data)
    if b'total-round-trips-heads' in data:
        fm.plain(
            b"  round-trips-heads:    %(total-round-trips-heads)9d\n" % data
        )
    if b'total-round-trips-branches' in data:
        fm.plain(
            b"  round-trips-branches:    %(total-round-trips-branches)9d\n"
            % data
        )
    if b'total-round-trips-between' in data:
        fm.plain(
            b"  round-trips-between:    %(total-round-trips-between)9d\n" % data
        )
    fm.plain(b"queries:               %(total-queries)9d\n" % data)
    if b'total-queries-branches' in data:
        fm.plain(b"  queries-branches:    %(total-queries-branches)9d\n" % data)
    if b'total-queries-between' in data:
        fm.plain(b"  queries-between:     %(total-queries-between)9d\n" % data)
    fm.plain(b"heads summary:\n")
    fm.plain(b"  total common heads:  %(nb-common-heads)9d\n" % data)
    fm.plain(b"    also local heads:  %(nb-common-heads-local)9d\n" % data)
    fm.plain(b"    also remote heads: %(nb-common-heads-remote)9d\n" % data)
    fm.plain(b"    both:              %(nb-common-heads-both)9d\n" % data)
    fm.plain(b"  local heads:         %(nb-head-local)9d\n" % data)
    fm.plain(b"    common:            %(nb-common-heads-local)9d\n" % data)
    fm.plain(b"    missing:           %(nb-head-local-missing)9d\n" % data)
    fm.plain(b"  remote heads:        %(nb-head-remote)9d\n" % data)
    fm.plain(b"    common:            %(nb-common-heads-remote)9d\n" % data)
    fm.plain(b"    unknown:           %(nb-head-remote-unknown)9d\n" % data)
    fm.plain(b"local changesets:      %(nb-revs)9d\n" % data)
    fm.plain(b"  common:              %(nb-revs-common)9d\n" % data)
    fm.plain(b"    heads:             %(nb-common-heads)9d\n" % data)
    fm.plain(b"    roots:             %(nb-common-roots)9d\n" % data)
    fm.plain(b"  missing:             %(nb-revs-missing)9d\n" % data)
    fm.plain(b"    heads:             %(nb-missing-heads)9d\n" % data)
    fm.plain(b"    roots:             %(nb-missing-roots)9d\n" % data)
    fm.plain(b"  first undecided set: %(nb-ini_und)9d\n" % data)
    fm.plain(b"    heads:             %(nb-ini_und-heads)9d\n" % data)
    fm.plain(b"    roots:             %(nb-ini_und-roots)9d\n" % data)
    fm.plain(b"    common:            %(nb-ini_und-common)9d\n" % data)
    fm.plain(b"    missing:           %(nb-ini_und-missing)9d\n" % data)

    if ui.verbose:
        fm.plain(
            b"common heads: %s\n"
            % b" ".join(sorted(short(n) for n in heads_common))
        )
    fm.end()


_chunksize = 4 << 10


@command(
    b'debugdownload',
    [
        (b'o', b'output', b'', _(b'path')),
    ],
    optionalrepo=True,
)
def debugdownload(ui, repo, url, output=None, **opts):
    """download a resource using Mercurial logic and config"""
    fh = urlmod.open(ui, url, output)

    dest = ui
    if output:
        dest = open(output, b"wb", _chunksize)
    try:
        data = fh.read(_chunksize)
        while data:
            dest.write(data)
            data = fh.read(_chunksize)
    finally:
        if output:
            dest.close()


@command(b'debugextensions', cmdutil.formatteropts, [], optionalrepo=True)
def debugextensions(ui, repo, **opts):
    '''show information about active extensions'''
    exts = extensions.extensions(ui)
    hgver = util.version()
    fm = ui.formatter(b'debugextensions', pycompat.byteskwargs(opts))
    for extname, extmod in sorted(exts, key=operator.itemgetter(0)):
        isinternal = extensions.ismoduleinternal(extmod)
        extsource = None

        if hasattr(extmod, '__file__'):
            extsource = pycompat.fsencode(extmod.__file__)
        elif getattr(sys, 'oxidized', False):
            extsource = pycompat.sysexecutable
        if isinternal:
            exttestedwith = []  # never expose magic string to users
        else:
            exttestedwith = getattr(extmod, 'testedwith', b'').split()
        extbuglink = getattr(extmod, 'buglink', None)

        fm.startitem()

        if ui.quiet or ui.verbose:
            fm.write(b'name', b'%s\n', extname)
        else:
            fm.write(b'name', b'%s', extname)
            if isinternal or hgver in exttestedwith:
                fm.plain(b'\n')
            elif not exttestedwith:
                fm.plain(_(b' (untested!)\n'))
            else:
                lasttestedversion = exttestedwith[-1]
                fm.plain(b' (%s!)\n' % lasttestedversion)

        fm.condwrite(
            ui.verbose and extsource,
            b'source',
            _(b'  location: %s\n'),
            extsource or b"",
        )

        if ui.verbose:
            fm.plain(_(b'  bundled: %s\n') % [b'no', b'yes'][isinternal])
        fm.data(bundled=isinternal)

        fm.condwrite(
            ui.verbose and exttestedwith,
            b'testedwith',
            _(b'  tested with: %s\n'),
            fm.formatlist(exttestedwith, name=b'ver'),
        )

        fm.condwrite(
            ui.verbose and extbuglink,
            b'buglink',
            _(b'  bug reporting: %s\n'),
            extbuglink or b"",
        )

    fm.end()


@command(
    b'debugfileset',
    [
        (
            b'r',
            b'rev',
            b'',
            _(b'apply the filespec on this revision'),
            _(b'REV'),
        ),
        (
            b'',
            b'all-files',
            False,
            _(b'test files from all revisions and working directory'),
        ),
        (
            b's',
            b'show-matcher',
            None,
            _(b'print internal representation of matcher'),
        ),
        (
            b'p',
            b'show-stage',
            [],
            _(b'print parsed tree at the given stage'),
            _(b'NAME'),
        ),
    ],
    _(b'[-r REV] [--all-files] [OPTION]... FILESPEC'),
)
def debugfileset(ui, repo, expr, **opts):
    '''parse and apply a fileset specification'''
    from . import fileset

    fileset.symbols  # force import of fileset so we have predicates to optimize

    ctx = logcmdutil.revsingle(repo, opts.get('rev'), None)

    stages = [
        (b'parsed', pycompat.identity),
        (b'analyzed', filesetlang.analyze),
        (b'optimized', filesetlang.optimize),
    ]
    stagenames = {n for n, f in stages}

    showalways = set()
    if ui.verbose and not opts['show_stage']:
        # show parsed tree by --verbose (deprecated)
        showalways.add(b'parsed')
    if opts['show_stage'] == [b'all']:
        showalways.update(stagenames)
    else:
        for n in opts['show_stage']:
            if n not in stagenames:
                raise error.Abort(_(b'invalid stage name: %s') % n)
        showalways.update(opts['show_stage'])

    tree = filesetlang.parse(expr)
    for n, f in stages:
        tree = f(tree)
        if n in showalways:
            if opts['show_stage'] or n != b'parsed':
                ui.write(b"* %s:\n" % n)
            ui.write(filesetlang.prettyformat(tree), b"\n")

    files = set()
    if opts['all_files']:
        for r in repo:
            c = repo[r]
            files.update(c.files())
            files.update(c.substate)
    if opts['all_files'] or ctx.rev() is None:
        wctx = repo[None]
        files.update(
            repo.dirstate.walk(
                scmutil.matchall(repo),
                subrepos=list(wctx.substate),
                unknown=True,
                ignored=True,
            )
        )
        files.update(wctx.substate)
    else:
        files.update(ctx.files())
        files.update(ctx.substate)

    m = ctx.matchfileset(repo.getcwd(), expr)
    if opts['show_matcher'] or (opts['show_matcher'] is None and ui.verbose):
        ui.writenoi18n(b'* matcher:\n', stringutil.prettyrepr(m), b'\n')
    for f in sorted(files):
        if not m(f):
            continue
        ui.write(b"%s\n" % f)


@command(
    b"debug-repair-issue6528",
    [
        (
            b'',
            b'to-report',
            b'',
            _(b'build a report of affected revisions to this file'),
            _(b'FILE'),
        ),
        (
            b'',
            b'from-report',
            b'',
            _(b'repair revisions listed in this report file'),
            _(b'FILE'),
        ),
        (
            b'',
            b'paranoid',
            False,
            _(b'check that both detection methods do the same thing'),
        ),
    ]
    + cmdutil.dryrunopts,
)
def debug_repair_issue6528(ui, repo, **opts):
    """find affected revisions and repair them. See issue6528 for more details.

    The `--to-report` and `--from-report` flags allow you to cache and reuse the
    computation of affected revisions for a given repository across clones.
    The report format is line-based (with empty lines ignored):

    ```
    <ascii-hex of the affected revision>,... <unencoded filelog index filename>
    ```

    There can be multiple broken revisions per filelog, they are separated by
    a comma with no spaces. The only space is between the revision(s) and the
    filename.

    Note that this does *not* mean that this repairs future affected revisions,
    that needs a separate fix at the exchange level that was introduced in
    Mercurial 5.9.1.

    There is a `--paranoid` flag to test that the fast implementation is correct
    by checking it against the slow implementation. Since this matter is quite
    urgent and testing every edge-case is probably quite costly, we use this
    method to test on large repositories as a fuzzing method of sorts.
    """
    cmdutil.check_incompatible_arguments(
        opts, 'to_report', ['from_report', 'dry_run']
    )
    dry_run = opts.get('dry_run')
    to_report = opts.get('to_report')
    from_report = opts.get('from_report')
    paranoid = opts.get('paranoid')
    # TODO maybe add filelog pattern and revision pattern parameters to help
    # narrow down the search for users that know what they're looking for?

    if requirements.REVLOGV1_REQUIREMENT not in repo.requirements:
        msg = b"can only repair revlogv1 repositories, v2 is not affected"
        raise error.Abort(_(msg))

    rewrite.repair_issue6528(
        ui,
        repo,
        dry_run=dry_run,
        to_report=to_report,
        from_report=from_report,
        paranoid=paranoid,
    )


@command(b'debugformat', [] + cmdutil.formatteropts)
def debugformat(ui, repo, **opts):
    """display format information about the current repository

    Use --verbose to get extra information about current config value and
    Mercurial default."""
    maxvariantlength = max(len(fv.name) for fv in upgrade.allformatvariant)
    maxvariantlength = max(len(b'format-variant'), maxvariantlength)

    def makeformatname(name):
        return b'%s:' + (b' ' * (maxvariantlength - len(name)))

    fm = ui.formatter(b'debugformat', pycompat.byteskwargs(opts))
    if fm.isplain():

        def formatvalue(value):
            if hasattr(value, 'startswith'):
                return value
            if value:
                return b'yes'
            else:
                return b'no'

    else:
        formatvalue = pycompat.identity

    fm.plain(b'format-variant')
    fm.plain(b' ' * (maxvariantlength - len(b'format-variant')))
    fm.plain(b' repo')
    if ui.verbose:
        fm.plain(b' config default')
    fm.plain(b'\n')
    for fv in upgrade.allformatvariant:
        fm.startitem()
        repovalue = fv.fromrepo(repo)
        configvalue = fv.fromconfig(repo)

        if repovalue != configvalue:
            namelabel = b'formatvariant.name.mismatchconfig'
            repolabel = b'formatvariant.repo.mismatchconfig'
        elif repovalue != fv.default:
            namelabel = b'formatvariant.name.mismatchdefault'
            repolabel = b'formatvariant.repo.mismatchdefault'
        else:
            namelabel = b'formatvariant.name.uptodate'
            repolabel = b'formatvariant.repo.uptodate'

        fm.write(b'name', makeformatname(fv.name), fv.name, label=namelabel)
        fm.write(b'repo', b' %3s', formatvalue(repovalue), label=repolabel)
        if fv.default != configvalue:
            configlabel = b'formatvariant.config.special'
        else:
            configlabel = b'formatvariant.config.default'
        fm.condwrite(
            ui.verbose,
            b'config',
            b' %6s',
            formatvalue(configvalue),
            label=configlabel,
        )
        fm.condwrite(
            ui.verbose,
            b'default',
            b' %7s',
            formatvalue(fv.default),
            label=b'formatvariant.default',
        )
        fm.plain(b'\n')
    fm.end()


@command(b'debugfsinfo', [], _(b'[PATH]'), norepo=True)
def debugfsinfo(ui, path=b"."):
    """show information detected about current filesystem"""
    ui.writenoi18n(b'path: %s\n' % path)
    ui.writenoi18n(
        b'mounted on: %s\n' % (util.getfsmountpoint(path) or b'(unknown)')
    )
    ui.writenoi18n(b'exec: %s\n' % (util.checkexec(path) and b'yes' or b'no'))
    ui.writenoi18n(b'fstype: %s\n' % (util.getfstype(path) or b'(unknown)'))
    ui.writenoi18n(
        b'symlink: %s\n' % (util.checklink(path) and b'yes' or b'no')
    )
    ui.writenoi18n(
        b'hardlink: %s\n' % (util.checknlink(path) and b'yes' or b'no')
    )
    casesensitive = b'(unknown)'
    try:
        with pycompat.namedtempfile(prefix=b'.debugfsinfo', dir=path) as f:
            casesensitive = util.fscasesensitive(f.name) and b'yes' or b'no'
    except OSError:
        pass
    ui.writenoi18n(b'case-sensitive: %s\n' % casesensitive)


@command(
    b'debuggetbundle',
    [
        (b'H', b'head', [], _(b'id of head node'), _(b'ID')),
        (b'C', b'common', [], _(b'id of common node'), _(b'ID')),
        (
            b't',
            b'type',
            b'bzip2',
            _(b'bundle compression type to use'),
            _(b'TYPE'),
        ),
    ],
    _(b'REPO FILE [-H|-C ID]...'),
    norepo=True,
)
def debuggetbundle(ui, repopath, bundlepath, head=None, common=None, **opts):
    """retrieves a bundle from a repo

    Every ID must be a full-length hex node id string. Saves the bundle to the
    given file.
    """
    repo = hg.peer(ui, pycompat.byteskwargs(opts), repopath)
    if not repo.capable(b'getbundle'):
        raise error.Abort(b"getbundle() not supported by target repository")
    args = {}
    if common:
        args['common'] = [bin(s) for s in common]
    if head:
        args['heads'] = [bin(s) for s in head]
    # TODO: get desired bundlecaps from command line.
    args['bundlecaps'] = None
    bundle = repo.getbundle(b'debug', **args)

    bundletype = opts.get('type', b'bzip2').lower()
    btypes = {
        b'none': b'HG10UN',
        b'bzip2': b'HG10BZ',
        b'gzip': b'HG10GZ',
        b'bundle2': b'HG20',
    }
    bundletype = btypes.get(bundletype)
    if bundletype not in bundle2.bundletypes:
        raise error.Abort(_(b'unknown bundle type specified with --type'))
    bundle2.writebundle(ui, bundle, bundlepath, bundletype)


@command(b'debugignore', [], b'[FILE]...')
def debugignore(ui, repo, *files, **opts):
    """display the combined ignore pattern and information about ignored files

    With no argument display the combined ignore pattern.

    Given space separated file names, shows if the given file is ignored and
    if so, show the ignore rule (file and line number) that matched it.
    """
    ignore = repo.dirstate._ignore
    if not files:
        # Show all the patterns
        ui.write(b"%s\n" % pycompat.byterepr(ignore))
    else:
        m = scmutil.match(repo[None], pats=files)
        uipathfn = scmutil.getuipathfn(repo, legacyrelativevalue=True)
        for f in m.files():
            nf = util.normpath(f)
            ignored = None
            ignoredata = None
            if nf != b'.':
                if ignore(nf):
                    ignored = nf
                    ignoredata = repo.dirstate._ignorefileandline(nf)
                else:
                    for p in pathutil.finddirs(nf):
                        if ignore(p):
                            ignored = p
                            ignoredata = repo.dirstate._ignorefileandline(p)
                            break
            if ignored:
                if ignored == nf:
                    ui.write(_(b"%s is ignored\n") % uipathfn(f))
                else:
                    ui.write(
                        _(
                            b"%s is ignored because of "
                            b"containing directory %s\n"
                        )
                        % (uipathfn(f), ignored)
                    )
                ignorefile, lineno, line = ignoredata
                ui.write(
                    _(b"(ignore rule in %s, line %d: '%s')\n")
                    % (ignorefile, lineno, line)
                )
            else:
                ui.write(_(b"%s is not ignored\n") % uipathfn(f))


@command(
    b'debug-revlog-index|debugindex',
    cmdutil.debugrevlogopts + cmdutil.formatteropts,
    _(b'-c|-m|FILE'),
)
def debugindex(ui, repo, file_=None, **opts):
    """dump index data for a revlog"""
    opts = pycompat.byteskwargs(opts)
    store = cmdutil.openstorage(repo, b'debugindex', file_, opts)

    fm = ui.formatter(b'debugindex', opts)

    revlog = getattr(store, '_revlog', store)

    return revlog_debug.debug_index(
        ui,
        repo,
        formatter=fm,
        revlog=revlog,
        full_node=ui.debugflag,
    )


@command(
    b'debugindexdot',
    cmdutil.debugrevlogopts,
    _(b'-c|-m|FILE'),
    optionalrepo=True,
)
def debugindexdot(ui, repo, file_=None, **opts):
    """dump an index DAG as a graphviz dot file"""
    r = cmdutil.openstorage(
        repo, b'debugindexdot', file_, pycompat.byteskwargs(opts)
    )
    ui.writenoi18n(b"digraph G {\n")
    for i in r:
        node = r.node(i)
        pp = r.parents(node)
        ui.write(b"\t%d -> %d\n" % (r.rev(pp[0]), i))
        if pp[1] != repo.nullid:
            ui.write(b"\t%d -> %d\n" % (r.rev(pp[1]), i))
    ui.write(b"}\n")


@command(b'debugindexstats', [])
def debugindexstats(ui, repo):
    """show stats related to the changelog index"""
    repo.changelog.shortest(repo.nullid, 1)
    index = repo.changelog.index
    if not hasattr(index, 'stats'):
        raise error.Abort(_(b'debugindexstats only works with native C code'))
    for k, v in sorted(index.stats().items()):
        ui.write(b'%s: %d\n' % (k, v))


@command(b'debuginstall', [] + cmdutil.formatteropts, b'', norepo=True)
def debuginstall(ui, **opts):
    """test Mercurial installation

    Returns 0 on success.
    """
    problems = 0

    fm = ui.formatter(b'debuginstall', pycompat.byteskwargs(opts))
    fm.startitem()

    # encoding might be unknown or wrong. don't translate these messages.
    fm.write(b'encoding', b"checking encoding (%s)...\n", encoding.encoding)
    err = None
    try:
        codecs.lookup(pycompat.sysstr(encoding.encoding))
    except LookupError as inst:
        err = stringutil.forcebytestr(inst)
        problems += 1
    fm.condwrite(
        err,
        b'encodingerror',
        b" %s\n (check that your locale is properly set)\n",
        err,
    )

    # Python
    pythonlib = None
    if hasattr(os, '__file__'):
        pythonlib = os.path.dirname(pycompat.fsencode(os.__file__))
    elif getattr(sys, 'oxidized', False):
        pythonlib = pycompat.sysexecutable

    fm.write(
        b'pythonexe',
        _(b"checking Python executable (%s)\n"),
        pycompat.sysexecutable or _(b"unknown"),
    )
    fm.write(
        b'pythonimplementation',
        _(b"checking Python implementation (%s)\n"),
        pycompat.sysbytes(platform.python_implementation()),
    )
    fm.write(
        b'pythonver',
        _(b"checking Python version (%s)\n"),
        (b"%d.%d.%d" % sys.version_info[:3]),
    )
    fm.write(
        b'pythonlib',
        _(b"checking Python lib (%s)...\n"),
        pythonlib or _(b"unknown"),
    )

    try:
        from . import rustext  # pytype: disable=import-error

        rustext.__doc__  # trigger lazy import
    except ImportError:
        rustext = None

    security = set(sslutil.supportedprotocols)
    if sslutil.hassni:
        security.add(b'sni')

    fm.write(
        b'pythonsecurity',
        _(b"checking Python security support (%s)\n"),
        fm.formatlist(sorted(security), name=b'protocol', fmt=b'%s', sep=b','),
    )

    # These are warnings, not errors. So don't increment problem count. This
    # may change in the future.
    if b'tls1.2' not in security:
        fm.plain(
            _(
                b'  TLS 1.2 not supported by Python install; '
                b'network connections lack modern security\n'
            )
        )
    if b'sni' not in security:
        fm.plain(
            _(
                b'  SNI not supported by Python install; may have '
                b'connectivity issues with some servers\n'
            )
        )

    fm.plain(
        _(
            b"checking Rust extensions (%s)\n"
            % (b'missing' if rustext is None else b'installed')
        ),
    )

    # TODO print CA cert info

    # hg version
    hgver = util.version()
    fm.write(
        b'hgver', _(b"checking Mercurial version (%s)\n"), hgver.split(b'+')[0]
    )
    fm.write(
        b'hgverextra',
        _(b"checking Mercurial custom build (%s)\n"),
        b'+'.join(hgver.split(b'+')[1:]),
    )

    # compiled modules
    hgmodules = None
    if hasattr(sys.modules[__name__], '__file__'):
        hgmodules = os.path.dirname(pycompat.fsencode(__file__))
    elif getattr(sys, 'oxidized', False):
        hgmodules = pycompat.sysexecutable

    fm.write(
        b'hgmodulepolicy', _(b"checking module policy (%s)\n"), policy.policy
    )
    fm.write(
        b'hgmodules',
        _(b"checking installed modules (%s)...\n"),
        hgmodules or _(b"unknown"),
    )

    rustandc = policy.policy in (b'rust+c', b'rust+c-allow')
    rustext = rustandc  # for now, that's the only case
    cext = policy.policy in (b'c', b'allow') or rustandc
    nopure = cext or rustext
    if nopure:
        err = None
        try:
            if cext:
                from .cext import (  # pytype: disable=import-error
                    base85,
                    bdiff,
                    mpatch,
                    osutil,
                )

                # quiet pyflakes
                dir(bdiff), dir(mpatch), dir(base85), dir(osutil)
            if rustext:
                from .rustext import (  # pytype: disable=import-error
                    ancestor,
                    dirstate,
                )

                dir(ancestor), dir(dirstate)  # quiet pyflakes
        except Exception as inst:
            err = stringutil.forcebytestr(inst)
            problems += 1
        fm.condwrite(err, b'extensionserror', b" %s\n", err)

    compengines = util.compengines._engines.values()
    fm.write(
        b'compengines',
        _(b'checking registered compression engines (%s)\n'),
        fm.formatlist(
            sorted(e.name() for e in compengines),
            name=b'compengine',
            fmt=b'%s',
            sep=b', ',
        ),
    )
    fm.write(
        b'compenginesavail',
        _(b'checking available compression engines (%s)\n'),
        fm.formatlist(
            sorted(e.name() for e in compengines if e.available()),
            name=b'compengine',
            fmt=b'%s',
            sep=b', ',
        ),
    )
    wirecompengines = compression.compengines.supportedwireengines(
        compression.SERVERROLE
    )
    fm.write(
        b'compenginesserver',
        _(
            b'checking available compression engines '
            b'for wire protocol (%s)\n'
        ),
        fm.formatlist(
            [e.name() for e in wirecompengines if e.wireprotosupport()],
            name=b'compengine',
            fmt=b'%s',
            sep=b', ',
        ),
    )
    re2 = b'missing'
    if util.has_re2():
        re2 = b'available'
    fm.plain(_(b'checking "re2" regexp engine (%s)\n') % re2)
    fm.data(re2=bool(util._re2))

    # templates
    p = templater.templatedir()
    fm.write(b'templatedirs', b'checking templates (%s)...\n', p or b'')
    fm.condwrite(not p, b'', _(b" no template directories found\n"))
    if p:
        (m, fp) = templater.try_open_template(b"map-cmdline.default")
        if m:
            # template found, check if it is working
            err = None
            try:
                templater.templater.frommapfile(m)
            except Exception as inst:
                err = stringutil.forcebytestr(inst)
                p = None
            fm.condwrite(err, b'defaulttemplateerror', b" %s\n", err)
        else:
            p = None
        fm.condwrite(
            p, b'defaulttemplate', _(b"checking default template (%s)\n"), m
        )
        fm.condwrite(
            not m,
            b'defaulttemplatenotfound',
            _(b" template '%s' not found\n"),
            b"default",
        )
    if not p:
        problems += 1
    fm.condwrite(
        not p, b'', _(b" (templates seem to have been installed incorrectly)\n")
    )

    # editor
    editor = ui.geteditor()
    editor = util.expandpath(editor)
    editorbin = procutil.shellsplit(editor)[0]
    fm.write(b'editor', _(b"checking commit editor... (%s)\n"), editorbin)
    cmdpath = procutil.findexe(editorbin)
    fm.condwrite(
        not cmdpath and editor == b'vi',
        b'vinotfound',
        _(
            b" No commit editor set and can't find %s in PATH\n"
            b" (specify a commit editor in your configuration"
            b" file)\n"
        ),
        not cmdpath and editor == b'vi' and editorbin,
    )
    fm.condwrite(
        not cmdpath and editor != b'vi',
        b'editornotfound',
        _(
            b" Can't find editor '%s' in PATH\n"
            b" (specify a commit editor in your configuration"
            b" file)\n"
        ),
        not cmdpath and editorbin,
    )
    if not cmdpath and editor != b'vi':
        problems += 1

    # check username
    username = None
    err = None
    try:
        username = ui.username()
    except error.Abort as e:
        err = e.message
        problems += 1

    fm.condwrite(
        username, b'username', _(b"checking username (%s)\n"), username
    )
    fm.condwrite(
        err,
        b'usernameerror',
        _(
            b"checking username...\n %s\n"
            b" (specify a username in your configuration file)\n"
        ),
        err,
    )

    for name, mod in extensions.extensions():
        handler = getattr(mod, 'debuginstall', None)
        if handler is not None:
            problems += handler(ui, fm)

    fm.condwrite(not problems, b'', _(b"no problems detected\n"))
    if not problems:
        fm.data(problems=problems)
    fm.condwrite(
        problems,
        b'problems',
        _(b"%d problems detected, please check your install!\n"),
        problems,
    )
    fm.end()

    return problems


@command(b'debugknown', [], _(b'REPO ID...'), norepo=True)
def debugknown(ui, repopath, *ids, **opts):
    """test whether node ids are known to a repo

    Every ID must be a full-length hex node id string. Returns a list of 0s
    and 1s indicating unknown/known.
    """
    repo = hg.peer(ui, pycompat.byteskwargs(opts), repopath)
    if not repo.capable(b'known'):
        raise error.Abort(b"known() not supported by target repository")
    flags = repo.known([bin(s) for s in ids])
    ui.write(b"%s\n" % (b"".join([f and b"1" or b"0" for f in flags])))


@command(b'debuglabelcomplete', [], _(b'LABEL...'))
def debuglabelcomplete(ui, repo, *args):
    '''backwards compatibility with old bash completion scripts (DEPRECATED)'''
    debugnamecomplete(ui, repo, *args)


@command(
    b'debuglocks',
    [
        (b'L', b'force-free-lock', None, _(b'free the store lock (DANGEROUS)')),
        (
            b'W',
            b'force-free-wlock',
            None,
            _(b'free the working state lock (DANGEROUS)'),
        ),
        (b's', b'set-lock', None, _(b'set the store lock until stopped')),
        (
            b'S',
            b'set-wlock',
            None,
            _(b'set the working state lock until stopped'),
        ),
    ],
    _(b'[OPTION]...'),
)
def debuglocks(ui, repo, **opts):
    """show or modify state of locks

    By default, this command will show which locks are held. This
    includes the user and process holding the lock, the amount of time
    the lock has been held, and the machine name where the process is
    running if it's not local.

    Locks protect the integrity of Mercurial's data, so should be
    treated with care. System crashes or other interruptions may cause
    locks to not be properly released, though Mercurial will usually
    detect and remove such stale locks automatically.

    However, detecting stale locks may not always be possible (for
    instance, on a shared filesystem). Removing locks may also be
    blocked by filesystem permissions.

    Setting a lock will prevent other commands from changing the data.
    The command will wait until an interruption (SIGINT, SIGTERM, ...) occurs.
    The set locks are removed when the command exits.

    Returns 0 if no locks are held.

    """

    if opts.get('force_free_lock'):
        repo.svfs.tryunlink(b'lock')
    if opts.get('force_free_wlock'):
        repo.vfs.tryunlink(b'wlock')
    if opts.get('force_free_lock') or opts.get('force_free_wlock'):
        return 0

    locks = []
    try:
        if opts.get('set_wlock'):
            try:
                locks.append(repo.wlock(False))
            except error.LockHeld:
                raise error.Abort(_(b'wlock is already held'))
        if opts.get('set_lock'):
            try:
                locks.append(repo.lock(False))
            except error.LockHeld:
                raise error.Abort(_(b'lock is already held'))
        if len(locks):
            try:
                if ui.interactive():
                    prompt = _(b"ready to release the lock (y)? $$ &Yes")
                    ui.promptchoice(prompt)
                else:
                    msg = b"%d locks held, waiting for signal\n"
                    msg %= len(locks)
                    ui.status(msg)
                    while True:  # XXX wait for a signal
                        time.sleep(0.1)
            except KeyboardInterrupt:
                msg = b"signal-received releasing locks\n"
                ui.status(msg)
            return 0
    finally:
        release(*locks)

    now = time.time()
    held = 0

    def report(vfs, name, method):
        # this causes stale locks to get reaped for more accurate reporting
        try:
            l = method(False)
        except error.LockHeld:
            l = None

        if l:
            l.release()
        else:
            try:
                st = vfs.lstat(name)
                age = now - st[stat.ST_MTIME]
                user = util.username(st.st_uid)
                locker = vfs.readlock(name)
                if b":" in locker:
                    host, pid = locker.split(b':')
                    if host == socket.gethostname():
                        locker = b'user %s, process %s' % (user or b'None', pid)
                    else:
                        locker = b'user %s, process %s, host %s' % (
                            user or b'None',
                            pid,
                            host,
                        )
                ui.writenoi18n(b"%-6s %s (%ds)\n" % (name + b":", locker, age))
                return 1
            except FileNotFoundError:
                pass

        ui.writenoi18n(b"%-6s free\n" % (name + b":"))
        return 0

    held += report(repo.svfs, b"lock", repo.lock)
    held += report(repo.vfs, b"wlock", repo.wlock)

    return held


@command(
    b'debugmanifestfulltextcache',
    [
        (b'', b'clear', False, _(b'clear the cache')),
        (
            b'a',
            b'add',
            [],
            _(b'add the given manifest nodes to the cache'),
            _(b'NODE'),
        ),
    ],
    b'',
)
def debugmanifestfulltextcache(ui, repo, add=(), **opts):
    """show, clear or amend the contents of the manifest fulltext cache"""

    def getcache():
        r = repo.manifestlog.getstorage(b'')
        try:
            return r._fulltextcache
        except AttributeError:
            msg = _(
                b"Current revlog implementation doesn't appear to have a "
                b"manifest fulltext cache\n"
            )
            raise error.Abort(msg)

    if opts.get('clear'):
        with repo.wlock():
            cache = getcache()
            cache.clear(clear_persisted_data=True)
            return

    if add:
        with repo.wlock():
            m = repo.manifestlog
            store = m.getstorage(b'')
            for n in add:
                try:
                    manifest = m[store.lookup(n)]
                except error.LookupError as e:
                    raise error.Abort(
                        bytes(e), hint=b"Check your manifest node id"
                    )
                manifest.read()  # stores revisision in cache too
            return

    cache = getcache()
    if not len(cache):
        ui.write(_(b'cache empty\n'))
    else:
        ui.write(
            _(
                b'cache contains %d manifest entries, in order of most to '
                b'least recent:\n'
            )
            % (len(cache),)
        )
        totalsize = 0
        for nodeid in cache:
            # Use cache.get to not update the LRU order
            data = cache.peek(nodeid)
            size = len(data)
            totalsize += size + 24  # 20 bytes nodeid, 4 bytes size
            ui.write(
                _(b'id: %s, size %s\n') % (hex(nodeid), util.bytecount(size))
            )
        ondisk = cache._opener.stat(b'manifestfulltextcache').st_size
        ui.write(
            _(b'total cache data size %s, on-disk %s\n')
            % (util.bytecount(totalsize), util.bytecount(ondisk))
        )


@command(b'debugmergestate', [] + cmdutil.templateopts, b'')
def debugmergestate(ui, repo, *args, **opts):
    """print merge state

    Use --verbose to print out information about whether v1 or v2 merge state
    was chosen."""

    if ui.verbose:
        ms = mergestatemod.mergestate(repo)

        # sort so that reasonable information is on top
        v1records = ms._readrecordsv1()
        v2records = ms._readrecordsv2()

        if not v1records and not v2records:
            pass
        elif not v2records:
            ui.writenoi18n(b'no version 2 merge state\n')
        elif ms._v1v2match(v1records, v2records):
            ui.writenoi18n(b'v1 and v2 states match: using v2\n')
        else:
            ui.writenoi18n(b'v1 and v2 states mismatch: using v1\n')

    if not opts['template']:
        opts['template'] = (
            b'{if(commits, "", "no merge state found\n")}'
            b'{commits % "{name}{if(label, " ({label})")}: {node}\n"}'
            b'{files % "file: {path} (state \\"{state}\\")\n'
            b'{if(local_path, "'
            b'  local path: {local_path} (hash {local_key}, flags \\"{local_flags}\\")\n'
            b'  ancestor path: {ancestor_path} (node {ancestor_node})\n'
            b'  other path: {other_path} (node {other_node})\n'
            b'")}'
            b'{if(rename_side, "'
            b'  rename side: {rename_side}\n'
            b'  renamed path: {renamed_path}\n'
            b'")}'
            b'{extras % "  extra: {key} = {value}\n"}'
            b'"}'
            b'{extras % "extra: {file} ({key} = {value})\n"}'
        )

    ms = mergestatemod.mergestate.read(repo)

    fm = ui.formatter(b'debugmergestate', pycompat.byteskwargs(opts))
    fm.startitem()

    fm_commits = fm.nested(b'commits')
    if ms.active():
        for name, node, label_index in (
            (b'local', ms.local, 0),
            (b'other', ms.other, 1),
        ):
            fm_commits.startitem()
            fm_commits.data(name=name)
            fm_commits.data(node=hex(node))
            if ms._labels and len(ms._labels) > label_index:
                fm_commits.data(label=ms._labels[label_index])
    fm_commits.end()

    fm_files = fm.nested(b'files')
    if ms.active():
        for f in ms:
            fm_files.startitem()
            fm_files.data(path=f)
            state = ms._state[f]
            fm_files.data(state=state[0])
            if state[0] in (
                mergestatemod.MERGE_RECORD_UNRESOLVED,
                mergestatemod.MERGE_RECORD_RESOLVED,
            ):
                fm_files.data(local_key=state[1])
                fm_files.data(local_path=state[2])
                fm_files.data(ancestor_path=state[3])
                fm_files.data(ancestor_node=state[4])
                fm_files.data(other_path=state[5])
                fm_files.data(other_node=state[6])
                fm_files.data(local_flags=state[7])
            elif state[0] in (
                mergestatemod.MERGE_RECORD_UNRESOLVED_PATH,
                mergestatemod.MERGE_RECORD_RESOLVED_PATH,
            ):
                fm_files.data(renamed_path=state[1])
                fm_files.data(rename_side=state[2])
            fm_extras = fm_files.nested(b'extras')
            for k, v in sorted(ms.extras(f).items()):
                fm_extras.startitem()
                fm_extras.data(key=k)
                fm_extras.data(value=v)
            fm_extras.end()

    fm_files.end()

    fm_extras = fm.nested(b'extras')
    for f, d in sorted(ms.allextras().items()):
        if f in ms:
            # If file is in mergestate, we have already processed it's extras
            continue
        for k, v in d.items():
            fm_extras.startitem()
            fm_extras.data(file=f)
            fm_extras.data(key=k)
            fm_extras.data(value=v)
    fm_extras.end()

    fm.end()


@command(b'debugnamecomplete', [], _(b'NAME...'))
def debugnamecomplete(ui, repo, *args):
    '''complete "names" - tags, open branch names, bookmark names'''

    names = set()
    # since we previously only listed open branches, we will handle that
    # specially (after this for loop)
    for name, ns in repo.names.items():
        if name != b'branches':
            names.update(ns.listnames(repo))
    names.update(
        tag
        for (tag, heads, tip, closed) in repo.branchmap().iterbranches()
        if not closed
    )
    completions = set()
    if not args:
        args = [b'']
    for a in args:
        completions.update(n for n in names if n.startswith(a))
    ui.write(b'\n'.join(sorted(completions)))
    ui.write(b'\n')


@command(
    b'debugnodemap',
    (
        cmdutil.debugrevlogopts
        + [
            (
                b'',
                b'dump-new',
                False,
                _(b'write a (new) persistent binary nodemap on stdout'),
            ),
            (b'', b'dump-disk', False, _(b'dump on-disk data on stdout')),
            (
                b'',
                b'check',
                False,
                _(b'check that the data on disk data are correct.'),
            ),
            (
                b'',
                b'metadata',
                False,
                _(b'display the on disk meta data for the nodemap'),
            ),
        ]
    ),
    _(b'-c|-m|FILE'),
)
def debugnodemap(ui, repo, file_=None, **opts):
    """write and inspect on disk nodemap"""
    if opts.get('changelog') or opts.get('manifest') or opts.get('dir'):
        if file_ is not None:
            raise error.InputError(
                _(b'cannot specify a file with other arguments')
            )
    elif file_ is None:
        opts['changelog'] = True
    r = cmdutil.openstorage(
        repo.unfiltered(), b'debugnodemap', file_, pycompat.byteskwargs(opts)
    )
    if isinstance(r, (manifest.manifestrevlog, filelog.filelog)):
        r = r._revlog
    if opts['dump_new']:
        if hasattr(r.index, "nodemap_data_all"):
            data = r.index.nodemap_data_all()
        else:
            data = nodemap.persistent_data(r.index)
        ui.write(data)
    elif opts['dump_disk']:
        nm_data = nodemap.persisted_data(r)
        if nm_data is not None:
            docket, data = nm_data
            ui.write(data[:])
    elif opts['check']:
        nm_data = nodemap.persisted_data(r)
        if nm_data is not None:
            docket, data = nm_data
            return nodemap.check_data(ui, r.index, data)
    elif opts['metadata']:
        nm_data = nodemap.persisted_data(r)
        if nm_data is not None:
            docket, data = nm_data
            ui.write((b"uid: %s\n") % docket.uid)
            ui.write((b"tip-rev: %d\n") % docket.tip_rev)
            ui.write((b"tip-node: %s\n") % hex(docket.tip_node))
            ui.write((b"data-length: %d\n") % docket.data_length)
            ui.write((b"data-unused: %d\n") % docket.data_unused)
            unused_perc = docket.data_unused * 100.0 / docket.data_length
            ui.write((b"data-unused: %2.3f%%\n") % unused_perc)


@command(
    b'debugobsolete',
    [
        (b'', b'flags', 0, _(b'markers flag')),
        (
            b'',
            b'record-parents',
            False,
            _(b'record parent information for the precursor'),
        ),
        (b'r', b'rev', [], _(b'display markers relevant to REV')),
        (
            b'',
            b'exclusive',
            False,
            _(b'restrict display to markers only relevant to REV'),
        ),
        (b'', b'index', False, _(b'display index of the marker')),
        (b'', b'delete', [], _(b'delete markers specified by indices')),
    ]
    + cmdutil.commitopts2
    + cmdutil.formatteropts,
    _(b'[OBSOLETED [REPLACEMENT ...]]'),
)
def debugobsolete(ui, repo, precursor=None, *successors, **opts):
    """create arbitrary obsolete marker

    With no arguments, displays the list of obsolescence markers."""

    def parsenodeid(s):
        try:
            # We do not use revsingle/revrange functions here to accept
            # arbitrary node identifiers, possibly not present in the
            # local repository.
            n = bin(s)
            if len(n) != repo.nodeconstants.nodelen:
                raise ValueError
            return n
        except ValueError:
            raise error.InputError(
                b'changeset references must be full hexadecimal '
                b'node identifiers'
            )

    if opts.get('delete'):
        indices = []
        for v in opts.get('delete'):
            try:
                indices.append(int(v))
            except ValueError:
                raise error.InputError(
                    _(b'invalid index value: %r') % v,
                    hint=_(b'use integers for indices'),
                )

        if repo.currenttransaction():
            raise error.Abort(
                _(b'cannot delete obsmarkers in the middle of transaction.')
            )

        with repo.lock():
            n = repair.deleteobsmarkers(repo.obsstore, indices)
            ui.write(_(b'deleted %i obsolescence markers\n') % n)

        return

    if precursor is not None:
        if opts['rev']:
            raise error.InputError(
                b'cannot select revision when creating marker'
            )
        metadata = {}
        metadata[b'user'] = encoding.fromlocal(opts['user'] or ui.username())
        succs = tuple(parsenodeid(succ) for succ in successors)
        l = repo.lock()
        try:
            tr = repo.transaction(b'debugobsolete')
            try:
                date = opts.get('date')
                if date:
                    date = dateutil.parsedate(date)
                else:
                    date = None
                prec = parsenodeid(precursor)
                parents = None
                if opts['record_parents']:
                    if prec not in repo.unfiltered():
                        raise error.Abort(
                            b'cannot used --record-parents on '
                            b'unknown changesets'
                        )
                    parents = repo.unfiltered()[prec].parents()
                    parents = tuple(p.node() for p in parents)
                repo.obsstore.create(
                    tr,
                    prec,
                    succs,
                    opts['flags'],
                    parents=parents,
                    date=date,
                    metadata=metadata,
                    ui=ui,
                )
                tr.close()
            except ValueError as exc:
                raise error.Abort(
                    _(b'bad obsmarker input: %s') % stringutil.forcebytestr(exc)
                )
            finally:
                tr.release()
        finally:
            l.release()
    else:
        if opts['rev']:
            revs = logcmdutil.revrange(repo, opts['rev'])
            nodes = [repo[r].node() for r in revs]
            markers = list(
                obsutil.getmarkers(
                    repo, nodes=nodes, exclusive=opts['exclusive']
                )
            )
            markers.sort(key=lambda x: x._data)
        else:
            markers = obsutil.getmarkers(repo)

        markerstoiter = markers
        isrelevant = lambda m: True
        if opts.get('rev') and opts.get('index'):
            markerstoiter = obsutil.getmarkers(repo)
            markerset = set(markers)
            isrelevant = lambda m: m in markerset

        fm = ui.formatter(b'debugobsolete', pycompat.byteskwargs(opts))
        for i, m in enumerate(markerstoiter):
            if not isrelevant(m):
                # marker can be irrelevant when we're iterating over a set
                # of markers (markerstoiter) which is bigger than the set
                # of markers we want to display (markers)
                # this can happen if both --index and --rev options are
                # provided and thus we need to iterate over all of the markers
                # to get the correct indices, but only display the ones that
                # are relevant to --rev value
                continue
            fm.startitem()
            ind = i if opts.get('index') else None
            cmdutil.showmarker(fm, m, index=ind)
        fm.end()


@command(
    b'debugp1copies',
    [(b'r', b'rev', b'', _(b'revision to debug'), _(b'REV'))],
    _(b'[-r REV]'),
)
def debugp1copies(ui, repo, **opts):
    """dump copy information compared to p1"""

    ctx = scmutil.revsingle(repo, opts.get('rev'), default=None)
    for dst, src in ctx.p1copies().items():
        ui.write(b'%s -> %s\n' % (src, dst))


@command(
    b'debugp2copies',
    [(b'r', b'rev', b'', _(b'revision to debug'), _(b'REV'))],
    _(b'[-r REV]'),
)
def debugp2copies(ui, repo, **opts):
    """dump copy information compared to p2"""

    ctx = scmutil.revsingle(repo, opts.get('rev'), default=None)
    for dst, src in ctx.p2copies().items():
        ui.write(b'%s -> %s\n' % (src, dst))


@command(
    b'debugpathcomplete',
    [
        (b'f', b'full', None, _(b'complete an entire path')),
        (b'n', b'normal', None, _(b'show only normal files')),
        (b'a', b'added', None, _(b'show only added files')),
        (b'r', b'removed', None, _(b'show only removed files')),
    ],
    _(b'FILESPEC...'),
)
def debugpathcomplete(ui, repo, *specs, **opts):
    """complete part or all of a tracked path

    This command supports shells that offer path name completion. It
    currently completes only files already known to the dirstate.

    Completion extends only to the next path segment unless
    --full is specified, in which case entire paths are used."""

    def complete(path, acceptable):
        dirstate = repo.dirstate
        spec = os.path.normpath(os.path.join(encoding.getcwd(), path))
        rootdir = repo.root + pycompat.ossep
        if spec != repo.root and not spec.startswith(rootdir):
            return [], []
        if os.path.isdir(spec):
            spec += b'/'
        spec = spec[len(rootdir) :]
        fixpaths = pycompat.ossep != b'/'
        if fixpaths:
            spec = spec.replace(pycompat.ossep, b'/')
        speclen = len(spec)
        fullpaths = opts['full']
        files, dirs = set(), set()
        adddir, addfile = dirs.add, files.add
        for f, st in dirstate.items():
            if f.startswith(spec) and st.state in acceptable:
                if fixpaths:
                    f = f.replace(b'/', pycompat.ossep)
                if fullpaths:
                    addfile(f)
                    continue
                s = f.find(pycompat.ossep, speclen)
                if s >= 0:
                    adddir(f[:s])
                else:
                    addfile(f)
        return files, dirs

    acceptable = b''
    if opts['normal']:
        acceptable += b'nm'
    if opts['added']:
        acceptable += b'a'
    if opts['removed']:
        acceptable += b'r'
    cwd = repo.getcwd()
    if not specs:
        specs = [b'.']

    files, dirs = set(), set()
    for spec in specs:
        f, d = complete(spec, acceptable or b'nmar')
        files.update(f)
        dirs.update(d)
    files.update(dirs)
    ui.write(b'\n'.join(repo.pathto(p, cwd) for p in sorted(files)))
    ui.write(b'\n')


@command(
    b'debugpathcopies',
    cmdutil.walkopts,
    b'hg debugpathcopies REV1 REV2 [FILE]',
    inferrepo=True,
)
def debugpathcopies(ui, repo, rev1, rev2, *pats, **opts):
    """show copies between two revisions"""
    ctx1 = scmutil.revsingle(repo, rev1)
    ctx2 = scmutil.revsingle(repo, rev2)
    m = scmutil.match(ctx1, pats, opts)
    for dst, src in sorted(copies.pathcopies(ctx1, ctx2, m).items()):
        ui.write(b'%s -> %s\n' % (src, dst))


@command(b'debugpeer', [], _(b'PATH'), norepo=True)
def debugpeer(ui, path):
    """establish a connection to a peer repository"""
    # Always enable peer request logging. Requires --debug to display
    # though.
    overrides = {
        (b'devel', b'debug.peer-request'): True,
    }

    with ui.configoverride(overrides):
        peer = hg.peer(ui, {}, path)

        try:
            local = peer.local() is not None
            canpush = peer.canpush()

            ui.write(_(b'url: %s\n') % peer.url())
            ui.write(_(b'local: %s\n') % (_(b'yes') if local else _(b'no')))
            ui.write(
                _(b'pushable: %s\n') % (_(b'yes') if canpush else _(b'no'))
            )
        finally:
            peer.close()


@command(
    b'debugpickmergetool',
    [
        (b'r', b'rev', b'', _(b'check for files in this revision'), _(b'REV')),
        (b'', b'changedelete', None, _(b'emulate merging change and delete')),
    ]
    + cmdutil.walkopts
    + cmdutil.mergetoolopts,
    _(b'[PATTERN]...'),
    inferrepo=True,
)
def debugpickmergetool(ui, repo, *pats, **opts):
    """examine which merge tool is chosen for specified file

    As described in :hg:`help merge-tools`, Mercurial examines
    configurations below in this order to decide which merge tool is
    chosen for specified file.

    1. ``--tool`` option
    2. ``HGMERGE`` environment variable
    3. configurations in ``merge-patterns`` section
    4. configuration of ``ui.merge``
    5. configurations in ``merge-tools`` section
    6. ``hgmerge`` tool (for historical reason only)
    7. default tool for fallback (``:merge`` or ``:prompt``)

    This command writes out examination result in the style below::

        FILE = MERGETOOL

    By default, all files known in the first parent context of the
    working directory are examined. Use file patterns and/or -I/-X
    options to limit target files. -r/--rev is also useful to examine
    files in another context without actual updating to it.

    With --debug, this command shows warning messages while matching
    against ``merge-patterns`` and so on, too. It is recommended to
    use this option with explicit file patterns and/or -I/-X options,
    because this option increases amount of output per file according
    to configurations in hgrc.

    With -v/--verbose, this command shows configurations below at
    first (only if specified).

    - ``--tool`` option
    - ``HGMERGE`` environment variable
    - configuration of ``ui.merge``

    If merge tool is chosen before matching against
    ``merge-patterns``, this command can't show any helpful
    information, even with --debug. In such case, information above is
    useful to know why a merge tool is chosen.
    """
    overrides = {}
    if opts['tool']:
        overrides[(b'ui', b'forcemerge')] = opts['tool']
        ui.notenoi18n(b'with --tool %r\n' % (pycompat.bytestr(opts['tool'])))

    with ui.configoverride(overrides, b'debugmergepatterns'):
        hgmerge = encoding.environ.get(b"HGMERGE")
        if hgmerge is not None:
            ui.notenoi18n(b'with HGMERGE=%r\n' % (pycompat.bytestr(hgmerge)))
        uimerge = ui.config(b"ui", b"merge")
        if uimerge:
            ui.notenoi18n(b'with ui.merge=%r\n' % (pycompat.bytestr(uimerge)))

        ctx = scmutil.revsingle(repo, opts.get('rev'))
        m = scmutil.match(ctx, pats, pycompat.byteskwargs(opts))
        changedelete = opts['changedelete']
        for path in ctx.walk(m):
            fctx = ctx[path]
            with ui.silent(
                error=True
            ) if not ui.debugflag else util.nullcontextmanager():
                tool, toolpath = filemerge._picktool(
                    repo,
                    ui,
                    path,
                    fctx.isbinary(),
                    b'l' in fctx.flags(),
                    changedelete,
                )
            ui.write(b'%s = %s\n' % (path, tool))


@command(b'debugpushkey', [], _(b'REPO NAMESPACE [KEY OLD NEW]'), norepo=True)
def debugpushkey(ui, repopath, namespace, *keyinfo, **opts):
    """access the pushkey key/value protocol

    With two args, list the keys in the given namespace.

    With five args, set a key to new if it currently is set to old.
    Reports success or failure.
    """

    target = hg.peer(ui, {}, repopath)
    try:
        if keyinfo:
            key, old, new = keyinfo
            with target.commandexecutor() as e:
                r = e.callcommand(
                    b'pushkey',
                    {
                        b'namespace': namespace,
                        b'key': key,
                        b'old': old,
                        b'new': new,
                    },
                ).result()

            ui.status(pycompat.bytestr(r) + b'\n')
            return not r
        else:
            for k, v in sorted(target.listkeys(namespace).items()):
                ui.write(
                    b"%s\t%s\n"
                    % (stringutil.escapestr(k), stringutil.escapestr(v))
                )
    finally:
        target.close()


@command(b'debugpvec', [], _(b'A B'))
def debugpvec(ui, repo, a, b=None):
    ca = scmutil.revsingle(repo, a)
    cb = scmutil.revsingle(repo, b)
    pa = pvec.ctxpvec(ca)
    pb = pvec.ctxpvec(cb)
    if pa == pb:
        rel = b"="
    elif pa > pb:
        rel = b">"
    elif pa < pb:
        rel = b"<"
    elif pa | pb:
        rel = b"|"
    ui.write(_(b"a: %s\n") % pa)
    ui.write(_(b"b: %s\n") % pb)
    ui.write(_(b"depth(a): %d depth(b): %d\n") % (pa._depth, pb._depth))
    ui.write(
        _(b"delta: %d hdist: %d distance: %d relation: %s\n")
        % (
            abs(pa._depth - pb._depth),
            pvec._hamming(pa._vec, pb._vec),
            pa.distance(pb),
            rel,
        )
    )


@command(
    b'debugrebuilddirstate|debugrebuildstate',
    [
        (b'r', b'rev', b'', _(b'revision to rebuild to'), _(b'REV')),
        (
            b'',
            b'minimal',
            None,
            _(
                b'only rebuild files that are inconsistent with '
                b'the working copy parent'
            ),
        ),
    ],
    _(b'[-r REV]'),
)
def debugrebuilddirstate(ui, repo, rev, **opts):
    """rebuild the dirstate as it would look like for the given revision

    If no revision is specified the first current parent will be used.

    The dirstate will be set to the files of the given revision.
    The actual working directory content or existing dirstate
    information such as adds or removes is not considered.

    ``minimal`` will only rebuild the dirstate status for files that claim to be
    tracked but are not in the parent manifest, or that exist in the parent
    manifest but are not in the dirstate. It will not change adds, removes, or
    modified files that are in the working copy parent.

    One use of this command is to make the next :hg:`status` invocation
    check the actual file content.
    """
    ctx = scmutil.revsingle(repo, rev)
    with repo.wlock():
        if repo.currenttransaction() is not None:
            msg = b'rebuild the dirstate outside of a transaction'
            raise error.ProgrammingError(msg)
        dirstate = repo.dirstate
        changedfiles = None
        # See command doc for what minimal does.
        if opts.get('minimal'):
            manifestfiles = set(ctx.manifest().keys())
            dirstatefiles = set(dirstate)
            manifestonly = manifestfiles - dirstatefiles
            dsonly = dirstatefiles - manifestfiles
            dsnotadded = {f for f in dsonly if not dirstate.get_entry(f).added}
            changedfiles = manifestonly | dsnotadded

        with dirstate.changing_parents(repo):
            dirstate.rebuild(ctx.node(), ctx.manifest(), changedfiles)


@command(
    b'debugrebuildfncache',
    [
        (
            b'',
            b'only-data',
            False,
            _(b'only look for wrong .d files (much faster)'),
        )
    ],
    b'',
)
def debugrebuildfncache(ui, repo, **opts):
    """rebuild the fncache file"""
    repair.rebuildfncache(ui, repo, opts.get("only_data"))


@command(
    b'debugrename',
    [(b'r', b'rev', b'', _(b'revision to debug'), _(b'REV'))],
    _(b'[-r REV] [FILE]...'),
)
def debugrename(ui, repo, *pats, **opts):
    """dump rename information"""

    ctx = scmutil.revsingle(repo, opts.get('rev'))
    m = scmutil.match(ctx, pats, pycompat.byteskwargs(opts))
    for abs in ctx.walk(m):
        fctx = ctx[abs]
        o = fctx.filelog().renamed(fctx.filenode())
        rel = repo.pathto(abs)
        if o:
            ui.write(_(b"%s renamed from %s:%s\n") % (rel, o[0], hex(o[1])))
        else:
            ui.write(_(b"%s not renamed\n") % rel)


@command(b'debugrequires|debugrequirements', [], b'')
def debugrequirements(ui, repo):
    """print the current repo requirements"""
    for r in sorted(repo.requirements):
        ui.write(b"%s\n" % r)


@command(
    b'debugrevlog',
    cmdutil.debugrevlogopts + [(b'd', b'dump', False, _(b'dump index data'))],
    _(b'-c|-m|FILE'),
    optionalrepo=True,
)
def debugrevlog(ui, repo, file_=None, **opts):
    """show data and statistics about a revlog"""
    r = cmdutil.openrevlog(
        repo, b'debugrevlog', file_, pycompat.byteskwargs(opts)
    )

    if opts.get("dump"):
        revlog_debug.dump(ui, r)
    else:
        revlog_debug.debug_revlog(ui, r)
    return 0


@command(
    b'debugrevlogindex',
    cmdutil.debugrevlogopts
    + [(b'f', b'format', 0, _(b'revlog format'), _(b'FORMAT'))],
    _(b'[-f FORMAT] -c|-m|FILE'),
    optionalrepo=True,
)
def debugrevlogindex(ui, repo, file_=None, **opts):
    """dump the contents of a revlog index"""
    r = cmdutil.openrevlog(
        repo, b'debugrevlogindex', file_, pycompat.byteskwargs(opts)
    )
    format = opts.get('format', 0)
    if format not in (0, 1):
        raise error.Abort(_(b"unknown format %d") % format)

    if ui.debugflag:
        shortfn = hex
    else:
        shortfn = short

    # There might not be anything in r, so have a sane default
    idlen = 12
    for i in r:
        idlen = len(shortfn(r.node(i)))
        break

    if format == 0:
        if ui.verbose:
            ui.writenoi18n(
                b"   rev    offset  length linkrev %s %s p2\n"
                % (b"nodeid".ljust(idlen), b"p1".ljust(idlen))
            )
        else:
            ui.writenoi18n(
                b"   rev linkrev %s %s p2\n"
                % (b"nodeid".ljust(idlen), b"p1".ljust(idlen))
            )
    elif format == 1:
        if ui.verbose:
            ui.writenoi18n(
                (
                    b"   rev flag   offset   length     size   link     p1"
                    b"     p2 %s\n"
                )
                % b"nodeid".rjust(idlen)
            )
        else:
            ui.writenoi18n(
                b"   rev flag     size   link     p1     p2 %s\n"
                % b"nodeid".rjust(idlen)
            )

    for i in r:
        node = r.node(i)
        if format == 0:
            try:
                pp = r.parents(node)
            except Exception:
                pp = [repo.nullid, repo.nullid]
            if ui.verbose:
                ui.write(
                    b"% 6d % 9d % 7d % 7d %s %s %s\n"
                    % (
                        i,
                        r.start(i),
                        r.length(i),
                        r.linkrev(i),
                        shortfn(node),
                        shortfn(pp[0]),
                        shortfn(pp[1]),
                    )
                )
            else:
                ui.write(
                    b"% 6d % 7d %s %s %s\n"
                    % (
                        i,
                        r.linkrev(i),
                        shortfn(node),
                        shortfn(pp[0]),
                        shortfn(pp[1]),
                    )
                )
        elif format == 1:
            pr = r.parentrevs(i)
            if ui.verbose:
                ui.write(
                    b"% 6d %04x % 8d % 8d % 8d % 6d % 6d % 6d %s\n"
                    % (
                        i,
                        r.flags(i),
                        r.start(i),
                        r.length(i),
                        r.rawsize(i),
                        r.linkrev(i),
                        pr[0],
                        pr[1],
                        shortfn(node),
                    )
                )
            else:
                ui.write(
                    b"% 6d %04x % 8d % 6d % 6d % 6d %s\n"
                    % (
                        i,
                        r.flags(i),
                        r.rawsize(i),
                        r.linkrev(i),
                        pr[0],
                        pr[1],
                        shortfn(node),
                    )
                )


@command(
    b'debugrevspec',
    [
        (
            b'',
            b'optimize',
            None,
            _(b'print parsed tree after optimizing (DEPRECATED)'),
        ),
        (
            b'',
            b'show-revs',
            True,
            _(b'print list of result revisions (default)'),
        ),
        (
            b's',
            b'show-set',
            None,
            _(b'print internal representation of result set'),
        ),
        (
            b'p',
            b'show-stage',
            [],
            _(b'print parsed tree at the given stage'),
            _(b'NAME'),
        ),
        (b'', b'no-optimized', False, _(b'evaluate tree without optimization')),
        (b'', b'verify-optimized', False, _(b'verify optimized result')),
    ],
    b'REVSPEC',
)
def debugrevspec(ui, repo, expr, **opts):
    """parse and apply a revision specification

    Use -p/--show-stage option to print the parsed tree at the given stages.
    Use -p all to print tree at every stage.

    Use --no-show-revs option with -s or -p to print only the set
    representation or the parsed tree respectively.

    Use --verify-optimized to compare the optimized result with the unoptimized
    one. Returns 1 if the optimized result differs.
    """
    aliases = ui.configitems(b'revsetalias')
    stages = [
        (b'parsed', lambda tree: tree),
        (
            b'expanded',
            lambda tree: revsetlang.expandaliases(tree, aliases, ui.warn),
        ),
        (b'concatenated', revsetlang.foldconcat),
        (b'analyzed', revsetlang.analyze),
        (b'optimized', revsetlang.optimize),
    ]
    if opts['no_optimized']:
        stages = stages[:-1]
    if opts['verify_optimized'] and opts['no_optimized']:
        raise error.Abort(
            _(b'cannot use --verify-optimized with --no-optimized')
        )
    stagenames = {n for n, f in stages}

    showalways = set()
    showchanged = set()
    if ui.verbose and not opts['show_stage']:
        # show parsed tree by --verbose (deprecated)
        showalways.add(b'parsed')
        showchanged.update([b'expanded', b'concatenated'])
        if opts['optimize']:
            showalways.add(b'optimized')
    if opts['show_stage'] and opts['optimize']:
        raise error.Abort(_(b'cannot use --optimize with --show-stage'))
    if opts['show_stage'] == [b'all']:
        showalways.update(stagenames)
    else:
        for n in opts['show_stage']:
            if n not in stagenames:
                raise error.Abort(_(b'invalid stage name: %s') % n)
        showalways.update(opts['show_stage'])

    treebystage = {}
    printedtree = None
    tree = revsetlang.parse(expr, lookup=revset.lookupfn(repo))
    for n, f in stages:
        treebystage[n] = tree = f(tree)
        if n in showalways or (n in showchanged and tree != printedtree):
            if opts['show_stage'] or n != b'parsed':
                ui.write(b"* %s:\n" % n)
            ui.write(revsetlang.prettyformat(tree), b"\n")
            printedtree = tree

    if opts['verify_optimized']:
        arevs = revset.makematcher(treebystage[b'analyzed'])(repo)
        brevs = revset.makematcher(treebystage[b'optimized'])(repo)
        if opts['show_set'] or (opts['show_set'] is None and ui.verbose):
            ui.writenoi18n(
                b"* analyzed set:\n", stringutil.prettyrepr(arevs), b"\n"
            )
            ui.writenoi18n(
                b"* optimized set:\n", stringutil.prettyrepr(brevs), b"\n"
            )
        arevs = list(arevs)
        brevs = list(brevs)
        if arevs == brevs:
            return 0
        ui.writenoi18n(b'--- analyzed\n', label=b'diff.file_a')
        ui.writenoi18n(b'+++ optimized\n', label=b'diff.file_b')
        sm = difflib.SequenceMatcher(None, arevs, brevs)
        for tag, alo, ahi, blo, bhi in sm.get_opcodes():
            if tag in ('delete', 'replace'):
                for c in arevs[alo:ahi]:
                    ui.write(b'-%d\n' % c, label=b'diff.deleted')
            if tag in ('insert', 'replace'):
                for c in brevs[blo:bhi]:
                    ui.write(b'+%d\n' % c, label=b'diff.inserted')
            if tag == 'equal':
                for c in arevs[alo:ahi]:
                    ui.write(b' %d\n' % c)
        return 1

    func = revset.makematcher(tree)
    revs = func(repo)
    if opts['show_set'] or (opts['show_set'] is None and ui.verbose):
        ui.writenoi18n(b"* set:\n", stringutil.prettyrepr(revs), b"\n")
    if not opts['show_revs']:
        return
    for c in revs:
        ui.write(b"%d\n" % c)


@command(
    b'debugserve',
    [
        (
            b'',
            b'sshstdio',
            False,
            _(b'run an SSH server bound to process handles'),
        ),
        (b'', b'logiofd', b'', _(b'file descriptor to log server I/O to')),
        (b'', b'logiofile', b'', _(b'file to log server I/O to')),
    ],
    b'',
)
def debugserve(ui, repo, **opts):
    """run a server with advanced settings

    This command is similar to :hg:`serve`. It exists partially as a
    workaround to the fact that ``hg serve --stdio`` must have specific
    arguments for security reasons.
    """
    if not opts['sshstdio']:
        raise error.Abort(_(b'only --sshstdio is currently supported'))

    logfh = None

    if opts['logiofd'] and opts['logiofile']:
        raise error.Abort(_(b'cannot use both --logiofd and --logiofile'))

    if opts['logiofd']:
        # Ideally we would be line buffered. But line buffering in binary
        # mode isn't supported and emits a warning in Python 3.8+. Disabling
        # buffering could have performance impacts. But since this isn't
        # performance critical code, it should be fine.
        try:
            logfh = os.fdopen(int(opts['logiofd']), 'ab', 0)
        except OSError as e:
            if e.errno != errno.ESPIPE:
                raise
            # can't seek a pipe, so `ab` mode fails on py3
            logfh = os.fdopen(int(opts['logiofd']), 'wb', 0)
    elif opts['logiofile']:
        logfh = open(opts['logiofile'], b'ab', 0)

    s = wireprotoserver.sshserver(ui, repo, logfh=logfh)
    s.serve_forever()


@command(b'debugsetparents', [], _(b'REV1 [REV2]'))
def debugsetparents(ui, repo, rev1, rev2=None):
    """manually set the parents of the current working directory (DANGEROUS)

    This command is not what you are looking for and should not be used. Using
    this command will most certainly results in slight corruption of the file
    level histories within your repository. DO NOT USE THIS COMMAND.

    The command updates the p1 and p2 fields in the dirstate, without touching
    anything else. This useful for writing repository conversion tools, but
    should be used with extreme care. For example, neither the working
    directory nor the dirstate is updated, so file statuses may be incorrect
    after running this command. Use it only if you are one of the few people who
    deeply understands both conversion tools and file level histories. If you are
    reading this help, you are not one of those people (most of them sailed west
    from Mithlond anyway).

    So, one more time, DO NOT USE THIS COMMAND.

    Returns 0 on success.
    """

    node1 = scmutil.revsingle(repo, rev1).node()
    node2 = scmutil.revsingle(repo, rev2, b'null').node()

    with repo.wlock():
        repo.setparents(node1, node2)


@command(b'debugsidedata', cmdutil.debugrevlogopts, _(b'-c|-m|FILE REV'))
def debugsidedata(ui, repo, file_, rev=None, **opts):
    """dump the side data for a cl/manifest/file revision

    Use --verbose to dump the sidedata content."""
    if opts.get('changelog') or opts.get('manifest') or opts.get('dir'):
        if rev is not None:
            raise error.InputError(
                _(b'cannot specify a revision with other arguments')
            )
        file_, rev = None, file_
    elif rev is None:
        raise error.InputError(_(b'please specify a revision'))
    r = cmdutil.openstorage(
        repo, b'debugdata', file_, pycompat.byteskwargs(opts)
    )
    r = getattr(r, '_revlog', r)
    try:
        sidedata = r.sidedata(r.lookup(rev))
    except KeyError:
        raise error.Abort(_(b'invalid revision identifier %s') % rev)
    if sidedata:
        sidedata = list(sidedata.items())
        sidedata.sort()
        ui.writenoi18n(b'%d sidedata entries\n' % len(sidedata))
        for key, value in sidedata:
            ui.writenoi18n(b' entry-%04o size %d\n' % (key, len(value)))
            if ui.verbose:
                ui.writenoi18n(b'  %s\n' % stringutil.pprint(value))


@command(b'debugssl', [], b'[SOURCE]', optionalrepo=True)
def debugssl(ui, repo, source=None, **opts):
    """test a secure connection to a server

    This builds the certificate chain for the server on Windows, installing the
    missing intermediates and trusted root via Windows Update if necessary.  It
    does nothing on other platforms.

    If SOURCE is omitted, the 'default' path will be used.  If a URL is given,
    that server is used. See :hg:`help urls` for more information.

    If the update succeeds, retry the original operation.  Otherwise, the cause
    of the SSL error is likely another issue.
    """
    if not pycompat.iswindows:
        raise error.Abort(
            _(b'certificate chain building is only possible on Windows')
        )

    if not source:
        if not repo:
            raise error.Abort(
                _(
                    b"there is no Mercurial repository here, and no "
                    b"server specified"
                )
            )
        source = b"default"

    path = urlutil.get_unique_pull_path_obj(b'debugssl', ui, source)
    url = path.url

    defaultport = {b'https': 443, b'ssh': 22}
    if url.scheme in defaultport:
        try:
            addr = (url.host, int(url.port or defaultport[url.scheme]))
        except ValueError:
            raise error.Abort(_(b"malformed port number in URL"))
    else:
        raise error.Abort(_(b"only https and ssh connections are supported"))

    from . import win32

    s = ssl.wrap_socket(
        socket.socket(),
        ssl_version=ssl.PROTOCOL_TLS,
        cert_reqs=ssl.CERT_NONE,
        ca_certs=None,
    )

    try:
        s.connect(addr)
        cert = s.getpeercert(True)

        ui.status(_(b'checking the certificate chain for %s\n') % url.host)

        complete = win32.checkcertificatechain(cert, build=False)

        if not complete:
            ui.status(_(b'certificate chain is incomplete, updating... '))

            if not win32.checkcertificatechain(cert):
                ui.status(_(b'failed.\n'))
            else:
                ui.status(_(b'done.\n'))
        else:
            ui.status(_(b'full certificate chain is available\n'))
    finally:
        s.close()


@command(
    b'debug::stable-tail-sort',
    [
        (
            b'T',
            b'template',
            b'{rev}\n',
            _(b'display with template'),
            _(b'TEMPLATE'),
        ),
    ],
    b'REV',
)
def debug_stable_tail_sort(ui, repo, revspec, template, **opts):
    """display the stable-tail sort of the ancestors of a given node"""
    rev = logcmdutil.revsingle(repo, revspec).rev()
    cl = repo.changelog

    displayer = logcmdutil.maketemplater(ui, repo, template)
    sorted_revs = stabletailsort._stable_tail_sort_naive(cl, rev)
    for ancestor_rev in sorted_revs:
        displayer.show(repo[ancestor_rev])


@command(
    b'debug::stable-tail-sort-leaps',
    [
        (
            b'T',
            b'template',
            b'{rev}',
            _(b'display with template'),
            _(b'TEMPLATE'),
        ),
        (b's', b'specific', False, _(b'restrict to specific leaps')),
    ],
    b'REV',
)
def debug_stable_tail_sort_leaps(ui, repo, rspec, template, specific, **opts):
    """display the leaps in the stable-tail sort of a node, one per line"""
    rev = logcmdutil.revsingle(repo, rspec).rev()

    if specific:
        get_leaps = stabletailsort._find_specific_leaps_naive
    else:
        get_leaps = stabletailsort._find_all_leaps_naive

    displayer = logcmdutil.maketemplater(ui, repo, template)
    for source, target in get_leaps(repo.changelog, rev):
        displayer.show(repo[source])
        displayer.show(repo[target])
        ui.write(b'\n')


@command(
    b"debugbackupbundle",
    [
        (
            b"",
            b"recover",
            b"",
            b"brings the specified changeset back into the repository",
        )
    ]
    + cmdutil.logopts,
    _(b"hg debugbackupbundle [--recover HASH]"),
)
def debugbackupbundle(ui, repo, *pats, **opts):
    """lists the changesets available in backup bundles

    Without any arguments, this command prints a list of the changesets in each
    backup bundle.

    --recover takes a changeset hash and unbundles the first bundle that
    contains that hash, which puts that changeset back in your repository.

    --verbose will print the entire commit message and the bundle path for that
    backup.
    """
    backups = list(
        filter(
            os.path.isfile, glob.glob(repo.vfs.join(b"strip-backup") + b"/*.hg")
        )
    )
    backups.sort(key=lambda x: os.path.getmtime(x), reverse=True)

    opts["bundle"] = b""
    opts["force"] = None
    limit = logcmdutil.getlimit(pycompat.byteskwargs(opts))

    def display(other, chlist, displayer):
        if opts.get("newest_first"):
            chlist.reverse()
        count = 0
        for n in chlist:
            if limit is not None and count >= limit:
                break
            parents = [
                True for p in other.changelog.parents(n) if p != repo.nullid
            ]
            if opts.get("no_merges") and len(parents) == 2:
                continue
            count += 1
            displayer.show(other[n])

    recovernode = opts.get("recover")
    if recovernode:
        if scmutil.isrevsymbol(repo, recovernode):
            ui.warn(_(b"%s already exists in the repo\n") % recovernode)
            return
    elif backups:
        msg = _(
            b"Recover changesets using: hg debugbackupbundle --recover "
            b"<changeset hash>\n\nAvailable backup changesets:"
        )
        ui.status(msg, label=b"status.removed")
    else:
        ui.status(_(b"no backup changesets found\n"))
        return

    for backup in backups:
        # Much of this is copied from the hg incoming logic
        source = os.path.relpath(backup, encoding.getcwd())
        path = urlutil.get_unique_pull_path_obj(
            b'debugbackupbundle',
            ui,
            source,
        )
        try:
            other = hg.peer(repo, pycompat.byteskwargs(opts), path)
        except error.LookupError as ex:
            msg = _(b"\nwarning: unable to open bundle %s") % path.loc
            hint = _(b"\n(missing parent rev %s)\n") % short(ex.name)
            ui.warn(msg, hint=hint)
            continue
        branches = (path.branch, opts.get('branch', []))
        revs, checkout = hg.addbranchrevs(
            repo, other, branches, opts.get("rev")
        )

        if revs:
            revs = [other.lookup(rev) for rev in revs]

        with ui.silent():
            try:
                other, chlist, cleanupfn = bundlerepo.getremotechanges(
                    ui, repo, other, revs, opts["bundle"], opts["force"]
                )
            except error.LookupError:
                continue

        try:
            if not chlist:
                continue
            if recovernode:
                with repo.lock(), repo.transaction(b"unbundle") as tr:
                    if scmutil.isrevsymbol(other, recovernode):
                        ui.status(_(b"Unbundling %s\n") % (recovernode))
                        f = hg.openpath(ui, path.loc)
                        gen = exchange.readbundle(ui, f, path.loc)
                        if isinstance(gen, bundle2.unbundle20):
                            bundle2.applybundle(
                                repo,
                                gen,
                                tr,
                                source=b"unbundle",
                                url=b"bundle:" + path.loc,
                            )
                        else:
                            gen.apply(repo, b"unbundle", b"bundle:" + path.loc)
                        break
            else:
                backupdate = encoding.strtolocal(
                    time.strftime(
                        "%a %H:%M, %Y-%m-%d",
                        time.localtime(os.path.getmtime(path.loc)),
                    )
                )
                ui.status(b"\n%s\n" % (backupdate.ljust(50)))
                if ui.verbose:
                    ui.status(b"%s%s\n" % (b"bundle:".ljust(13), path.loc))
                else:
                    opts[
                        "template"
                    ] = b"{label('status.modified', node|short)} {desc|firstline}\n"
                displayer = logcmdutil.changesetdisplayer(
                    ui, other, pycompat.byteskwargs(opts), False
                )
                display(other, chlist, displayer)
                displayer.close()
        finally:
            cleanupfn()


@command(
    b'debugsub',
    [(b'r', b'rev', b'', _(b'revision to check'), _(b'REV'))],
    _(b'[-r REV] [REV]'),
)
def debugsub(ui, repo, rev=None):
    ctx = scmutil.revsingle(repo, rev, None)
    for k, v in sorted(ctx.substate.items()):
        ui.writenoi18n(b'path %s\n' % k)
        ui.writenoi18n(b' source   %s\n' % v[0])
        ui.writenoi18n(b' revision %s\n' % v[1])


@command(
    b'debugshell',
    [
        (
            b'c',
            b'command',
            b'',
            _(b'program passed in as a string'),
            _(b'COMMAND'),
        )
    ],
    _(b'[-c COMMAND]'),
    optionalrepo=True,
)
def debugshell(ui, repo, **opts):
    """run an interactive Python interpreter

    The local namespace is provided with a reference to the ui and
    the repo instance (if available).
    """
    import code

    imported_objects = {
        'ui': ui,
        'repo': repo,
    }

    # py2exe disables initialization of the site module, which is responsible
    # for arranging for ``quit()`` to exit the interpreter.  Manually initialize
    # the stuff that site normally does here, so that the interpreter can be
    # quit in a consistent manner, whether run with pyoxidizer, exewrapper.c,
    # py.exe, or py2exe.
    if getattr(sys, "frozen", None) == 'console_exe':
        try:
            import site

            site.setcopyright()
            site.sethelper()
            site.setquit()
        except ImportError:
            site = None  # Keep PyCharm happy

    command = opts.get('command')
    if command:
        compiled = code.compile_command(encoding.strfromlocal(command))
        code.InteractiveInterpreter(locals=imported_objects).runcode(compiled)
        return

    code.interact(local=imported_objects)


@command(
    b'debug-revlog-stats',
    [
        (b'c', b'changelog', None, _(b'Display changelog statistics')),
        (b'm', b'manifest', None, _(b'Display manifest statistics')),
        (b'f', b'filelogs', None, _(b'Display filelogs statistics')),
    ]
    + cmdutil.formatteropts,
)
def debug_revlog_stats(ui, repo, **opts):
    """display statistics about revlogs in the store"""
    changelog = opts["changelog"]
    manifest = opts["manifest"]
    filelogs = opts["filelogs"]

    if changelog is None and manifest is None and filelogs is None:
        changelog = True
        manifest = True
        filelogs = True

    repo = repo.unfiltered()
    fm = ui.formatter(b'debug-revlog-stats', pycompat.byteskwargs(opts))
    revlog_debug.debug_revlog_stats(repo, fm, changelog, manifest, filelogs)
    fm.end()


@command(
    b'debugsuccessorssets',
    [(b'', b'closest', False, _(b'return closest successors sets only'))],
    _(b'[REV]'),
)
def debugsuccessorssets(ui, repo, *revs, **opts):
    """show set of successors for revision

    A successors set of changeset A is a consistent group of revisions that
    succeed A. It contains non-obsolete changesets only unless closests
    successors set is set.

    In most cases a changeset A has a single successors set containing a single
    successor (changeset A replaced by A').

    A changeset that is made obsolete with no successors are called "pruned".
    Such changesets have no successors sets at all.

    A changeset that has been "split" will have a successors set containing
    more than one successor.

    A changeset that has been rewritten in multiple different ways is called
    "divergent". Such changesets have multiple successor sets (each of which
    may also be split, i.e. have multiple successors).

    Results are displayed as follows::

        <rev1>
            <successors-1A>
        <rev2>
            <successors-2A>
            <successors-2B1> <successors-2B2> <successors-2B3>

    Here rev2 has two possible (i.e. divergent) successors sets. The first
    holds one element, whereas the second holds three (i.e. the changeset has
    been split).
    """
    # passed to successorssets caching computation from one call to another
    cache = {}
    ctx2str = bytes
    node2str = short
    for rev in logcmdutil.revrange(repo, revs):
        ctx = repo[rev]
        ui.write(b'%s\n' % ctx2str(ctx))
        for succsset in obsutil.successorssets(
            repo, ctx.node(), closest=opts['closest'], cache=cache
        ):
            if succsset:
                ui.write(b'    ')
                ui.write(node2str(succsset[0]))
                for node in succsset[1:]:
                    ui.write(b' ')
                    ui.write(node2str(node))
            ui.write(b'\n')


@command(b'debugtagscache', [])
def debugtagscache(ui, repo):
    """display the contents of .hg/cache/hgtagsfnodes1"""
    cache = tagsmod.hgtagsfnodescache(repo.unfiltered())
    flog = repo.file(b'.hgtags')
    for r in repo:
        node = repo[r].node()
        tagsnode = cache.getfnode(node, computemissing=False)
        if tagsnode:
            tagsnodedisplay = hex(tagsnode)
            if not flog.hasnode(tagsnode):
                tagsnodedisplay += b' (unknown node)'
        elif tagsnode is None:
            tagsnodedisplay = b'missing'
        else:
            tagsnodedisplay = b'invalid'

        ui.write(b'%d %s %s\n' % (r, hex(node), tagsnodedisplay))


@command(
    b'debugtemplate',
    [
        (b'r', b'rev', [], _(b'apply template on changesets'), _(b'REV')),
        (b'D', b'define', [], _(b'define template keyword'), _(b'KEY=VALUE')),
    ],
    _(b'[-r REV]... [-D KEY=VALUE]... TEMPLATE'),
    optionalrepo=True,
)
def debugtemplate(ui, repo, tmpl, **opts):
    """parse and apply a template

    If -r/--rev is given, the template is processed as a log template and
    applied to the given changesets. Otherwise, it is processed as a generic
    template.

    Use --verbose to print the parsed tree.
    """
    revs = None
    if opts['rev']:
        if repo is None:
            raise error.RepoError(
                _(b'there is no Mercurial repository here (.hg not found)')
            )
        revs = logcmdutil.revrange(repo, opts['rev'])

    props = {}
    for d in opts['define']:
        try:
            k, v = (e.strip() for e in d.split(b'=', 1))
            if not k or k == b'ui':
                raise ValueError
            props[k] = v
        except ValueError:
            raise error.Abort(_(b'malformed keyword definition: %s') % d)

    if ui.verbose:
        aliases = ui.configitems(b'templatealias')
        tree = templater.parse(tmpl)
        ui.note(templater.prettyformat(tree), b'\n')
        newtree = templater.expandaliases(tree, aliases)
        if newtree != tree:
            ui.notenoi18n(
                b"* expanded:\n", templater.prettyformat(newtree), b'\n'
            )

    if revs is None:
        tres = formatter.templateresources(ui, repo)
        t = formatter.maketemplater(ui, tmpl, resources=tres)
        if ui.verbose:
            kwds, funcs = t.symbolsuseddefault()
            ui.writenoi18n(b"* keywords: %s\n" % b', '.join(sorted(kwds)))
            ui.writenoi18n(b"* functions: %s\n" % b', '.join(sorted(funcs)))
        ui.write(t.renderdefault(props))
    else:
        displayer = logcmdutil.maketemplater(ui, repo, tmpl)
        if ui.verbose:
            kwds, funcs = displayer.t.symbolsuseddefault()
            ui.writenoi18n(b"* keywords: %s\n" % b', '.join(sorted(kwds)))
            ui.writenoi18n(b"* functions: %s\n" % b', '.join(sorted(funcs)))
        for r in revs:
            displayer.show(repo[r], **pycompat.strkwargs(props))
        displayer.close()


@command(
    b'debuguigetpass',
    [
        (b'p', b'prompt', b'', _(b'prompt text'), _(b'TEXT')),
    ],
    _(b'[-p TEXT]'),
    norepo=True,
)
def debuguigetpass(ui, prompt=b''):
    """show prompt to type password"""
    r = ui.getpass(prompt)
    if r is None:
        r = b"<default response>"
    ui.writenoi18n(b'response: %s\n' % r)


@command(
    b'debuguiprompt',
    [
        (b'p', b'prompt', b'', _(b'prompt text'), _(b'TEXT')),
    ],
    _(b'[-p TEXT]'),
    norepo=True,
)
def debuguiprompt(ui, prompt=b''):
    """show plain prompt"""
    r = ui.prompt(prompt)
    ui.writenoi18n(b'response: %s\n' % r)


@command(b'debugupdatecaches', [])
def debugupdatecaches(ui, repo, *pats, **opts):
    """warm all known caches in the repository"""
    with repo.wlock(), repo.lock():
        repo.updatecaches(caches=repository.CACHES_ALL)


@command(
    b'debugupgraderepo',
    [
        (
            b'o',
            b'optimize',
            [],
            _(b'extra optimization to perform'),
            _(b'NAME'),
        ),
        (b'', b'run', False, _(b'performs an upgrade')),
        (b'', b'backup', True, _(b'keep the old repository content around')),
        (b'', b'changelog', None, _(b'select the changelog for upgrade')),
        (b'', b'manifest', None, _(b'select the manifest for upgrade')),
        (b'', b'filelogs', None, _(b'select all filelogs for upgrade')),
    ],
)
def debugupgraderepo(ui, repo, run=False, optimize=None, backup=True, **opts):
    """upgrade a repository to use different features

    If no arguments are specified, the repository is evaluated for upgrade
    and a list of problems and potential optimizations is printed.

    With ``--run``, a repository upgrade is performed. Behavior of the upgrade
    can be influenced via additional arguments. More details will be provided
    by the command output when run without ``--run``.

    During the upgrade, the repository will be locked and no writes will be
    allowed.

    At the end of the upgrade, the repository may not be readable while new
    repository data is swapped in. This window will be as long as it takes to
    rename some directories inside the ``.hg`` directory. On most machines, this
    should complete almost instantaneously and the chances of a consumer being
    unable to access the repository should be low.

    By default, all revlogs will be upgraded. You can restrict this using flags
    such as `--manifest`:

      * `--manifest`: only optimize the manifest
      * `--no-manifest`: optimize all revlog but the manifest
      * `--changelog`: optimize the changelog only
      * `--no-changelog --no-manifest`: optimize filelogs only
      * `--filelogs`: optimize the filelogs only
      * `--no-changelog --no-manifest --no-filelogs`: skip all revlog optimizations
    """
    return upgrade.upgraderepo(
        ui, repo, run=run, optimize=set(optimize), backup=backup, **opts
    )


@command(
    b'debug::unbundle',
    [],
    _(b'FILE...'),
    helpcategory=command.CATEGORY_IMPORT_EXPORT,
)
def debugunbundle(ui, repo, fname1, *fnames):
    """same as `hg unbundle`, but pretent to come from a push

    This is useful to debug behavior and performance change in this case.
    """
    fnames = (fname1,) + fnames
    cmdutil.unbundle_files(ui, repo, fnames)


@command(
    b'debugwalk', cmdutil.walkopts, _(b'[OPTION]... [FILE]...'), inferrepo=True
)
def debugwalk(ui, repo, *pats, **opts):
    """show how files match on given patterns"""
    m = scmutil.match(repo[None], pats, pycompat.byteskwargs(opts))
    if ui.verbose:
        ui.writenoi18n(b'* matcher:\n', stringutil.prettyrepr(m), b'\n')
    items = list(repo[None].walk(m))
    if not items:
        return
    f = lambda fn: fn
    if ui.configbool(b'ui', b'slash') and pycompat.ossep != b'/':
        f = lambda fn: util.normpath(fn)
    fmt = b'f  %%-%ds  %%-%ds  %%s' % (
        max([len(abs) for abs in items]),
        max([len(repo.pathto(abs)) for abs in items]),
    )
    for abs in items:
        line = fmt % (
            abs,
            f(repo.pathto(abs)),
            m.exact(abs) and b'exact' or b'',
        )
        ui.write(b"%s\n" % line.rstrip())


@command(b'debugwhyunstable', [], _(b'REV'))
def debugwhyunstable(ui, repo, rev):
    """explain instabilities of a changeset"""
    for entry in obsutil.whyunstable(repo, scmutil.revsingle(repo, rev)):
        dnodes = b''
        if entry.get(b'divergentnodes'):
            dnodes = (
                b' '.join(
                    b'%s (%s)' % (ctx.hex(), ctx.phasestr())
                    for ctx in entry[b'divergentnodes']
                )
                + b' '
            )
        ui.write(
            b'%s: %s%s %s\n'
            % (entry[b'instability'], dnodes, entry[b'reason'], entry[b'node'])
        )


@command(
    b'debugwireargs',
    [
        (b'', b'three', b'', b'three'),
        (b'', b'four', b'', b'four'),
        (b'', b'five', b'', b'five'),
    ]
    + cmdutil.remoteopts,
    _(b'REPO [OPTIONS]... [ONE [TWO]]'),
    norepo=True,
)
def debugwireargs(ui, repopath, *vals, **opts):
    repo = hg.peer(ui, pycompat.byteskwargs(opts), repopath)
    try:
        for opt in cmdutil.remoteopts:
            del opts[pycompat.sysstr(opt[1])]
        args = {}
        for k, v in opts.items():
            if v:
                args[k] = v

        # run twice to check that we don't mess up the stream for the next command
        res1 = repo.debugwireargs(*vals, **args)
        res2 = repo.debugwireargs(*vals, **args)
        ui.write(b"%s\n" % res1)
        if res1 != res2:
            ui.warn(b"%s\n" % res2)
    finally:
        repo.close()


def _parsewirelangblocks(fh):
    activeaction = None
    blocklines = []
    lastindent = 0

    for line in fh:
        line = line.rstrip()
        if not line:
            continue

        if line.startswith(b'#'):
            continue

        if not line.startswith(b' '):
            # New block. Flush previous one.
            if activeaction:
                yield activeaction, blocklines

            activeaction = line
            blocklines = []
            lastindent = 0
            continue

        # Else we start with an indent.

        if not activeaction:
            raise error.Abort(_(b'indented line outside of block'))

        indent = len(line) - len(line.lstrip())

        # If this line is indented more than the last line, concatenate it.
        if indent > lastindent and blocklines:
            blocklines[-1] += line.lstrip()
        else:
            blocklines.append(line)
            lastindent = indent

    # Flush last block.
    if activeaction:
        yield activeaction, blocklines


@command(
    b'debugwireproto',
    [
        (b'', b'localssh', False, _(b'start an SSH server for this repo')),
        (b'', b'peer', b'', _(b'construct a specific version of the peer')),
        (
            b'',
            b'noreadstderr',
            False,
            _(b'do not read from stderr of the remote'),
        ),
        (
            b'',
            b'nologhandshake',
            False,
            _(b'do not log I/O related to the peer handshake'),
        ),
    ]
    + cmdutil.remoteopts,
    _(b'[PATH]'),
    optionalrepo=True,
)
def debugwireproto(ui, repo, path=None, **opts):
    """send wire protocol commands to a server

    This command can be used to issue wire protocol commands to remote
    peers and to debug the raw data being exchanged.

    ``--localssh`` will start an SSH server against the current repository
    and connect to that. By default, the connection will perform a handshake
    and establish an appropriate peer instance.

    ``--peer`` can be used to bypass the handshake protocol and construct a
    peer instance using the specified class type. Valid values are ``raw``,
    ``ssh1``. ``raw`` instances only allow sending raw data payloads and
    don't support higher-level command actions.

    ``--noreadstderr`` can be used to disable automatic reading from stderr
    of the peer (for SSH connections only). Disabling automatic reading of
    stderr is useful for making output more deterministic.

    Commands are issued via a mini language which is specified via stdin.
    The language consists of individual actions to perform. An action is
    defined by a block. A block is defined as a line with no leading
    space followed by 0 or more lines with leading space. Blocks are
    effectively a high-level command with additional metadata.

    Lines beginning with ``#`` are ignored.

    The following sections denote available actions.

    raw
    ---

    Send raw data to the server.

    The block payload contains the raw data to send as one atomic send
    operation. The data may not actually be delivered in a single system
    call: it depends on the abilities of the transport being used.

    Each line in the block is de-indented and concatenated. Then, that
    value is evaluated as a Python b'' literal. This allows the use of
    backslash escaping, etc.

    raw+
    ----

    Behaves like ``raw`` except flushes output afterwards.

    command <X>
    -----------

    Send a request to run a named command, whose name follows the ``command``
    string.

    Arguments to the command are defined as lines in this block. The format of
    each line is ``<key> <value>``. e.g.::

       command listkeys
           namespace bookmarks

    If the value begins with ``eval:``, it will be interpreted as a Python
    literal expression. Otherwise values are interpreted as Python b'' literals.
    This allows sending complex types and encoding special byte sequences via
    backslash escaping.

    The following arguments have special meaning:

    ``PUSHFILE``
        When defined, the *push* mechanism of the peer will be used instead
        of the static request-response mechanism and the content of the
        file specified in the value of this argument will be sent as the
        command payload.

        This can be used to submit a local bundle file to the remote.

    batchbegin
    ----------

    Instruct the peer to begin a batched send.

    All ``command`` blocks are queued for execution until the next
    ``batchsubmit`` block.

    batchsubmit
    -----------

    Submit previously queued ``command`` blocks as a batch request.

    This action MUST be paired with a ``batchbegin`` action.

    httprequest <method> <path>
    ---------------------------

    (HTTP peer only)

    Send an HTTP request to the peer.

    The HTTP request line follows the ``httprequest`` action. e.g. ``GET /foo``.

    Arguments of the form ``<key>: <value>`` are interpreted as HTTP request
    headers to add to the request. e.g. ``Accept: foo``.

    The following arguments are special:

    ``BODYFILE``
        The content of the file defined as the value to this argument will be
        transferred verbatim as the HTTP request body.

    ``frame <type> <flags> <payload>``
        Send a unified protocol frame as part of the request body.

        All frames will be collected and sent as the body to the HTTP
        request.

    close
    -----

    Close the connection to the server.

    flush
    -----

    Flush data written to the server.

    readavailable
    -------------

    Close the write end of the connection and read all available data from
    the server.

    If the connection to the server encompasses multiple pipes, we poll both
    pipes and read available data.

    readline
    --------

    Read a line of output from the server. If there are multiple output
    pipes, reads only the main pipe.

    ereadline
    ---------

    Like ``readline``, but read from the stderr pipe, if available.

    read <X>
    --------

    ``read()`` N bytes from the server's main output pipe.

    eread <X>
    ---------

    ``read()`` N bytes from the server's stderr pipe, if available.

    Specifying Unified Frame-Based Protocol Frames
    ----------------------------------------------

    It is possible to emit a *Unified Frame-Based Protocol* by using special
    syntax.

    A frame is composed as a type, flags, and payload. These can be parsed
    from a string of the form:

       <request-id> <stream-id> <stream-flags> <type> <flags> <payload>

    ``request-id`` and ``stream-id`` are integers defining the request and
    stream identifiers.

    ``type`` can be an integer value for the frame type or the string name
    of the type. The strings are defined in ``wireprotoframing.py``. e.g.
    ``command-name``.

    ``stream-flags`` and ``flags`` are a ``|`` delimited list of flag
    components. Each component (and there can be just one) can be an integer
    or a flag name for stream flags or frame flags, respectively. Values are
    resolved to integers and then bitwise OR'd together.

    ``payload`` represents the raw frame payload. If it begins with
    ``cbor:``, the following string is evaluated as Python code and the
    resulting object is fed into a CBOR encoder. Otherwise it is interpreted
    as a Python byte string literal.
    """
    if opts['localssh'] and not repo:
        raise error.Abort(_(b'--localssh requires a repository'))

    if opts['peer'] and opts['peer'] not in (
        b'raw',
        b'ssh1',
    ):
        raise error.Abort(
            _(b'invalid value for --peer'),
            hint=_(b'valid values are "raw" and "ssh1"'),
        )

    if path and opts['localssh']:
        raise error.Abort(_(b'cannot specify --localssh with an explicit path'))

    if ui.interactive():
        ui.write(_(b'(waiting for commands on stdin)\n'))

    blocks = list(_parsewirelangblocks(ui.fin))

    proc = None
    stdin = None
    stdout = None
    stderr = None
    opener = None

    if opts['localssh']:
        # We start the SSH server in its own process so there is process
        # separation. This prevents a whole class of potential bugs around
        # shared state from interfering with server operation.
        args = procutil.hgcmd() + [
            b'-R',
            repo.root,
            b'debugserve',
            b'--sshstdio',
        ]
        proc = subprocess.Popen(
            pycompat.rapply(procutil.tonativestr, args),
            stdin=subprocess.PIPE,
            stdout=subprocess.PIPE,
            stderr=subprocess.PIPE,
            bufsize=0,
        )

        stdin = proc.stdin
        stdout = proc.stdout
        stderr = proc.stderr

        # We turn the pipes into observers so we can log I/O.
        if ui.verbose or opts['peer'] == b'raw':
            stdin = util.makeloggingfileobject(
                ui, proc.stdin, b'i', logdata=True
            )
            stdout = util.makeloggingfileobject(
                ui, proc.stdout, b'o', logdata=True
            )
            stderr = util.makeloggingfileobject(
                ui, proc.stderr, b'e', logdata=True
            )

        # --localssh also implies the peer connection settings.

        url = b'ssh://localserver'
        autoreadstderr = not opts['noreadstderr']

        if opts['peer'] == b'ssh1':
            ui.write(_(b'creating ssh peer for wire protocol version 1\n'))
            peer = sshpeer.sshv1peer(
                ui,
                url,
                proc,
                stdin,
                stdout,
                stderr,
                None,
                autoreadstderr=autoreadstderr,
            )
        elif opts['peer'] == b'raw':
            ui.write(_(b'using raw connection to peer\n'))
            peer = None
        else:
            ui.write(_(b'creating ssh peer from handshake results\n'))
            peer = sshpeer._make_peer(
                ui,
                url,
                proc,
                stdin,
                stdout,
                stderr,
                autoreadstderr=autoreadstderr,
            )

    elif path:
        # We bypass hg.peer() so we can proxy the sockets.
        # TODO consider not doing this because we skip
        # ``hg.wirepeersetupfuncs`` and potentially other useful functionality.
        u = urlutil.url(path)
        if u.scheme != b'http':
            raise error.Abort(_(b'only http:// paths are currently supported'))

        url, authinfo = u.authinfo()
        openerargs = {
            'useragent': b'Mercurial debugwireproto',
        }

        # Turn pipes/sockets into observers so we can log I/O.
        if ui.verbose:
            openerargs.update(
                {
                    'loggingfh': ui,
                    'loggingname': b's',
                    'loggingopts': {
                        'logdata': True,
                        'logdataapis': False,
                    },
                }
            )

        if ui.debugflag:
            openerargs['loggingopts']['logdataapis'] = True

        # Don't send default headers when in raw mode. This allows us to
        # bypass most of the behavior of our URL handling code so we can
        # have near complete control over what's sent on the wire.
        if opts['peer'] == b'raw':
            openerargs['sendaccept'] = False

        opener = urlmod.opener(ui, authinfo, **openerargs)

        if opts['peer'] == b'raw':
            ui.write(_(b'using raw connection to peer\n'))
            peer = None
        elif opts['peer']:
            raise error.Abort(
                _(b'--peer %s not supported with HTTP peers') % opts['peer']
            )
        else:
            peer_path = urlutil.try_path(ui, path)
            peer = httppeer._make_peer(ui, peer_path, opener=opener)

        # We /could/ populate stdin/stdout with sock.makefile()...
    else:
        raise error.Abort(_(b'unsupported connection configuration'))

    batchedcommands = None

    # Now perform actions based on the parsed wire language instructions.
    for action, lines in blocks:
        if action in (b'raw', b'raw+'):
            if not stdin:
                raise error.Abort(_(b'cannot call raw/raw+ on this peer'))

            # Concatenate the data together.
            data = b''.join(l.lstrip() for l in lines)
            data = stringutil.unescapestr(data)
            stdin.write(data)

            if action == b'raw+':
                stdin.flush()
        elif action == b'flush':
            if not stdin:
                raise error.Abort(_(b'cannot call flush on this peer'))
            stdin.flush()
        elif action.startswith(b'command'):
            if not peer:
                raise error.Abort(
                    _(
                        b'cannot send commands unless peer instance '
                        b'is available'
                    )
                )

            command = action.split(b' ', 1)[1]

            args = {}
            for line in lines:
                # We need to allow empty values.
                fields = line.lstrip().split(b' ', 1)
                if len(fields) == 1:
                    key = fields[0]
                    value = b''
                else:
                    key, value = fields

                if value.startswith(b'eval:'):
                    value = stringutil.evalpythonliteral(value[5:])
                else:
                    value = stringutil.unescapestr(value)

                args[key] = value

            if batchedcommands is not None:
                batchedcommands.append((command, args))
                continue

            ui.status(_(b'sending %s command\n') % command)

            if b'PUSHFILE' in args:
                with open(args[b'PUSHFILE'], 'rb') as fh:
                    del args[b'PUSHFILE']
                    res, output = peer._callpush(
                        command, fh, **pycompat.strkwargs(args)
                    )
                    ui.status(_(b'result: %s\n') % stringutil.escapestr(res))
                    ui.status(
                        _(b'remote output: %s\n') % stringutil.escapestr(output)
                    )
            else:
                with peer.commandexecutor() as e:
                    res = e.callcommand(command, args).result()

                ui.status(
                    _(b'response: %s\n')
                    % stringutil.pprint(res, bprefix=True, indent=2)
                )

        elif action == b'batchbegin':
            if batchedcommands is not None:
                raise error.Abort(_(b'nested batchbegin not allowed'))

            batchedcommands = []
        elif action == b'batchsubmit':
            # There is a batching API we could go through. But it would be
            # difficult to normalize requests into function calls. It is easier
            # to bypass this layer and normalize to commands + args.
            ui.status(
                _(b'sending batch with %d sub-commands\n')
                % len(batchedcommands)
            )
            assert peer is not None
            for i, chunk in enumerate(peer._submitbatch(batchedcommands)):
                ui.status(
                    _(b'response #%d: %s\n') % (i, stringutil.escapestr(chunk))
                )

            batchedcommands = None

        elif action.startswith(b'httprequest '):
            if not opener:
                raise error.Abort(
                    _(b'cannot use httprequest without an HTTP peer')
                )

            request = action.split(b' ', 2)
            if len(request) != 3:
                raise error.Abort(
                    _(
                        b'invalid httprequest: expected format is '
                        b'"httprequest <method> <path>'
                    )
                )

            method, httppath = request[1:]
            headers = {}
            body = None
            frames = []
            for line in lines:
                line = line.lstrip()
                m = re.match(b'^([a-zA-Z0-9_-]+): (.*)$', line)
                if m:
                    # Headers need to use native strings.
                    key = pycompat.strurl(m.group(1))
                    value = pycompat.strurl(m.group(2))
                    headers[key] = value
                    continue

                if line.startswith(b'BODYFILE '):
                    with open(line.split(b' ', 1), b'rb') as fh:
                        body = fh.read()
                elif line.startswith(b'frame '):
                    frame = wireprotoframing.makeframefromhumanstring(
                        line[len(b'frame ') :]
                    )

                    frames.append(frame)
                else:
                    raise error.Abort(
                        _(b'unknown argument to httprequest: %s') % line
                    )

            url = path + httppath

            if frames:
                body = b''.join(bytes(f) for f in frames)

            req = urlmod.urlreq.request(pycompat.strurl(url), body, headers)

            # urllib.Request insists on using has_data() as a proxy for
            # determining the request method. Override that to use our
            # explicitly requested method.
            req.get_method = lambda: pycompat.sysstr(method)

            try:
                res = opener.open(req)
                body = res.read()
            except util.urlerr.urlerror as e:
                # read() method must be called, but only exists in Python 2
                getattr(e, 'read', lambda: None)()
                continue

            ct = res.headers.get('Content-Type')
            if ct == 'application/mercurial-cbor':
                ui.write(
                    _(b'cbor> %s\n')
                    % stringutil.pprint(
                        cborutil.decodeall(body), bprefix=True, indent=2
                    )
                )

        elif action == b'close':
            assert peer is not None
            peer.close()
        elif action == b'readavailable':
            if not stdout or not stderr:
                raise error.Abort(
                    _(b'readavailable not available on this peer')
                )

            stdin.close()
            stdout.read()
            stderr.read()

        elif action == b'readline':
            if not stdout:
                raise error.Abort(_(b'readline not available on this peer'))
            stdout.readline()
        elif action == b'ereadline':
            if not stderr:
                raise error.Abort(_(b'ereadline not available on this peer'))
            stderr.readline()
        elif action.startswith(b'read '):
            count = int(action.split(b' ', 1)[1])
            if not stdout:
                raise error.Abort(_(b'read not available on this peer'))
            stdout.read(count)
        elif action.startswith(b'eread '):
            count = int(action.split(b' ', 1)[1])
            if not stderr:
                raise error.Abort(_(b'eread not available on this peer'))
            stderr.read(count)
        else:
            raise error.Abort(_(b'unknown action: %s') % action)

    if batchedcommands is not None:
        raise error.Abort(_(b'unclosed "batchbegin" request'))

    if peer:
        peer.close()

    if proc:
        proc.kill()
