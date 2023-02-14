# Copyright 2017-present Gregory Szorc <gregory.szorc@gmail.com>
#
# This software may be used and distributed according to the terms of the
# GNU General Public License version 2 or any later version.

"""generate release notes from commit messages (EXPERIMENTAL)

It is common to maintain files detailing changes in a project between
releases. Maintaining these files can be difficult and time consuming.
The :hg:`releasenotes` command provided by this extension makes the
process simpler by automating it.
"""

from __future__ import absolute_import

import difflib
import errno
import re

from mercurial.i18n import _
from mercurial.pycompat import open
from mercurial.node import hex
from mercurial import (
    cmdutil,
    config,
    error,
    minirst,
    pycompat,
    registrar,
    scmutil,
    util,
)
from mercurial.utils import (
    procutil,
    stringutil,
)

cmdtable = {}
command = registrar.command(cmdtable)

try:
    # Silence a warning about python-Levenshtein.
    #
    # We don't need the the performance that much and it get anoying in tests.
    import warnings

    with warnings.catch_warnings():
        warnings.filterwarnings(
            action="ignore",
            message=".*python-Levenshtein.*",
            category=UserWarning,
            module="fuzzywuzzy.fuzz",
        )

        import fuzzywuzzy.fuzz as fuzz

        fuzz.token_set_ratio
except ImportError:
    fuzz = None

# Note for extension authors: ONLY specify testedwith = 'ships-with-hg-core' for
# extensions which SHIP WITH MERCURIAL. Non-mainline extensions should
# be specifying the version(s) of Mercurial they are tested with, or
# leave the attribute unspecified.
testedwith = b'ships-with-hg-core'

DEFAULT_SECTIONS = [
    (b'feature', _(b'New Features')),
    (b'bc', _(b'Backwards Compatibility Changes')),
    (b'fix', _(b'Bug Fixes')),
    (b'perf', _(b'Performance Improvements')),
    (b'api', _(b'API Changes')),
]

RE_DIRECTIVE = re.compile(br'^\.\. ([a-zA-Z0-9_]+)::\s*([^$]+)?$')
RE_ISSUE = br'\bissue ?[0-9]{4,6}(?![0-9])\b'

BULLET_SECTION = _(b'Other Changes')


class parsedreleasenotes(object):
    def __init__(self):
        self.sections = {}

    def __contains__(self, section):
        return section in self.sections

    def __iter__(self):
        return iter(sorted(self.sections))

    def addtitleditem(self, section, title, paragraphs):
        """Add a titled release note entry."""
        self.sections.setdefault(section, ([], []))
        self.sections[section][0].append((title, paragraphs))

    def addnontitleditem(self, section, paragraphs):
        """Adds a non-titled release note entry.

        Will be rendered as a bullet point.
        """
        self.sections.setdefault(section, ([], []))
        self.sections[section][1].append(paragraphs)

    def titledforsection(self, section):
        """Returns titled entries in a section.

        Returns a list of (title, paragraphs) tuples describing sub-sections.
        """
        return self.sections.get(section, ([], []))[0]

    def nontitledforsection(self, section):
        """Returns non-titled, bulleted paragraphs in a section."""
        return self.sections.get(section, ([], []))[1]

    def hastitledinsection(self, section, title):
        return any(t[0] == title for t in self.titledforsection(section))

    def merge(self, ui, other):
        """Merge another instance into this one.

        This is used to combine multiple sources of release notes together.
        """
        if not fuzz:
            ui.warn(
                _(
                    b"module 'fuzzywuzzy' not found, merging of similar "
                    b"releasenotes is disabled\n"
                )
            )

        for section in other:
            existingnotes = converttitled(
                self.titledforsection(section)
            ) + convertnontitled(self.nontitledforsection(section))
            for title, paragraphs in other.titledforsection(section):
                if self.hastitledinsection(section, title):
                    # TODO prompt for resolution if different and running in
                    # interactive mode.
                    ui.write(
                        _(b'%s already exists in %s section; ignoring\n')
                        % (title, section)
                    )
                    continue

                incoming_str = converttitled([(title, paragraphs)])[0]
                if section == b'fix':
                    issue = getissuenum(incoming_str)
                    if issue:
                        if findissue(ui, existingnotes, issue):
                            continue

                if similar(ui, existingnotes, incoming_str):
                    continue

                self.addtitleditem(section, title, paragraphs)

            for paragraphs in other.nontitledforsection(section):
                if paragraphs in self.nontitledforsection(section):
                    continue

                incoming_str = convertnontitled([paragraphs])[0]
                if section == b'fix':
                    issue = getissuenum(incoming_str)
                    if issue:
                        if findissue(ui, existingnotes, issue):
                            continue

                if similar(ui, existingnotes, incoming_str):
                    continue

                self.addnontitleditem(section, paragraphs)


