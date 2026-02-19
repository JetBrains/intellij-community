# help.py - help data for mercurial
#
# Copyright 2006 Olivia Mackall <olivia@selenic.com>
#
# This software may be used and distributed according to the terms of the
# GNU General Public License version 2 or any later version.


import itertools
import re
import textwrap

from typing import (
    Callable,
    Dict,
    Iterable,
    List,
    Optional,
    Set,
    Tuple,
    Union,
    cast,
)

from .i18n import (
    _,
    gettext,
)
from . import (
    cmdutil,
    encoding,
    error,
    extensions,
    fancyopts,
    filemerge,
    fileset,
    minirst,
    pycompat,
    registrar,
    revset,
    templatefilters,
    templatefuncs,
    templatekw,
    ui as uimod,
)
from .hgweb import webcommands
from .utils import (
    compression,
    resourceutil,
    stringutil,
)

_DocLoader = Callable[[uimod.ui], bytes]
# Old extensions may not register with a category
_HelpEntry = Union["_HelpEntryNoCategory", "_HelpEntryWithCategory"]
_HelpEntryNoCategory = Tuple[List[bytes], bytes, _DocLoader]
_HelpEntryWithCategory = Tuple[List[bytes], bytes, _DocLoader, bytes]
_SelectFn = Callable[[object], bool]
_SynonymTable = Dict[bytes, List[bytes]]
_TopicHook = Callable[[uimod.ui, bytes, bytes], bytes]

_exclkeywords: Set[bytes] = {
    b"(ADVANCED)",
    b"(DEPRECATED)",
    b"(EXPERIMENTAL)",
    # i18n: "(ADVANCED)" is a keyword, must be translated consistently
    _(b"(ADVANCED)"),
    # i18n: "(DEPRECATED)" is a keyword, must be translated consistently
    _(b"(DEPRECATED)"),
    # i18n: "(EXPERIMENTAL)" is a keyword, must be translated consistently
    _(b"(EXPERIMENTAL)"),
}

# The order in which command categories will be displayed.
# Extensions with custom categories should insert them into this list
# after/before the appropriate item, rather than replacing the list or
# assuming absolute positions.
CATEGORY_ORDER: List[bytes] = [
    registrar.command.CATEGORY_REPO_CREATION,
    registrar.command.CATEGORY_REMOTE_REPO_MANAGEMENT,
    registrar.command.CATEGORY_COMMITTING,
    registrar.command.CATEGORY_CHANGE_MANAGEMENT,
    registrar.command.CATEGORY_CHANGE_ORGANIZATION,
    registrar.command.CATEGORY_FILE_CONTENTS,
    registrar.command.CATEGORY_CHANGE_NAVIGATION,
    registrar.command.CATEGORY_WORKING_DIRECTORY,
    registrar.command.CATEGORY_IMPORT_EXPORT,
    registrar.command.CATEGORY_MAINTENANCE,
    registrar.command.CATEGORY_HELP,
    registrar.command.CATEGORY_MISC,
    registrar.command.CATEGORY_NONE,
]

# Human-readable category names. These are translated.
# Extensions with custom categories should add their names here.
CATEGORY_NAMES: Dict[bytes, bytes] = {
    registrar.command.CATEGORY_REPO_CREATION: b'Repository creation',
    registrar.command.CATEGORY_REMOTE_REPO_MANAGEMENT: b'Remote repository management',
    registrar.command.CATEGORY_COMMITTING: b'Change creation',
    registrar.command.CATEGORY_CHANGE_NAVIGATION: b'Change navigation',
    registrar.command.CATEGORY_CHANGE_MANAGEMENT: b'Change manipulation',
    registrar.command.CATEGORY_CHANGE_ORGANIZATION: b'Change organization',
    registrar.command.CATEGORY_WORKING_DIRECTORY: b'Working directory management',
    registrar.command.CATEGORY_FILE_CONTENTS: b'File content management',
    registrar.command.CATEGORY_IMPORT_EXPORT: b'Change import/export',
    registrar.command.CATEGORY_MAINTENANCE: b'Repository maintenance',
    registrar.command.CATEGORY_HELP: b'Help',
    registrar.command.CATEGORY_MISC: b'Miscellaneous commands',
    registrar.command.CATEGORY_NONE: b'Uncategorized commands',
}

# Topic categories.
TOPIC_CATEGORY_IDS = b'ids'
TOPIC_CATEGORY_OUTPUT = b'output'
TOPIC_CATEGORY_CONFIG = b'config'
TOPIC_CATEGORY_CONCEPTS = b'concepts'
TOPIC_CATEGORY_MISC = b'misc'
TOPIC_CATEGORY_NONE = b'none'

# The order in which topic categories will be displayed.
# Extensions with custom categories should insert them into this list
# after/before the appropriate item, rather than replacing the list or
# assuming absolute positions.
TOPIC_CATEGORY_ORDER: List[bytes] = [
    TOPIC_CATEGORY_IDS,
    TOPIC_CATEGORY_OUTPUT,
    TOPIC_CATEGORY_CONFIG,
    TOPIC_CATEGORY_CONCEPTS,
    TOPIC_CATEGORY_MISC,
    TOPIC_CATEGORY_NONE,
]

