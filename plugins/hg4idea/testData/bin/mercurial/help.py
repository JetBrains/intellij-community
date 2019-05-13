# help.py - help data for mercurial
#
# Copyright 2006 Matt Mackall <mpm@selenic.com>
#
# This software may be used and distributed according to the terms of the
# GNU General Public License version 2 or any later version.

from i18n import gettext, _
import itertools, sys, os, error
import extensions, revset, fileset, templatekw, templatefilters, filemerge
import encoding, util, minirst
import cmdutil

def listexts(header, exts, indent=1):
    '''return a text listing of the given extensions'''
    rst = []
    if exts:
        rst.append('\n%s\n\n' % header)
        for name, desc in sorted(exts.iteritems()):
            rst.append('%s:%s: %s\n' % (' ' * indent, name, desc))
    return rst

def extshelp():
    rst = loaddoc('extensions')().splitlines(True)
    rst.extend(listexts(_('enabled extensions:'), extensions.enabled()))
    rst.extend(listexts(_('disabled extensions:'), extensions.disabled()))
    doc = ''.join(rst)
    return doc

def optrst(options, verbose):
    data = []
    multioccur = False
    for option in options:
        if len(option) == 5:
            shortopt, longopt, default, desc, optlabel = option
        else:
            shortopt, longopt, default, desc = option
            optlabel = _("VALUE") # default label

        if _("DEPRECATED") in desc and not verbose:
            continue

        so = ''
        if shortopt:
            so = '-' + shortopt
        lo = '--' + longopt
        if default:
            desc += _(" (default: %s)") % default

        if isinstance(default, list):
            lo += " %s [+]" % optlabel
            multioccur = True
        elif (default is not None) and not isinstance(default, bool):
            lo += " %s" % optlabel

        data.append((so, lo, desc))

    rst = minirst.maketable(data, 1)

    if multioccur:
        rst.append(_("\n[+] marked option can be specified multiple times\n"))

    return ''.join(rst)

def indicateomitted(rst, omitted, notomitted=None):
    rst.append('\n\n.. container:: omitted\n\n    %s\n\n' % omitted)
    if notomitted:
        rst.append('\n\n.. container:: notomitted\n\n    %s\n\n' % notomitted)

def topicmatch(kw):
    """Return help topics matching kw.

    Returns {'section': [(name, summary), ...], ...} where section is
    one of topics, commands, extensions, or extensioncommands.
    """
    kw = encoding.lower(kw)
    def lowercontains(container):
        return kw in encoding.lower(container)  # translated in helptable
    results = {'topics': [],
               'commands': [],
               'extensions': [],
               'extensioncommands': [],
               }
    for names, header, doc in helptable:
        if (sum(map(lowercontains, names))
            or lowercontains(header)
            or lowercontains(doc())):
            results['topics'].append((names[0], header))
    import commands # avoid cycle
    for cmd, entry in commands.table.iteritems():
        if cmd.startswith('debug'):
            continue
        if len(entry) == 3:
            summary = entry[2]
        else:
            summary = ''
        # translate docs *before* searching there
        docs = _(getattr(entry[0], '__doc__', None)) or ''
        if kw in cmd or lowercontains(summary) or lowercontains(docs):
            doclines = docs.splitlines()
            if doclines:
                summary = doclines[0]
            cmdname = cmd.split('|')[0].lstrip('^')
            results['commands'].append((cmdname, summary))
    for name, docs in itertools.chain(
        extensions.enabled().iteritems(),
        extensions.disabled().iteritems()):
        # extensions.load ignores the UI argument
        mod = extensions.load(None, name, '')
        if lowercontains(name) or lowercontains(docs):
            # extension docs are already translated
            results['extensions'].append((name, docs.splitlines()[0]))
        for cmd, entry in getattr(mod, 'cmdtable', {}).iteritems():
            if kw in cmd or (len(entry) > 2 and lowercontains(entry[2])):
                cmdname = cmd.split('|')[0].lstrip('^')
                if entry[0].__doc__:
                    cmddoc = gettext(entry[0].__doc__).splitlines()[0]
                else:
                    cmddoc = _('(no help text available)')
                results['extensioncommands'].append((cmdname, cmddoc))
    return results