class releasenotessections(object):
    def __init__(self, ui, repo=None):
        if repo:
            sections = util.sortdict(DEFAULT_SECTIONS)
            custom_sections = getcustomadmonitions(repo)
            if custom_sections:
                sections.update(custom_sections)
            self._sections = list(pycompat.iteritems(sections))
        else:
            self._sections = list(DEFAULT_SECTIONS)

    def __iter__(self):
        return iter(self._sections)

    def names(self):
        return [t[0] for t in self._sections]

    def sectionfromtitle(self, title):
        for name, value in self._sections:
            if value == title:
                return name

        return None


def converttitled(titledparagraphs):
    """
    Convert titled paragraphs to strings
    """
    string_list = []
    for title, paragraphs in titledparagraphs:
        lines = []
        for para in paragraphs:
            lines.extend(para)
        string_list.append(b' '.join(lines))
    return string_list


def convertnontitled(nontitledparagraphs):
    """
    Convert non-titled bullets to strings
    """
    string_list = []
    for paragraphs in nontitledparagraphs:
        lines = []
        for para in paragraphs:
            lines.extend(para)
        string_list.append(b' '.join(lines))
    return string_list


def getissuenum(incoming_str):
    """
    Returns issue number from the incoming string if it exists
    """
    issue = re.search(RE_ISSUE, incoming_str, re.IGNORECASE)
    if issue:
        issue = issue.group()
    return issue


def findissue(ui, existing, issue):
    """
    Returns true if issue number already exists in notes.
    """
    if any(issue in s for s in existing):
        ui.write(_(b'"%s" already exists in notes; ignoring\n') % issue)
        return True
    else:
        return False


def similar(ui, existing, incoming_str):
    """
    Returns true if similar note found in existing notes.
    """
    if len(incoming_str.split()) > 10:
        merge = similaritycheck(incoming_str, existing)
        if not merge:
            ui.write(
                _(b'"%s" already exists in notes file; ignoring\n')
                % incoming_str
            )
            return True
        else:
            return False
    else:
        return False


def similaritycheck(incoming_str, existingnotes):
    """
    Returns false when note fragment can be merged to existing notes.
    """
    # fuzzywuzzy not present
    if not fuzz:
        return True

    merge = True
    for bullet in existingnotes:
        score = fuzz.token_set_ratio(incoming_str, bullet)
        if score > 75:
            merge = False
            break
    return merge


def getcustomadmonitions(repo):
    ctx = repo[b'.']
    p = config.config()

    def read(f, sections=None, remap=None):
        if f in ctx:
            data = ctx[f].data()
            p.parse(f, data, sections, remap, read)
        else:
            raise error.Abort(
                _(b".hgreleasenotes file \'%s\' not found") % repo.pathto(f)
            )

    if b'.hgreleasenotes' in ctx:
        read(b'.hgreleasenotes')
    return p.items(b'sections')