# Human-readable topic category names. These are translated.
TOPIC_CATEGORY_NAMES: Dict[bytes, bytes] = {
    TOPIC_CATEGORY_IDS: b'Mercurial identifiers',
    TOPIC_CATEGORY_OUTPUT: b'Mercurial output',
    TOPIC_CATEGORY_CONFIG: b'Mercurial configuration',
    TOPIC_CATEGORY_CONCEPTS: b'Concepts',
    TOPIC_CATEGORY_MISC: b'Miscellaneous',
    TOPIC_CATEGORY_NONE: b'Uncategorized topics',
}


def listexts(
    header: bytes,
    exts: Dict[bytes, bytes],
    indent: int = 1,
    showdeprecated: bool = False,
) -> List[bytes]:
    '''return a text listing of the given extensions'''
    rst = []
    if exts:
        for name, desc in sorted(exts.items()):
            if not showdeprecated and any(w in desc for w in _exclkeywords):
                continue
            rst.append(b'%s:%s: %s\n' % (b' ' * indent, name, desc))
    if rst:
        rst.insert(0, b'\n%s\n\n' % header)
    return rst


def extshelp(ui: uimod.ui) -> bytes:
    rst = loaddoc(b'extensions')(ui).splitlines(True)
    rst.extend(
        listexts(
            _(b'enabled extensions:'), extensions.enabled(), showdeprecated=True
        )
    )
    rst.extend(
        listexts(
            _(b'disabled extensions:'),
            extensions.disabled(),
            showdeprecated=ui.verbose,
        )
    )
    doc = b''.join(rst)
    return doc


def parsedefaultmarker(text: bytes) -> Optional[Tuple[bytes, List[bytes]]]:
    """given a text 'abc (DEFAULT: def.ghi)',
    returns (b'abc', (b'def', b'ghi')). Otherwise return None"""
    if text[-1:] == b')':
        marker = b' (DEFAULT: '
        pos = text.find(marker)
        if pos >= 0:
            item = text[pos + len(marker) : -1]
            return text[:pos], item.split(b'.', 2)


def optrst(header: bytes, options, verbose: bool, ui: uimod.ui) -> bytes:
    data = []
    multioccur = False
    for option in options:
        if len(option) == 5:
            shortopt, longopt, default, desc, optlabel = option
        else:
            shortopt, longopt, default, desc = option
            optlabel = _(b"VALUE")  # default label

        if not verbose and any(w in desc for w in _exclkeywords):
            continue
        defaultstrsuffix = b''
        if default is None:
            parseresult = parsedefaultmarker(desc)
            if parseresult is not None:
                (desc, (section, name)) = parseresult
                if ui.configbool(section, name):
                    default = True
                    defaultstrsuffix = _(b' from config')
        so = b''
        if shortopt:
            so = b'-' + shortopt
        lo = b'--' + longopt
        if default is True:
            lo = b'--[no-]' + longopt

        if isinstance(default, fancyopts.customopt):
            default = default.getdefaultvalue()
        if default and not callable(default):
            # default is of unknown type, and in Python 2 we abused
            # the %s-shows-repr property to handle integers etc. To
            # match that behavior on Python 3, we do str(default) and
            # then convert it to bytes.
            defaultstr = pycompat.bytestr(default)
            if default is True:
                defaultstr = _(b"on")
            desc += _(b" (default: %s)") % (defaultstr + defaultstrsuffix)

        if isinstance(default, list):
            lo += b" %s [+]" % optlabel
            multioccur = True
        elif (default is not None) and not isinstance(default, bool):
            lo += b" %s" % optlabel

        data.append((so, lo, desc))

    if multioccur:
        header += _(b" ([+] can be repeated)")

    rst = [b'\n%s:\n\n' % header]
    rst.extend(minirst.maketable(data, 1))

    return b''.join(rst)


def indicateomitted(
    rst: List[bytes], omitted: bytes, notomitted: Optional[bytes] = None
) -> None:
    rst.append(b'\n\n.. container:: omitted\n\n    %s\n\n' % omitted)
    if notomitted:
        rst.append(b'\n\n.. container:: notomitted\n\n    %s\n\n' % notomitted)


def filtercmd(ui: uimod.ui, cmd: bytes, func, kw: bytes, doc: bytes) -> bool:
    if not ui.debugflag and cmd.startswith(b"debug") and kw != b"debug":
        # Debug command, and user is not looking for those.
        return True
    if not ui.verbose:
        if not kw and not doc:
            # Command had no documentation, no point in showing it by default.
            return True
        if getattr(func, 'alias', False) and not getattr(func, 'owndoc', False):
            # Alias didn't have its own documentation.
            return True
        if doc and any(w in doc for w in _exclkeywords):
            # Documentation has excluded keywords.
            return True
    if kw == b"shortlist" and not getattr(func, 'helpbasic', False):
        # We're presenting the short list but the command is not basic.
        return True
    if ui.configbool(b'help', b'hidden-command.%s' % cmd):
        # Configuration explicitly hides the command.
        return True
    return False