def loaddoc(topic):
    """Return a delayed loader for help/topic.txt."""

    def loader():
        if util.mainfrozen():
            module = sys.executable
        else:
            module = __file__
        base = os.path.dirname(module)

        for dir in ('.', '..'):
            docdir = os.path.join(base, dir, 'help')
            if os.path.isdir(docdir):
                break

        path = os.path.join(docdir, topic + ".txt")
        doc = gettext(util.readfile(path))
        for rewriter in helphooks.get(topic, []):
            doc = rewriter(topic, doc)
        return doc

    return loader

helptable = sorted([
    (["config", "hgrc"], _("Configuration Files"), loaddoc('config')),
    (["dates"], _("Date Formats"), loaddoc('dates')),
    (["patterns"], _("File Name Patterns"), loaddoc('patterns')),
    (['environment', 'env'], _('Environment Variables'),
     loaddoc('environment')),
    (['revisions', 'revs'], _('Specifying Single Revisions'),
     loaddoc('revisions')),
    (['multirevs', 'mrevs'], _('Specifying Multiple Revisions'),
     loaddoc('multirevs')),
    (['revsets', 'revset'], _("Specifying Revision Sets"), loaddoc('revsets')),
    (['filesets', 'fileset'], _("Specifying File Sets"), loaddoc('filesets')),
    (['diffs'], _('Diff Formats'), loaddoc('diffs')),
    (['merge-tools', 'mergetools'], _('Merge Tools'), loaddoc('merge-tools')),
    (['templating', 'templates', 'template', 'style'], _('Template Usage'),
     loaddoc('templates')),
    (['urls'], _('URL Paths'), loaddoc('urls')),
    (["extensions"], _("Using Additional Features"), extshelp),
    (["subrepos", "subrepo"], _("Subrepositories"), loaddoc('subrepos')),
    (["hgweb"], _("Configuring hgweb"), loaddoc('hgweb')),
    (["glossary"], _("Glossary"), loaddoc('glossary')),
    (["hgignore", "ignore"], _("Syntax for Mercurial Ignore Files"),
     loaddoc('hgignore')),
    (["phases"], _("Working with Phases"), loaddoc('phases')),
])

# Map topics to lists of callable taking the current topic help and
# returning the updated version
helphooks = {}

def addtopichook(topic, rewriter):
    helphooks.setdefault(topic, []).append(rewriter)

def makeitemsdoc(topic, doc, marker, items):
    """Extract docstring from the items key to function mapping, build a
    .single documentation block and use it to overwrite the marker in doc
    """
    entries = []
    for name in sorted(items):
        text = (items[name].__doc__ or '').rstrip()
        if not text:
            continue
        text = gettext(text)
        lines = text.splitlines()
        doclines = [(lines[0])]
        for l in lines[1:]:
            # Stop once we find some Python doctest
            if l.strip().startswith('>>>'):
                break
            doclines.append('  ' + l.strip())
        entries.append('\n'.join(doclines))
    entries = '\n\n'.join(entries)
    return doc.replace(marker, entries)

def addtopicsymbols(topic, marker, symbols):
    def add(topic, doc):
        return makeitemsdoc(topic, doc, marker, symbols)
    addtopichook(topic, add)

addtopicsymbols('filesets', '.. predicatesmarker', fileset.symbols)
addtopicsymbols('merge-tools', '.. internaltoolsmarker', filemerge.internals)
addtopicsymbols('revsets', '.. predicatesmarker', revset.symbols)
addtopicsymbols('templates', '.. keywordsmarker', templatekw.dockeywords)
addtopicsymbols('templates', '.. filtersmarker', templatefilters.filters)