def checkadmonitions(ui, repo, directives, revs):
    """
    Checks the commit messages for admonitions and their validity.

    .. abcd::

       First paragraph under this admonition

    For this commit message, using `hg releasenotes -r . --check`
    returns: Invalid admonition 'abcd' present in changeset 3ea92981e103

    As admonition 'abcd' is neither present in default nor custom admonitions
    """
    for rev in revs:
        ctx = repo[rev]
        admonition = re.search(RE_DIRECTIVE, ctx.description())
        if admonition:
            if admonition.group(1) in directives:
                continue
            else:
                ui.write(
                    _(b"Invalid admonition '%s' present in changeset %s\n")
                    % (admonition.group(1), ctx.hex()[:12])
                )
                sim = lambda x: difflib.SequenceMatcher(
                    None, admonition.group(1), x
                ).ratio()

                similar = [s for s in directives if sim(s) > 0.6]
                if len(similar) == 1:
                    ui.write(_(b"(did you mean %s?)\n") % similar[0])
                elif similar:
                    ss = b", ".join(sorted(similar))
                    ui.write(_(b"(did you mean one of %s?)\n") % ss)


def _getadmonitionlist(ui, sections):
    for section in sections:
        ui.write(b"%s: %s\n" % (section[0], section[1]))


def parsenotesfromrevisions(repo, directives, revs):
    notes = parsedreleasenotes()

    for rev in revs:
        ctx = repo[rev]

        blocks, pruned = minirst.parse(
            ctx.description(), admonitions=directives
        )

        for i, block in enumerate(blocks):
            if block[b'type'] != b'admonition':
                continue

            directive = block[b'admonitiontitle']
            title = block[b'lines'][0].strip() if block[b'lines'] else None

            if i + 1 == len(blocks):
                raise error.Abort(
                    _(
                        b'changeset %s: release notes directive %s '
                        b'lacks content'
                    )
                    % (ctx, directive)
                )

            # Now search ahead and find all paragraphs attached to this
            # admonition.
            paragraphs = []
            for j in range(i + 1, len(blocks)):
                pblock = blocks[j]

                # Margin blocks may appear between paragraphs. Ignore them.
                if pblock[b'type'] == b'margin':
                    continue

                if pblock[b'type'] == b'admonition':
                    break

                if pblock[b'type'] != b'paragraph':
                    repo.ui.warn(
                        _(
                            b'changeset %s: unexpected block in release '
                            b'notes directive %s\n'
                        )
                        % (ctx, directive)
                    )

                if pblock[b'indent'] > 0:
                    paragraphs.append(pblock[b'lines'])
                else:
                    break

            # TODO consider using title as paragraph for more concise notes.
            if not paragraphs:
                repo.ui.warn(
                    _(b"error parsing releasenotes for revision: '%s'\n")
                    % hex(ctx.node())
                )
            if title:
                notes.addtitleditem(directive, title, paragraphs)
            else:
                notes.addnontitleditem(directive, paragraphs)

    return notes