def filtertopic(ui: uimod.ui, topic: bytes) -> bool:
    return ui.configbool(b'help', b'hidden-topic.%s' % topic, False)


def topicmatch(
    ui: uimod.ui, commands, kw: bytes
) -> Dict[bytes, List[Tuple[bytes, bytes]]]:
    """Return help topics matching kw.

    Returns {'section': [(name, summary), ...], ...} where section is
    one of topics, commands, extensions, or extensioncommands.
    """
    kw = encoding.lower(kw)

    def lowercontains(container):
        return kw in encoding.lower(container)  # translated in helptable

    results = {
        b'topics': [],
        b'commands': [],
        b'extensions': [],
        b'extensioncommands': [],
    }
    for topic in helptable:
        names, header, doc = topic[0:3]
        # Old extensions may use a str as doc.
        if (
            sum(map(lowercontains, names))
            or lowercontains(header)
            or (callable(doc) and lowercontains(doc(ui)))
        ):
            name = names[0]
            if not filtertopic(ui, name):
                results[b'topics'].append((names[0], header))
    for cmd, entry in commands.table.items():
        if len(entry) == 3:
            summary = entry[2]
        else:
            summary = b''
        # translate docs *before* searching there
        func = entry[0]
        docs = _(pycompat.getdoc(func)) or b''
        if kw in cmd or lowercontains(summary) or lowercontains(docs):
            if docs:
                summary = stringutil.firstline(docs)
            cmdname = cmdutil.parsealiases(cmd)[0]
            if filtercmd(ui, cmdname, func, kw, docs):
                continue
            results[b'commands'].append((cmdname, summary))
    for name, docs in itertools.chain(
        extensions.enabled(False).items(),
        extensions.disabled().items(),
    ):
        if not docs:
            continue
        name = name.rpartition(b'.')[-1]
        if lowercontains(name) or lowercontains(docs):
            # extension docs are already translated
            results[b'extensions'].append((name, stringutil.firstline(docs)))
        try:
            mod = extensions.load(ui, name, b'')
        except ImportError:
            # debug message would be printed in extensions.load()
            continue
        for cmd, entry in getattr(mod, 'cmdtable', {}).items():
            if kw in cmd or (len(entry) > 2 and lowercontains(entry[2])):
                cmdname = cmdutil.parsealiases(cmd)[0]
                func = entry[0]
                cmddoc = pycompat.getdoc(func)
                if cmddoc:
                    cmddoc = stringutil.firstline(gettext(cmddoc))
                else:
                    cmddoc = _(b'(no help text available)')
                if filtercmd(ui, cmdname, func, kw, cmddoc):
                    continue
                results[b'extensioncommands'].append((cmdname, cmddoc))
    return results


def loaddoc(topic: bytes, subdir: Optional[bytes] = None) -> _DocLoader:
    """Return a delayed loader for help/topic.txt."""

    def loader(ui: uimod.ui) -> bytes:
        package = b'mercurial.helptext'
        if subdir:
            package += b'.' + subdir
        with resourceutil.open_resource(package, topic + b'.txt') as fp:
            doc = gettext(fp.read())
        for rewriter in helphooks.get(topic, []):
            doc = rewriter(ui, topic, doc)
        return doc

    return loader


internalstable: List[_HelpEntryNoCategory] = sorted(
    [
        (
            [b'bid-merge'],
            _(b'Bid Merge Algorithm'),
            loaddoc(b'bid-merge', subdir=b'internals'),
        ),
        ([b'bundle2'], _(b'Bundle2'), loaddoc(b'bundle2', subdir=b'internals')),
        ([b'bundles'], _(b'Bundles'), loaddoc(b'bundles', subdir=b'internals')),
        ([b'cbor'], _(b'CBOR'), loaddoc(b'cbor', subdir=b'internals')),
        ([b'censor'], _(b'Censor'), loaddoc(b'censor', subdir=b'internals')),
        (
            [b'changegroups'],
            _(b'Changegroups'),
            loaddoc(b'changegroups', subdir=b'internals'),
        ),
        (
            [b'config'],
            _(b'Config Registrar'),
            loaddoc(b'config', subdir=b'internals'),
        ),
        (
            [b'dirstate-v2'],
            _(b'dirstate-v2 file format'),
            loaddoc(b'dirstate-v2', subdir=b'internals'),
        ),
        (
            [b'extensions', b'extension'],
            _(b'Extension API'),
            loaddoc(b'extensions', subdir=b'internals'),
        ),
        (
            [b'mergestate'],
            _(b'Mergestate'),
            loaddoc(b'mergestate', subdir=b'internals'),
        ),
        (
            [b'requirements'],
            _(b'Repository Requirements'),
            loaddoc(b'requirements', subdir=b'internals'),
        ),
        (
            [b'revlogs'],
            _(b'Revision Logs'),
            loaddoc(b'revlogs', subdir=b'internals'),
        ),
        (
            [b'wireprotocol'],
            _(b'Wire Protocol'),
            loaddoc(b'wireprotocol', subdir=b'internals'),
        ),
        (
            [b'wireprotocolrpc'],
            _(b'Wire Protocol RPC'),
            loaddoc(b'wireprotocolrpc', subdir=b'internals'),
        ),
        (
            [b'wireprotocolv2'],
            _(b'Wire Protocol Version 2'),
            loaddoc(b'wireprotocolv2', subdir=b'internals'),
        ),
    ]
)