def help_(ui, name, unknowncmd=False, full=True, **opts):
    '''
    Generate the help for 'name' as unformatted restructured text. If
    'name' is None, describe the commands available.
    '''

    import commands # avoid cycle

    def helpcmd(name):
        try:
            aliases, entry = cmdutil.findcmd(name, commands.table,
                                             strict=unknowncmd)
        except error.AmbiguousCommand, inst:
            # py3k fix: except vars can't be used outside the scope of the
            # except block, nor can be used inside a lambda. python issue4617
            prefix = inst.args[0]
            select = lambda c: c.lstrip('^').startswith(prefix)
            rst = helplist(select)
            return rst

        rst = []

        # check if it's an invalid alias and display its error if it is
        if getattr(entry[0], 'badalias', False):
            if not unknowncmd:
                ui.pushbuffer()
                entry[0](ui)
                rst.append(ui.popbuffer())
            return rst

        # synopsis
        if len(entry) > 2:
            if entry[2].startswith('hg'):
                rst.append("%s\n" % entry[2])
            else:
                rst.append('hg %s %s\n' % (aliases[0], entry[2]))
        else:
            rst.append('hg %s\n' % aliases[0])
        # aliases
        if full and not ui.quiet and len(aliases) > 1:
            rst.append(_("\naliases: %s\n") % ', '.join(aliases[1:]))
        rst.append('\n')

        # description
        doc = gettext(entry[0].__doc__)
        if not doc:
            doc = _("(no help text available)")
        if util.safehasattr(entry[0], 'definition'):  # aliased command
            if entry[0].definition.startswith('!'):  # shell alias
                doc = _('shell alias for::\n\n    %s') % entry[0].definition[1:]
            else:
                doc = _('alias for: hg %s\n\n%s') % (entry[0].definition, doc)
        doc = doc.splitlines(True)
        if ui.quiet or not full:
            rst.append(doc[0])
        else:
            rst.extend(doc)
        rst.append('\n')

        # check if this command shadows a non-trivial (multi-line)
        # extension help text
        try:
            mod = extensions.find(name)
            doc = gettext(mod.__doc__) or ''
            if '\n' in doc.strip():
                msg = _('use "hg help -e %s" to show help for '
                        'the %s extension') % (name, name)
                rst.append('\n%s\n' % msg)
        except KeyError:
            pass

        # options
        if not ui.quiet and entry[1]:
            rst.append('\n%s\n\n' % _("options:"))
            rst.append(optrst(entry[1], ui.verbose))

        if ui.verbose:
            rst.append('\n%s\n\n' % _("global options:"))
            rst.append(optrst(commands.globalopts, ui.verbose))

        if not ui.verbose:
            if not full:
                rst.append(_('\nuse "hg help %s" to show the full help text\n')
                           % name)
            elif not ui.quiet:
                omitted = _('use "hg -v help %s" to show more complete'
                            ' help and the global options') % name
                notomitted = _('use "hg -v help %s" to show'
                               ' the global options') % name
                indicateomitted(rst, omitted, notomitted)

        return rst


    def helplist(select=None):
        # list of commands
        if name == "shortlist":
            header = _('basic commands:\n\n')
        else:
            header = _('list of commands:\n\n')

        h = {}
        cmds = {}
        for c, e in commands.table.iteritems():
            f = c.split("|", 1)[0]
            if select and not select(f):
                continue
            if (not select and name != 'shortlist' and
                e[0].__module__ != commands.__name__):
                continue
            if name == "shortlist" and not f.startswith("^"):
                continue
            f = f.lstrip("^")
            if not ui.debugflag and f.startswith("debug"):
                continue
            doc = e[0].__doc__
            if doc and 'DEPRECATED' in doc and not ui.verbose:
                continue
            doc = gettext(doc)
            if not doc:
                doc = _("(no help text available)")
            h[f] = doc.splitlines()[0].rstrip()
            cmds[f] = c.lstrip("^")

        rst = []
        if not h:
            if not ui.quiet:
                rst.append(_('no commands defined\n'))
            return rst

        if not ui.quiet:
            rst.append(header)
        fns = sorted(h)
        for f in fns:
            if ui.verbose:
                commacmds = cmds[f].replace("|",", ")
                rst.append(" :%s: %s\n" % (commacmds, h[f]))
            else:
                rst.append(' :%s: %s\n' % (f, h[f]))

        if not name:
            exts = listexts(_('enabled extensions:'), extensions.enabled())
            if exts:
                rst.append('\n')
                rst.extend(exts)

            rst.append(_("\nadditional help topics:\n\n"))
            topics = []
            for names, header, doc in helptable:
                topics.append((names[0], header))
            for t, desc in topics:
                rst.append(" :%s: %s\n" % (t, desc))

        optlist = []
        if not ui.quiet:
            if ui.verbose:
                optlist.append((_("global options:"), commands.globalopts))
                if name == 'shortlist':
                    optlist.append((_('use "hg help" for the full list '
                                           'of commands'), ()))
            else:
                if name == 'shortlist':
                    msg = _('use "hg help" for the full list of commands '
                            'or "hg -v" for details')
                elif name and not full:
                    msg = _('use "hg help %s" to show the full help '
                            'text') % name
                else:
                    msg = _('use "hg -v help%s" to show builtin aliases and '
                            'global options') % (name and " " + name or "")
                optlist.append((msg, ()))

        if optlist:
            for title, options in optlist:
                rst.append('\n%s\n' % title)
                if options:
                    rst.append('\n%s\n' % optrst(options, ui.verbose))
        return rst

    def helptopic(name):
        for names, header, doc in helptable:
            if name in names:
                break
        else:
            raise error.UnknownCommand(name)

        rst = [minirst.section(header)]

        # description
        if not doc:
            rst.append("    %s\n" % _("(no help text available)"))
        if util.safehasattr(doc, '__call__'):
            rst += ["    %s\n" % l for l in doc().splitlines()]

        if not ui.verbose:
            omitted = (_('use "hg help -v %s" to show more complete help') %
                       name)
            indicateomitted(rst, omitted)

        try:
            cmdutil.findcmd(name, commands.table)
            rst.append(_('\nuse "hg help -c %s" to see help for '
                       'the %s command\n') % (name, name))
        except error.UnknownCommand:
            pass
        return rst

    def helpext(name):
        try:
            mod = extensions.find(name)
            doc = gettext(mod.__doc__) or _('no help text available')
        except KeyError:
            mod = None
            doc = extensions.disabledext(name)
            if not doc:
                raise error.UnknownCommand(name)

        if '\n' not in doc:
            head, tail = doc, ""
        else:
            head, tail = doc.split('\n', 1)
        rst = [_('%s extension - %s\n\n') % (name.split('.')[-1], head)]
        if tail:
            rst.extend(tail.splitlines(True))
            rst.append('\n')

        if not ui.verbose:
            omitted = (_('use "hg help -v %s" to show more complete help') %
                       name)
            indicateomitted(rst, omitted)

        if mod:
            try:
                ct = mod.cmdtable
            except AttributeError:
                ct = {}
            modcmds = set([c.split('|', 1)[0] for c in ct])
            rst.extend(helplist(modcmds.__contains__))
        else:
            rst.append(_('use "hg help extensions" for information on enabling '
                       'extensions\n'))
        return rst

    def helpextcmd(name):
        cmd, ext, mod = extensions.disabledcmd(ui, name,
                                               ui.configbool('ui', 'strict'))
        doc = gettext(mod.__doc__).splitlines()[0]

        rst = listexts(_("'%s' is provided by the following "
                              "extension:") % cmd, {ext: doc}, indent=4)
        rst.append('\n')
        rst.append(_('use "hg help extensions" for information on enabling '
                   'extensions\n'))
        return rst


    rst = []
    kw = opts.get('keyword')
    if kw:
        matches = topicmatch(kw)
        for t, title in (('topics', _('Topics')),
                         ('commands', _('Commands')),
                         ('extensions', _('Extensions')),
                         ('extensioncommands', _('Extension Commands'))):
            if matches[t]:
                rst.append('%s:\n\n' % title)
                rst.extend(minirst.maketable(sorted(matches[t]), 1))
                rst.append('\n')
    elif name and name != 'shortlist':
        i = None
        if unknowncmd:
            queries = (helpextcmd,)
        elif opts.get('extension'):
            queries = (helpext,)
        elif opts.get('command'):
            queries = (helpcmd,)
        else:
            queries = (helptopic, helpcmd, helpext, helpextcmd)
        for f in queries:
            try:
                rst = f(name)
                i = None
                break
            except error.UnknownCommand, inst:
                i = inst
        if i:
            raise i
    else:
        # program name
        if not ui.quiet:
            rst = [_("Mercurial Distributed SCM\n"), '\n']
        rst.extend(helplist())

    return ''.join(rst)