def parsereleasenotesfile(sections, text):
    """Parse text content containing generated release notes."""
    notes = parsedreleasenotes()

    blocks = minirst.parse(text)[0]

    def gatherparagraphsbullets(offset, title=False):
        notefragment = []

        for i in range(offset + 1, len(blocks)):
            block = blocks[i]

            if block[b'type'] == b'margin':
                continue
            elif block[b'type'] == b'section':
                break
            elif block[b'type'] == b'bullet':
                if block[b'indent'] != 0:
                    raise error.Abort(_(b'indented bullet lists not supported'))
                if title:
                    lines = [l[1:].strip() for l in block[b'lines']]
                    notefragment.append(lines)
                    continue
                else:
                    lines = [[l[1:].strip() for l in block[b'lines']]]

                    for block in blocks[i + 1 :]:
                        if block[b'type'] in (b'bullet', b'section'):
                            break
                        if block[b'type'] == b'paragraph':
                            lines.append(block[b'lines'])
                    notefragment.append(lines)
                    continue
            elif block[b'type'] != b'paragraph':
                raise error.Abort(
                    _(b'unexpected block type in release notes: %s')
                    % block[b'type']
                )
            if title:
                notefragment.append(block[b'lines'])

        return notefragment

    currentsection = None
    for i, block in enumerate(blocks):
        if block[b'type'] != b'section':
            continue

        title = block[b'lines'][0]

        # TODO the parsing around paragraphs and bullet points needs some
        # work.
        if block[b'underline'] == b'=':  # main section
            name = sections.sectionfromtitle(title)
            if not name:
                raise error.Abort(
                    _(b'unknown release notes section: %s') % title
                )

            currentsection = name
            bullet_points = gatherparagraphsbullets(i)
            if bullet_points:
                for para in bullet_points:
                    notes.addnontitleditem(currentsection, para)

        elif block[b'underline'] == b'-':  # sub-section
            if title == BULLET_SECTION:
                bullet_points = gatherparagraphsbullets(i)
                for para in bullet_points:
                    notes.addnontitleditem(currentsection, para)
            else:
                paragraphs = gatherparagraphsbullets(i, True)
                notes.addtitleditem(currentsection, title, paragraphs)
        else:
            raise error.Abort(_(b'unsupported section type for %s') % title)

    return notes


def serializenotes(sections, notes):
    """Serialize release notes from parsed fragments and notes.

    This function essentially takes the output of ``parsenotesfromrevisions()``
    and ``parserelnotesfile()`` and produces output combining the 2.
    """
    lines = []

    for sectionname, sectiontitle in sections:
        if sectionname not in notes:
            continue

        lines.append(sectiontitle)
        lines.append(b'=' * len(sectiontitle))
        lines.append(b'')

        # First pass to emit sub-sections.
        for title, paragraphs in notes.titledforsection(sectionname):
            lines.append(title)
            lines.append(b'-' * len(title))
            lines.append(b'')

            for i, para in enumerate(paragraphs):
                if i:
                    lines.append(b'')
                lines.extend(
                    stringutil.wrap(b' '.join(para), width=78).splitlines()
                )

            lines.append(b'')

        # Second pass to emit bullet list items.

        # If the section has titled and non-titled items, we can't
        # simply emit the bullet list because it would appear to come
        # from the last title/section. So, we emit a new sub-section
        # for the non-titled items.
        nontitled = notes.nontitledforsection(sectionname)
        if notes.titledforsection(sectionname) and nontitled:
            # TODO make configurable.
            lines.append(BULLET_SECTION)
            lines.append(b'-' * len(BULLET_SECTION))
            lines.append(b'')

        for paragraphs in nontitled:
            lines.extend(
                stringutil.wrap(
                    b' '.join(paragraphs[0]),
                    width=78,
                    initindent=b'* ',
                    hangindent=b'  ',
                ).splitlines()
            )

            for para in paragraphs[1:]:
                lines.append(b'')
                lines.extend(
                    stringutil.wrap(
                        b' '.join(para),
                        width=78,
                        initindent=b'  ',
                        hangindent=b'  ',
                    ).splitlines()
                )

            lines.append(b'')

    if lines and lines[-1]:
        lines.append(b'')

    return b'\n'.join(lines)