def internalshelp(ui: uimod.ui) -> bytes:
    """Generate the index for the "internals" topic."""
    lines = [
        b'To access a subtopic, use "hg help internals.{subtopic-name}"\n',
        b'\n',
    ]
    for names, header, doc in internalstable:
        lines.append(b' :%s: %s\n' % (names[0], header))

    return b''.join(lines)


helptable: List[_HelpEntryWithCategory] = sorted(
    [
        (
            [b'bundlespec'],
            _(b"Bundle File Formats"),
            loaddoc(b'bundlespec'),
            TOPIC_CATEGORY_CONCEPTS,
        ),
        (
            [b'color'],
            _(b"Colorizing Outputs"),
            loaddoc(b'color'),
            TOPIC_CATEGORY_OUTPUT,
        ),
        (
            [b"config", b"hgrc"],
            _(b"Configuration Files"),
            loaddoc(b'config'),
            TOPIC_CATEGORY_CONFIG,
        ),
        (
            [b'deprecated'],
            _(b"Deprecated Features"),
            loaddoc(b'deprecated'),
            TOPIC_CATEGORY_MISC,
        ),
        (
            [b"dates"],
            _(b"Date Formats"),
            loaddoc(b'dates'),
            TOPIC_CATEGORY_OUTPUT,
        ),
        (
            [b"flags"],
            _(b"Command-line flags"),
            loaddoc(b'flags'),
            TOPIC_CATEGORY_CONFIG,
        ),
        (
            [b"patterns"],
            _(b"File Name Patterns"),
            loaddoc(b'patterns'),
            TOPIC_CATEGORY_IDS,
        ),
        (
            [b'environment', b'env'],
            _(b'Environment Variables'),
            loaddoc(b'environment'),
            TOPIC_CATEGORY_CONFIG,
        ),
        (
            [
                b'revisions',
                b'revs',
                b'revsets',
                b'revset',
                b'multirevs',
                b'mrevs',
            ],
            _(b'Specifying Revisions'),
            loaddoc(b'revisions'),
            TOPIC_CATEGORY_IDS,
        ),
        (
            [
                b'rust',
                b'rustext',
                b'rhg',
            ],
            _(b'Rust in Mercurial'),
            loaddoc(b'rust'),
            TOPIC_CATEGORY_CONFIG,
        ),
        (
            [b'filesets', b'fileset'],
            _(b"Specifying File Sets"),
            loaddoc(b'filesets'),
            TOPIC_CATEGORY_IDS,
        ),
        (
            [b'diffs'],
            _(b'Diff Formats'),
            loaddoc(b'diffs'),
            TOPIC_CATEGORY_OUTPUT,
        ),
        (
            [b'merge-tools', b'mergetools', b'mergetool'],
            _(b'Merge Tools'),
            loaddoc(b'merge-tools'),
            TOPIC_CATEGORY_CONFIG,
        ),
        (
            [b'templating', b'templates', b'template', b'style'],
            _(b'Template Usage'),
            loaddoc(b'templates'),
            TOPIC_CATEGORY_OUTPUT,
        ),
        ([b'urls'], _(b'URL Paths'), loaddoc(b'urls'), TOPIC_CATEGORY_IDS),
        (
            [b"extensions"],
            _(b"Using Additional Features"),
            extshelp,
            TOPIC_CATEGORY_CONFIG,
        ),
        (
            [b"subrepos", b"subrepo"],
            _(b"Subrepositories"),
            loaddoc(b'subrepos'),
            TOPIC_CATEGORY_CONCEPTS,
        ),
        (
            [b"hgweb"],
            _(b"Configuring hgweb"),
            loaddoc(b'hgweb'),
            TOPIC_CATEGORY_CONFIG,
        ),
        (
            [b"glossary"],
            _(b"Glossary"),
            loaddoc(b'glossary'),
            TOPIC_CATEGORY_CONCEPTS,
        ),
        (
            [b"hgignore", b"ignore"],
            _(b"Syntax for Mercurial Ignore Files"),
            loaddoc(b'hgignore'),
            TOPIC_CATEGORY_IDS,
        ),
        (
            [b"phases"],
            _(b"Working with Phases"),
            loaddoc(b'phases'),
            TOPIC_CATEGORY_CONCEPTS,
        ),
        (
            [b"evolution"],
            _(b"Safely rewriting history (EXPERIMENTAL)"),
            loaddoc(b'evolution'),
            TOPIC_CATEGORY_CONCEPTS,
        ),
        (
            [b'scripting'],
            _(b'Using Mercurial from scripts and automation'),
            loaddoc(b'scripting'),
            TOPIC_CATEGORY_MISC,
        ),
        (
            [b'internals'],
            _(b"Technical implementation topics"),
            internalshelp,
            TOPIC_CATEGORY_MISC,
        ),
        (
            [b'pager'],
            _(b"Pager Support"),
            loaddoc(b'pager'),
            TOPIC_CATEGORY_CONFIG,
        ),
    ]
)

