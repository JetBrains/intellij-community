# Copyright 2020 Joerg Sonnenberger <joerg@bec.de>
#
# This software may be used and distributed according to the terms of the
# GNU General Public License version 2 or any later version.
"""export repositories as git fast-import stream"""

# The format specification for fast-import streams can be found at
# https://git-scm.com/docs/git-fast-import#_input_format

from __future__ import absolute_import
import re

from mercurial.i18n import _
from mercurial.node import hex, nullrev
from mercurial.utils import stringutil
from mercurial import (
    error,
    pycompat,
    registrar,
    scmutil,
)
from .convert import convcmd

# Note for extension authors: ONLY specify testedwith = 'ships-with-hg-core' for
# extensions which SHIP WITH MERCURIAL. Non-mainline extensions should
# be specifying the version(s) of Mercurial they are tested with, or
# leave the attribute unspecified.
testedwith = b"ships-with-hg-core"

cmdtable = {}
command = registrar.command(cmdtable)

GIT_PERSON_PROHIBITED = re.compile(b'[<>\n"]')
GIT_EMAIL_PROHIBITED = re.compile(b"[<> \n]")


def convert_to_git_user(authormap, user, rev):
    mapped_user = authormap.get(user, user)
    user_person = stringutil.person(mapped_user)
    user_email = stringutil.email(mapped_user)
    if GIT_EMAIL_PROHIBITED.match(user_email) or GIT_PERSON_PROHIBITED.match(
        user_person
    ):
        raise error.Abort(
            _(b"Unable to parse user into person and email for revision %s")
            % rev
        )
    if user_person:
        return b'"' + user_person + b'" <' + user_email + b'>'
    else:
        return b"<" + user_email + b">"


def convert_to_git_date(date):
    timestamp, utcoff = date
    tzsign = b"+" if utcoff <= 0 else b"-"
    if utcoff % 60 != 0:
        raise error.Abort(
            _(b"UTC offset in %b is not an integer number of seconds") % (date,)
        )
    utcoff = abs(utcoff) // 60
    tzh = utcoff // 60
    tzmin = utcoff % 60
    return b"%d " % int(timestamp) + tzsign + b"%02d%02d" % (tzh, tzmin)


def convert_to_git_ref(branch):
    # XXX filter/map depending on git restrictions
    return b"refs/heads/" + branch


def write_data(buf, data, skip_newline):
    buf.append(b"data %d\n" % len(data))
    buf.append(data)
    if not skip_newline or data[-1:] != b"\n":
        buf.append(b"\n")


def export_commit(ui, repo, rev, marks, authormap):
    ctx = repo[rev]
    revid = ctx.hex()
    if revid in marks:
        ui.debug(b"warning: revision %s already exported, skipped\n" % revid)
        return
    parents = [p for p in ctx.parents() if p.rev() != nullrev]
    for p in parents:
        if p.hex() not in marks:
            ui.warn(
                _(b"warning: parent %s of %s has not been exported, skipped\n")
                % (p, revid)
            )
            return

    # For all files modified by the commit, check if they have already
    # been exported and otherwise dump the blob with the new mark.
    for fname in ctx.files():
        if fname not in ctx:
            continue
        filectx = ctx.filectx(fname)
        filerev = hex(filectx.filenode())
        if filerev not in marks:
            mark = len(marks) + 1
            marks[filerev] = mark
            data = filectx.data()
            buf = [b"blob\n", b"mark :%d\n" % mark]
            write_data(buf, data, False)
            ui.write(*buf, keepprogressbar=True)
            del buf

    # Assign a mark for the current revision for references by
    # latter merge commits.
    mark = len(marks) + 1
    marks[revid] = mark

    ref = convert_to_git_ref(ctx.branch())
    buf = [
        b"commit %s\n" % ref,
        b"mark :%d\n" % mark,
        b"committer %s %s\n"
        % (
            convert_to_git_user(authormap, ctx.user(), revid),
            convert_to_git_date(ctx.date()),
        ),
    ]
    write_data(buf, ctx.description(), True)
    if parents:
        buf.append(b"from :%d\n" % marks[parents[0].hex()])
    if len(parents) == 2:
        buf.append(b"merge :%d\n" % marks[parents[1].hex()])
        p0ctx = repo[parents[0]]
        files = ctx.manifest().diff(p0ctx.manifest())
    else:
        files = ctx.files()
    filebuf = []
    for fname in files:
        if fname not in ctx:
            filebuf.append((fname, b"D %s\n" % fname))
        else:
            filectx = ctx.filectx(fname)
            filerev = filectx.filenode()
            fileperm = b"755" if filectx.isexec() else b"644"
            changed = b"M %s :%d %s\n" % (fileperm, marks[hex(filerev)], fname)
            filebuf.append((fname, changed))
    filebuf.sort()
    buf.extend(changed for (fname, changed) in filebuf)
    del filebuf
    buf.append(b"\n")
    ui.write(*buf, keepprogressbar=True)
    del buf


isrev = re.compile(b"^[0-9a-f]{40}$")


@command(
    b"fastexport",
    [
        (b"r", b"rev", [], _(b"revisions to export"), _(b"REV")),
        (b"i", b"import-marks", b"", _(b"old marks file to read"), _(b"FILE")),
        (b"e", b"export-marks", b"", _(b"new marks file to write"), _(b"FILE")),
        (
            b"A",
            b"authormap",
            b"",
            _(b"remap usernames using this file"),
            _(b"FILE"),
        ),
    ],
    _(b"[OPTION]... [REV]..."),
    helpcategory=command.CATEGORY_IMPORT_EXPORT,
)
def fastexport(ui, repo, *revs, **opts):
    """export repository as git fast-import stream

    This command lets you dump a repository as a human-readable text stream.
    It can be piped into corresponding import routines like "git fast-import".
    Incremental dumps can be created by using marks files.
    """
    opts = pycompat.byteskwargs(opts)

    revs += tuple(opts.get(b"rev", []))
    if not revs:
        revs = scmutil.revrange(repo, [b":"])
    else:
        revs = scmutil.revrange(repo, revs)
    if not revs:
        raise error.Abort(_(b"no revisions matched"))
    authorfile = opts.get(b"authormap")
    if authorfile:
        authormap = convcmd.readauthormap(ui, authorfile)
    else:
        authormap = {}

    import_marks = opts.get(b"import_marks")
    marks = {}
    if import_marks:
        with open(import_marks, "rb") as import_marks_file:
            for line in import_marks_file:
                line = line.strip()
                if not isrev.match(line) or line in marks:
                    raise error.Abort(_(b"Corrupted marks file"))
                marks[line] = len(marks) + 1

    revs.sort()
    with ui.makeprogress(
        _(b"exporting"), unit=_(b"revisions"), total=len(revs)
    ) as progress:
        for rev in revs:
            export_commit(ui, repo, rev, marks, authormap)
            progress.increment()

    export_marks = opts.get(b"export_marks")
    if export_marks:
        with open(export_marks, "wb") as export_marks_file:
            output_marks = [None] * len(marks)
            for k, v in marks.items():
                output_marks[v - 1] = k
            for k in output_marks:
                export_marks_file.write(k + b"\n")