@command(
    b'releasenotes',
    [
        (
            b'r',
            b'rev',
            b'',
            _(b'revisions to process for release notes'),
            _(b'REV'),
        ),
        (
            b'c',
            b'check',
            False,
            _(b'checks for validity of admonitions (if any)'),
            _(b'REV'),
        ),
        (
            b'l',
            b'list',
            False,
            _(b'list the available admonitions with their title'),
            None,
        ),
    ],
    _(b'hg releasenotes [-r REV] [-c] FILE'),
    helpcategory=command.CATEGORY_CHANGE_NAVIGATION,
)
def releasenotes(ui, repo, file_=None, **opts):
    """parse release notes from commit messages into an output file

    Given an output file and set of revisions, this command will parse commit
    messages for release notes then add them to the output file.

    Release notes are defined in commit messages as ReStructuredText
    directives. These have the form::

       .. directive:: title

          content

    Each ``directive`` maps to an output section in a generated release notes
    file, which itself is ReStructuredText. For example, the ``.. feature::``
    directive would map to a ``New Features`` section.

    Release note directives can be either short-form or long-form. In short-
    form, ``title`` is omitted and the release note is rendered as a bullet
    list. In long form, a sub-section with the title ``title`` is added to the
    section.

    The ``FILE`` argument controls the output file to write gathered release
    notes to. The format of the file is::

       Section 1
       =========

       ...

       Section 2
       =========

       ...

    Only sections with defined release notes are emitted.

    If a section only has short-form notes, it will consist of bullet list::

       Section
       =======

       * Release note 1
       * Release note 2

    If a section has long-form notes, sub-sections will be emitted::

       Section
       =======

       Note 1 Title
       ------------

       Description of the first long-form note.

       Note 2 Title
       ------------

       Description of the second long-form note.

    If the ``FILE`` argument points to an existing file, that file will be
    parsed for release notes having the format that would be generated by this
    command. The notes from the processed commit messages will be *merged*
    into this parsed set.

    During release notes merging:

    * Duplicate items are automatically ignored
    * Items that are different are automatically ignored if the similarity is
      greater than a threshold.

    This means that the release notes file can be updated independently from
    this command and changes should not be lost when running this command on
    that file. A particular use case for this is to tweak the wording of a
    release note after it has been added to the release notes file.

    The -c/--check option checks the commit message for invalid admonitions.

    The -l/--list option, presents the user with a list of existing available
    admonitions along with their title. This also includes the custom
    admonitions (if any).
    """

    opts = pycompat.byteskwargs(opts)
    sections = releasenotessections(ui, repo)

    cmdutil.check_incompatible_arguments(opts, b'list', [b'rev', b'check'])

    if opts.get(b'list'):
        return _getadmonitionlist(ui, sections)

    rev = opts.get(b'rev')
    revs = scmutil.revrange(repo, [rev or b'not public()'])
    if opts.get(b'check'):
        return checkadmonitions(ui, repo, sections.names(), revs)

    incoming = parsenotesfromrevisions(repo, sections.names(), revs)

    if file_ is None:
        ui.pager(b'releasenotes')
        return ui.write(serializenotes(sections, incoming))

    try:
        with open(file_, b'rb') as fh:
            notes = parsereleasenotesfile(sections, fh.read())
    except IOError as e:
        if e.errno != errno.ENOENT:
            raise

        notes = parsedreleasenotes()

    notes.merge(ui, incoming)

    with open(file_, b'wb') as fh:
        fh.write(serializenotes(sections, notes))


@command(b'debugparsereleasenotes', norepo=True)
def debugparsereleasenotes(ui, path, repo=None):
    """parse release notes and print resulting data structure"""
    if path == b'-':
        text = procutil.stdin.read()
    else:
        with open(path, b'rb') as fh:
            text = fh.read()

    sections = releasenotessections(ui, repo)

    notes = parsereleasenotesfile(sections, text)

    for section in notes:
        ui.write(_(b'section: %s\n') % section)
        for title, paragraphs in notes.titledforsection(section):
            ui.write(_(b'  subsection: %s\n') % title)
            for para in paragraphs:
                ui.write(_(b'    paragraph: %s\n') % b' '.join(para))

        for paragraphs in notes.nontitledforsection(section):
            ui.write(_(b'  bullet point:\n'))
            for para in paragraphs:
                ui.write(_(b'    paragraph: %s\n') % b' '.join(para))