# Maps topics with sub-topics to a list of their sub-topics.
subtopics: Dict[bytes, List[_HelpEntryNoCategory]] = {
    b'internals': internalstable,
}

# Map topics to lists of callable taking the current topic help and
# returning the updated version
helphooks: Dict[bytes, List[_TopicHook]] = {}


def addtopichook(topic: bytes, rewriter: _TopicHook) -> None:
    helphooks.setdefault(topic, []).append(rewriter)


def makeitemsdoc(
    ui: uimod.ui,
    topic: bytes,
    doc: bytes,
    marker: bytes,
    items: Dict[bytes, bytes],
    dedent: bool = False,
) -> bytes:
    """Extract docstring from the items key to function mapping, build a
    single documentation block and use it to overwrite the marker in doc.
    """
    entries = []
    for name in sorted(items):
        text = (pycompat.getdoc(items[name]) or b'').rstrip()
        if not text or not ui.verbose and any(w in text for w in _exclkeywords):
            continue
        text = gettext(text)
        if dedent:
            # Abuse latin1 to use textwrap.dedent() on bytes.
            text = textwrap.dedent(text.decode('latin1')).encode('latin1')
        lines = text.splitlines()
        doclines = [lines[0]]
        for l in lines[1:]:
            # Stop once we find some Python doctest
            if l.strip().startswith(b'>>>'):
                break
            if dedent:
                doclines.append(l.rstrip())
            else:
                doclines.append(b'  ' + l.strip())
        entries.append(b'\n'.join(doclines))
    entries = b'\n\n'.join(entries)
    return doc.replace(marker, entries)


def addtopicsymbols(
    topic: bytes, marker: bytes, symbols, dedent: bool = False
) -> None:
    def add(ui: uimod.ui, topic: bytes, doc: bytes):
        return makeitemsdoc(ui, topic, doc, marker, symbols, dedent=dedent)

    addtopichook(topic, add)


addtopicsymbols(
    b'bundlespec',
    b'.. bundlecompressionmarker',
    compression.bundlecompressiontopics(),
)
addtopicsymbols(b'filesets', b'.. predicatesmarker', fileset.symbols)
addtopicsymbols(
    b'merge-tools', b'.. internaltoolsmarker', filemerge.internalsdoc
)
addtopicsymbols(b'revisions', b'.. predicatesmarker', revset.symbols)
addtopicsymbols(b'templates', b'.. keywordsmarker', templatekw.keywords)
addtopicsymbols(b'templates', b'.. filtersmarker', templatefilters.filters)
addtopicsymbols(b'templates', b'.. functionsmarker', templatefuncs.funcs)
addtopicsymbols(
    b'hgweb', b'.. webcommandsmarker', webcommands.commands, dedent=True
)


def inserttweakrc(ui: uimod.ui, topic: bytes, doc: bytes) -> bytes:
    marker = b'.. tweakdefaultsmarker'
    repl = uimod.tweakrc

    def sub(m):
        lines = [m.group(1) + s for s in repl.splitlines()]
        return b'\n'.join(lines)

    return re.sub(br'( *)%s' % re.escape(marker), sub, doc)


def _getcategorizedhelpcmds(
    ui: uimod.ui, cmdtable, name: bytes, select: Optional[_SelectFn] = None
) -> Tuple[Dict[bytes, List[bytes]], Dict[bytes, bytes], _SynonymTable]:
    # Category -> list of commands
    cats = {}
    # Command -> short description
    h = {}
    # Command -> string showing synonyms
    syns = {}
    for c, e in cmdtable.items():
        fs = cmdutil.parsealiases(c)
        f = fs[0]
        syns[f] = fs
        func = e[0]
        if select and not select(f):
            continue
        doc = pycompat.getdoc(func)
        if filtercmd(ui, f, func, name, doc):
            continue
        doc = gettext(doc)
        if not doc:
            doc = _(b"(no help text available)")
        h[f] = stringutil.firstline(doc).rstrip()

        cat = getattr(func, 'helpcategory', None) or (
            registrar.command.CATEGORY_NONE
        )
        cats.setdefault(cat, []).append(f)
    return cats, h, syns


def _getcategorizedhelptopics(
    ui: uimod.ui, topictable: List[_HelpEntry]
) -> Tuple[Dict[bytes, List[Tuple[bytes, bytes]]], Dict[bytes, List[bytes]]]:
    # Group commands by category.
    topiccats = {}
    syns = {}
    for topic in topictable:
        names, header, doc = topic[0:3]
        if len(topic) > 3 and topic[3]:
            category: bytes = cast(bytes, topic[3])  # help pytype
        else:
            category: bytes = TOPIC_CATEGORY_NONE

        topicname = names[0]
        syns[topicname] = list(names)
        if not filtertopic(ui, topicname):
            topiccats.setdefault(category, []).append((topicname, header))
    return topiccats, syns


addtopichook(b'config', inserttweakrc)


def help_(
    ui: uimod.ui,
    commands,
    name: bytes,
    unknowncmd: bool = False,
    full: bool = True,
    subtopic: Optional[bytes] = None,
    fullname: Optional[bytes] = None,
    **opts
) -> bytes:
    """
    Generate the help for 'name' as unformatted restructured text. If
    'name' is None, describe the commands available.
    """

    opts = pycompat.byteskwargs(opts)

    def helpcmd(name: bytes, subtopic: Optional[bytes]) -> List[bytes]:
        try:
            aliases, entry = cmdutil.findcmd(
                name, commands.table, strict=unknowncmd
            )
        except error.AmbiguousCommand as inst:
            # py3 fix: except vars can't be used outside the scope of the
            # except block, nor can be used inside a lambda. python issue4617
            prefix = inst.prefix
            select = lambda c: cmdutil.parsealiases(c)[0].startswith(prefix)
            rst = helplist(select)
            return rst

        rst = []

        # check if it's an invalid alias and display its error if it is
        if getattr(entry[0], 'badalias', None):
            rst.append(entry[0].badalias + b'\n')
            if entry[0].unknowncmd:
                try:
                    rst.extend(helpextcmd(entry[0].cmdname))
                except error.UnknownCommand:
                    pass
            return rst

        # synopsis
        if len(entry) > 2:
            if entry[2].startswith(b'hg'):
                rst.append(b"%s\n" % entry[2])
            else:
                rst.append(b'hg %s %s\n' % (aliases[0], entry[2]))
        else:
            rst.append(b'hg %s\n' % aliases[0])
        # aliases
        if full and not ui.quiet and len(aliases) > 1:
            rst.append(_(b"\naliases: %s\n") % b', '.join(aliases[1:]))
        rst.append(b'\n')

        # description
        doc = gettext(pycompat.getdoc(entry[0]))
        if not doc:
            doc = _(b"(no help text available)")
        if hasattr(entry[0], 'definition'):  # aliased command
            source = entry[0].source
            if entry[0].definition.startswith(b'!'):  # shell alias
                doc = _(b'shell alias for: %s\n\n%s\n\ndefined by: %s\n') % (
                    entry[0].definition[1:],
                    doc,
                    source,
                )
            else:
                doc = _(b'alias for: hg %s\n\n%s\n\ndefined by: %s\n') % (
                    entry[0].definition,
                    doc,
                    source,
                )
        doc = doc.splitlines(True)
        if ui.quiet or not full:
            rst.append(doc[0])
        else:
            rst.extend(doc)
        rst.append(b'\n')

        # check if this command shadows a non-trivial (multi-line)
        # extension help text
        try:
            mod = extensions.find(name)
            doc = gettext(pycompat.getdoc(mod)) or b''
            if b'\n' in doc.strip():
                msg = _(
                    b"(use 'hg help -e %s' to show help for "
                    b"the %s extension)"
                ) % (name, name)
                rst.append(b'\n%s\n' % msg)
        except KeyError:
            pass

        # options
        if not ui.quiet and entry[1]:
            rst.append(optrst(_(b"options"), entry[1], ui.verbose, ui))

        if ui.verbose:
            rst.append(
                optrst(
                    _(b"global options"), commands.globalopts, ui.verbose, ui
                )
            )

        if not ui.verbose:
            if not full:
                rst.append(_(b"\n(use 'hg %s -h' to show more help)\n") % name)
            elif not ui.quiet:
                rst.append(
                    _(
                        b'\n(some details hidden, use --verbose '
                        b'to show complete help)'
                    )
                )

        return rst

    def helplist(select: Optional[_SelectFn] = None, **opts) -> List[bytes]:
        cats, h, syns = _getcategorizedhelpcmds(
            ui, commands.table, name, select
        )

        rst = []
        if not h:
            if not ui.quiet:
                rst.append(_(b'no commands defined\n'))
            return rst

        # Output top header.
        if not ui.quiet:
            if name == b"shortlist":
                rst.append(_(b'basic commands:\n\n'))
            elif name == b"debug":
                rst.append(_(b'debug commands (internal and unsupported):\n\n'))
            else:
                rst.append(_(b'list of commands:\n'))

        def appendcmds(cmds: Iterable[bytes]) -> None:
            cmds = sorted(cmds)
            for c in cmds:
                display_cmd = c
                if ui.verbose:
                    display_cmd = b', '.join(syns[c])
                display_cmd = display_cmd.replace(b':', br'\:')
                rst.append(b' :%s: %s\n' % (display_cmd, h[c]))

        if name in (b'shortlist', b'debug'):
            # List without categories.
            appendcmds(h)
        else:
            # Check that all categories have an order.
            missing_order = set(cats.keys()) - set(CATEGORY_ORDER)
            if missing_order:
                ui.develwarn(
                    b'help categories missing from CATEGORY_ORDER: %s'
                    % stringutil.forcebytestr(missing_order)
                )

            # List per category.
            for cat in CATEGORY_ORDER:
                catfns = cats.get(cat, [])
                if catfns:
                    if len(cats) > 1:
                        catname = gettext(CATEGORY_NAMES[cat])
                        rst.append(b"\n%s:\n" % catname)
                    rst.append(b"\n")
                    appendcmds(catfns)

        ex = opts.get
        anyopts = ex('keyword') or not (ex('command') or ex('extension'))
        if not name and anyopts:
            exts = listexts(
                _(b'enabled extensions:'),
                extensions.enabled(),
                showdeprecated=ui.verbose,
            )
            if exts:
                rst.append(b'\n')
                rst.extend(exts)

            rst.append(_(b"\nadditional help topics:\n"))
            topiccats, topicsyns = _getcategorizedhelptopics(ui, helptable)

            # Check that all categories have an order.
            missing_order = set(topiccats.keys()) - set(TOPIC_CATEGORY_ORDER)
            if missing_order:
                ui.develwarn(
                    b'help categories missing from TOPIC_CATEGORY_ORDER: %s'
                    % stringutil.forcebytestr(missing_order)
                )

            # Output topics per category.
            for cat in TOPIC_CATEGORY_ORDER:
                topics = topiccats.get(cat, [])
                if topics:
                    if len(topiccats) > 1:
                        catname = gettext(TOPIC_CATEGORY_NAMES[cat])
                        rst.append(b"\n%s:\n" % catname)
                    rst.append(b"\n")
                    for t, desc in topics:
                        rst.append(b" :%s: %s\n" % (t, desc))

        if ui.quiet:
            pass
        elif ui.verbose:
            rst.append(
                b'\n%s\n'
                % optrst(
                    _(b"global options"), commands.globalopts, ui.verbose, ui
                )
            )
            if name == b'shortlist':
                rst.append(
                    _(b"\n(use 'hg help' for the full list of commands)\n")
                )
        else:
            if name == b'shortlist':
                rst.append(
                    _(
                        b"\n(use 'hg help' for the full list of commands "
                        b"or 'hg -v' for details)\n"
                    )
                )
            elif name and not full:
                rst.append(
                    _(b"\n(use 'hg help %s' to show the full help text)\n")
                    % name
                )
            elif name and syns and name in syns.keys():
                rst.append(
                    _(
                        b"\n(use 'hg help -v -e %s' to show built-in "
                        b"aliases and global options)\n"
                    )
                    % name
                )
            else:
                rst.append(
                    _(
                        b"\n(use 'hg help -v%s' to show built-in aliases "
                        b"and global options)\n"
                    )
                    % (name and b" " + name or b"")
                )
        return rst

    def helptopic(name: bytes, subtopic: Optional[bytes] = None) -> List[bytes]:
        # Look for sub-topic entry first.
        header, doc = None, None
        if subtopic and name in subtopics:
            for names, header, doc in subtopics[name]:
                if subtopic in names:
                    break
            if not any(subtopic in s[0] for s in subtopics[name]):
                raise error.UnknownCommand(name)

        if not header:
            for topic in helptable:
                names, header, doc = topic[0:3]
                if name in names:
                    break
            else:
                raise error.UnknownCommand(name)

        rst = [minirst.section(header)]

        # description
        if not doc:
            rst.append(b"    %s\n" % _(b"(no help text available)"))
        if callable(doc):
            rst += [b"    %s\n" % l for l in doc(ui).splitlines()]

        if not ui.verbose:
            omitted = _(
                b'(some details hidden, use --verbose'
                b' to show complete help)'
            )
            indicateomitted(rst, omitted)

        try:
            cmdutil.findcmd(name, commands.table)
            rst.append(
                _(b"\nuse 'hg help -c %s' to see help for the %s command\n")
                % (name, name)
            )
        except error.UnknownCommand:
            pass
        return rst

    def helpext(name: bytes, subtopic: Optional[bytes] = None) -> List[bytes]:
        try:
            mod = extensions.find(name)
            doc = gettext(pycompat.getdoc(mod)) or _(b'no help text available')
        except KeyError:
            mod = None
            doc = extensions.disabled_help(name)
            if not doc:
                raise error.UnknownCommand(name)

        if b'\n' not in doc:
            head, tail = doc, b""
        else:
            head, tail = doc.split(b'\n', 1)
        rst = [_(b'%s extension - %s\n\n') % (name.rpartition(b'.')[-1], head)]
        if tail:
            rst.extend(tail.splitlines(True))
            rst.append(b'\n')

        if not ui.verbose:
            omitted = _(
                b'(some details hidden, use --verbose'
                b' to show complete help)'
            )
            indicateomitted(rst, omitted)

        if mod:
            try:
                ct = mod.cmdtable
            except AttributeError:
                ct = {}
            modcmds = {c.partition(b'|')[0] for c in ct}
            rst.extend(helplist(modcmds.__contains__))
        else:
            rst.append(
                _(
                    b"(use 'hg help extensions' for information on enabling"
                    b" extensions)\n"
                )
            )
        return rst

    def helpextcmd(
        name: bytes, subtopic: Optional[bytes] = None
    ) -> List[bytes]:
        cmd, ext, doc = extensions.disabledcmd(
            ui, name, ui.configbool(b'ui', b'strict')
        )
        doc = stringutil.firstline(doc)

        rst = listexts(
            _(b"'%s' is provided by the following extension:") % cmd,
            {ext: doc},
            indent=4,
            showdeprecated=True,
        )
        rst.append(b'\n')
        rst.append(
            _(
                b"(use 'hg help extensions' for information on enabling "
                b"extensions)\n"
            )
        )
        return rst

    rst = []
    kw = opts.get(b'keyword')
    if kw or name is None and any(opts[o] for o in opts):
        matches = topicmatch(ui, commands, name or b'')
        helpareas = []
        if opts.get(b'extension'):
            helpareas += [(b'extensions', _(b'Extensions'))]
        if opts.get(b'command'):
            helpareas += [(b'commands', _(b'Commands'))]
        if not helpareas:
            helpareas = [
                (b'topics', _(b'Topics')),
                (b'commands', _(b'Commands')),
                (b'extensions', _(b'Extensions')),
                (b'extensioncommands', _(b'Extension Commands')),
            ]
        for t, title in helpareas:
            if matches[t]:
                rst.append(b'%s:\n\n' % title)
                rst.extend(minirst.maketable(sorted(matches[t]), 1))
                rst.append(b'\n')
        if not rst:
            msg = _(b'no matches')
            hint = _(b"try 'hg help' for a list of topics")
            raise error.InputError(msg, hint=hint)
    elif name and name != b'shortlist':
        queries = []
        if unknowncmd:
            queries += [helpextcmd]
        if opts.get(b'extension'):
            queries += [helpext]
        if opts.get(b'command'):
            queries += [helpcmd]
        if not queries:
            queries = (helptopic, helpcmd, helpext, helpextcmd)
        for f in queries:
            try:
                rst = f(name, subtopic)
                break
            except error.UnknownCommand:
                pass
        else:
            if unknowncmd:
                raise error.UnknownCommand(name)
            else:
                if fullname:
                    formatname = fullname
                else:
                    formatname = name
                if subtopic:
                    hintname = subtopic
                else:
                    hintname = name
                msg = _(b'no such help topic: %s') % formatname
                hint = _(b"try 'hg help --keyword %s'") % hintname
                raise error.InputError(msg, hint=hint)
    else:
        # program name
        if not ui.quiet:
            rst = [_(b"Mercurial Distributed SCM\n"), b'\n']
        rst.extend(helplist(None, **pycompat.strkwargs(opts)))

    return b''.join(rst)


def formattedhelp(
    ui: uimod.ui,
    commands,
    fullname: Optional[bytes],
    keep: Optional[Iterable[bytes]] = None,
    unknowncmd: bool = False,
    full: bool = True,
    **opts
) -> bytes:
    """get help for a given topic (as a dotted name) as rendered rst

    Either returns the rendered help text or raises an exception.
    """
    if keep is None:
        keep = []
    else:
        keep = list(keep)  # make a copy so we can mutate this later

    # <fullname> := <name>[.<subtopic][.<section>]
    name = subtopic = section = None
    if fullname is not None:
        nameparts = fullname.split(b'.')
        name = nameparts.pop(0)
        if nameparts and name in subtopics:
            subtopic = nameparts.pop(0)
        if nameparts:
            section = encoding.lower(b'.'.join(nameparts))

    textwidth = ui.configint(b'ui', b'textwidth')
    termwidth = ui.termwidth() - 2
    if textwidth <= 0 or termwidth < textwidth:
        textwidth = termwidth
    text = help_(
        ui,
        commands,
        name,
        fullname=fullname,
        subtopic=subtopic,
        unknowncmd=unknowncmd,
        full=full,
        **opts
    )

    blocks, pruned = minirst.parse(text, keep=keep)
    if b'verbose' in pruned:
        keep.append(b'omitted')
    else:
        keep.append(b'notomitted')
    blocks, pruned = minirst.parse(text, keep=keep)
    if section:
        blocks = minirst.filtersections(blocks, section)

    # We could have been given a weird ".foo" section without a name
    # to look for, or we could have simply failed to found "foo.bar"
    # because bar isn't a section of foo
    if section and not (blocks and name):
        raise error.InputError(_(b"help section not found: %s") % fullname)

    return minirst.formatplain(blocks, textwidth)
