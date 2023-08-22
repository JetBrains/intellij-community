# registrar.py - utilities to register function for specific purpose
#
#  Copyright FUJIWARA Katsunori <foozy@lares.dti.ne.jp> and others
#
# This software may be used and distributed according to the terms of the
# GNU General Public License version 2 or any later version.

from __future__ import absolute_import

from . import (
    configitems,
    error,
    pycompat,
    util,
)

# unlike the other registered items, config options are neither functions or
# classes. Registering the option is just small function call.
#
# We still add the official API to the registrar module for consistency with
# the other items extensions want might to register.
configitem = configitems.getitemregister


class _funcregistrarbase(object):
    """Base of decorator to register a function for specific purpose

    This decorator stores decorated functions into own dict 'table'.

    The least derived class can be defined by overriding 'formatdoc',
    for example::

        class keyword(_funcregistrarbase):
            _docformat = ":%s: %s"

    This should be used as below:

        keyword = registrar.keyword()

        @keyword(b'bar')
        def barfunc(*args, **kwargs):
            '''Explanation of bar keyword ....
            '''
            pass

    In this case:

    - 'barfunc' is stored as 'bar' in '_table' of an instance 'keyword' above
    - 'barfunc.__doc__' becomes ":bar: Explanation of bar keyword"
    """

    def __init__(self, table=None):
        if table is None:
            self._table = {}
        else:
            self._table = table

    def __call__(self, decl, *args, **kwargs):
        return lambda func: self._doregister(func, decl, *args, **kwargs)

    def _doregister(self, func, decl, *args, **kwargs):
        name = self._getname(decl)

        if name in self._table:
            msg = b'duplicate registration for name: "%s"' % name
            raise error.ProgrammingError(msg)

        if func.__doc__ and not util.safehasattr(func, '_origdoc'):
            func._origdoc = func.__doc__.strip()
            doc = pycompat.sysbytes(func._origdoc)
            func.__doc__ = pycompat.sysstr(self._formatdoc(decl, doc))

        self._table[name] = func
        self._extrasetup(name, func, *args, **kwargs)

        return func

    def _merge(self, registrarbase):
        """Merge the entries of the given registrar object into this one.

        The other registrar object must not contain any entries already in the
        current one, or a ProgrammmingError is raised.  Additionally, the types
        of the two registrars must match.
        """
        if not isinstance(registrarbase, type(self)):
            msg = b"cannot merge different types of registrar"
            raise error.ProgrammingError(msg)

        dups = set(registrarbase._table).intersection(self._table)

        if dups:
            msg = b'duplicate registration for names: "%s"' % b'", "'.join(dups)
            raise error.ProgrammingError(msg)

        self._table.update(registrarbase._table)

    def _parsefuncdecl(self, decl):
        """Parse function declaration and return the name of function in it"""
        i = decl.find(b'(')
        if i >= 0:
            return decl[:i]
        else:
            return decl

    def _getname(self, decl):
        """Return the name of the registered function from decl

        Derived class should override this, if it allows more
        descriptive 'decl' string than just a name.
        """
        return decl

    _docformat = None

    def _formatdoc(self, decl, doc):
        """Return formatted document of the registered function for help

        'doc' is '__doc__.strip()' of the registered function.
        """
        return self._docformat % (decl, doc)

    def _extrasetup(self, name, func):
        """Execute extra setup for registered function, if needed"""


class command(_funcregistrarbase):
    """Decorator to register a command function to table

    This class receives a command table as its argument. The table should
    be a dict.

    The created object can be used as a decorator for adding commands to
    that command table. This accepts multiple arguments to define a command.

    The first argument is the command name (as bytes).

    The `options` keyword argument is an iterable of tuples defining command
    arguments. See ``mercurial.fancyopts.fancyopts()`` for the format of each
    tuple.

    The `synopsis` argument defines a short, one line summary of how to use the
    command. This shows up in the help output.

    There are three arguments that control what repository (if any) is found
    and passed to the decorated function: `norepo`, `optionalrepo`, and
    `inferrepo`.

    The `norepo` argument defines whether the command does not require a
    local repository. Most commands operate against a repository, thus the
    default is False. When True, no repository will be passed.

    The `optionalrepo` argument defines whether the command optionally requires
    a local repository. If no repository can be found, None will be passed
    to the decorated function.

    The `inferrepo` argument defines whether to try to find a repository from
    the command line arguments. If True, arguments will be examined for
    potential repository locations. See ``findrepo()``. If a repository is
    found, it will be used and passed to the decorated function.

    The `intents` argument defines a set of intended actions or capabilities
    the command is taking. These intents can be used to affect the construction
    of the repository object passed to the command. For example, commands
    declaring that they are read-only could receive a repository that doesn't
    have any methods allowing repository mutation. Other intents could be used
    to prevent the command from running if the requested intent could not be
    fulfilled.

    If `helpcategory` is set (usually to one of the constants in the help
    module), the command will be displayed under that category in the help's
    list of commands.

    The following intents are defined:

    readonly
       The command is read-only

    The signature of the decorated function looks like this:
        def cmd(ui[, repo] [, <args>] [, <options>])

      `repo` is required if `norepo` is False.
      `<args>` are positional args (or `*args`) arguments, of non-option
      arguments from the command line.
      `<options>` are keyword arguments (or `**options`) of option arguments
      from the command line.

    See the WritingExtensions and MercurialApi documentation for more exhaustive
    descriptions and examples.
    """

    # Command categories for grouping them in help output.
    # These can also be specified for aliases, like:
    # [alias]
    # myalias = something
    # myalias:category = repo
    CATEGORY_REPO_CREATION = b'repo'
    CATEGORY_REMOTE_REPO_MANAGEMENT = b'remote'
    CATEGORY_COMMITTING = b'commit'
    CATEGORY_CHANGE_MANAGEMENT = b'management'
    CATEGORY_CHANGE_ORGANIZATION = b'organization'
    CATEGORY_FILE_CONTENTS = b'files'
    CATEGORY_CHANGE_NAVIGATION = b'navigation'
    CATEGORY_WORKING_DIRECTORY = b'wdir'
    CATEGORY_IMPORT_EXPORT = b'import'
    CATEGORY_MAINTENANCE = b'maintenance'
    CATEGORY_HELP = b'help'
    CATEGORY_MISC = b'misc'
    CATEGORY_NONE = b'none'

    def _doregister(
        self,
        func,
        name,
        options=(),
        synopsis=None,
        norepo=False,
        optionalrepo=False,
        inferrepo=False,
        intents=None,
        helpcategory=None,
        helpbasic=False,
    ):
        func.norepo = norepo
        func.optionalrepo = optionalrepo
        func.inferrepo = inferrepo
        func.intents = intents or set()
        func.helpcategory = helpcategory
        func.helpbasic = helpbasic
        if synopsis:
            self._table[name] = func, list(options), synopsis
        else:
            self._table[name] = func, list(options)
        return func

    def rename(self, old, new):
        """rename a command. Used to add aliases, debugstrip ->
        debugstrip|strip
        """
        self._table[new] = self._table.pop(old)


INTENT_READONLY = b'readonly'


class revsetpredicate(_funcregistrarbase):
    """Decorator to register revset predicate

    Usage::

        revsetpredicate = registrar.revsetpredicate()

        @revsetpredicate(b'mypredicate(arg1, arg2[, arg3])')
        def mypredicatefunc(repo, subset, x):
            '''Explanation of this revset predicate ....
            '''
            pass

    The first string argument is used also in online help.

    Optional argument 'safe' indicates whether a predicate is safe for
    DoS attack (False by default).

    Optional argument 'takeorder' indicates whether a predicate function
    takes ordering policy as the last argument.

    Optional argument 'weight' indicates the estimated run-time cost, useful
    for static optimization, default is 1. Higher weight means more expensive.
    Usually, revsets that are fast and return only one revision has a weight of
    0.5 (ex. a symbol); revsets with O(changelog) complexity and read only the
    changelog have weight 10 (ex. author); revsets reading manifest deltas have
    weight 30 (ex. adds); revset reading manifest contents have weight 100
    (ex. contains). Note: those values are flexible. If the revset has a
    same big-O time complexity as 'contains', but with a smaller constant, it
    might have a weight of 90.

    'revsetpredicate' instance in example above can be used to
    decorate multiple functions.

    Decorated functions are registered automatically at loading
    extension, if an instance named as 'revsetpredicate' is used for
    decorating in extension.

    Otherwise, explicit 'revset.loadpredicate()' is needed.
    """

    _getname = _funcregistrarbase._parsefuncdecl
    _docformat = b"``%s``\n    %s"

    def _extrasetup(self, name, func, safe=False, takeorder=False, weight=1):
        func._safe = safe
        func._takeorder = takeorder
        func._weight = weight


class filesetpredicate(_funcregistrarbase):
    """Decorator to register fileset predicate

    Usage::

        filesetpredicate = registrar.filesetpredicate()

        @filesetpredicate(b'mypredicate()')
        def mypredicatefunc(mctx, x):
            '''Explanation of this fileset predicate ....
            '''
            pass

    The first string argument is used also in online help.

    Optional argument 'callstatus' indicates whether a predicate
     implies 'matchctx.status()' at runtime or not (False, by
     default).

    Optional argument 'weight' indicates the estimated run-time cost, useful
    for static optimization, default is 1. Higher weight means more expensive.
    There are predefined weights in the 'filesetlang' module.

    ====== =============================================================
    Weight Description and examples
    ====== =============================================================
    0.5    basic match patterns (e.g. a symbol)
    10     computing status (e.g. added()) or accessing a few files
    30     reading file content for each (e.g. grep())
    50     scanning working directory (ignored())
    ====== =============================================================

    'filesetpredicate' instance in example above can be used to
    decorate multiple functions.

    Decorated functions are registered automatically at loading
    extension, if an instance named as 'filesetpredicate' is used for
    decorating in extension.

    Otherwise, explicit 'fileset.loadpredicate()' is needed.
    """

    _getname = _funcregistrarbase._parsefuncdecl
    _docformat = b"``%s``\n    %s"

    def _extrasetup(self, name, func, callstatus=False, weight=1):
        func._callstatus = callstatus
        func._weight = weight


class _templateregistrarbase(_funcregistrarbase):
    """Base of decorator to register functions as template specific one"""

    _docformat = b":%s: %s"


class templatekeyword(_templateregistrarbase):
    """Decorator to register template keyword

    Usage::

        templatekeyword = registrar.templatekeyword()

        # new API (since Mercurial 4.6)
        @templatekeyword(b'mykeyword', requires={b'repo', b'ctx'})
        def mykeywordfunc(context, mapping):
            '''Explanation of this template keyword ....
            '''
            pass

    The first string argument is used also in online help.

    Optional argument 'requires' should be a collection of resource names
    which the template keyword depends on.

    'templatekeyword' instance in example above can be used to
    decorate multiple functions.

    Decorated functions are registered automatically at loading
    extension, if an instance named as 'templatekeyword' is used for
    decorating in extension.

    Otherwise, explicit 'templatekw.loadkeyword()' is needed.
    """

    def _extrasetup(self, name, func, requires=()):
        func._requires = requires


class templatefilter(_templateregistrarbase):
    """Decorator to register template filer

    Usage::

        templatefilter = registrar.templatefilter()

        @templatefilter(b'myfilter', intype=bytes)
        def myfilterfunc(text):
            '''Explanation of this template filter ....
            '''
            pass

    The first string argument is used also in online help.

    Optional argument 'intype' defines the type of the input argument,
    which should be (bytes, int, templateutil.date, or None for any.)

    'templatefilter' instance in example above can be used to
    decorate multiple functions.

    Decorated functions are registered automatically at loading
    extension, if an instance named as 'templatefilter' is used for
    decorating in extension.

    Otherwise, explicit 'templatefilters.loadkeyword()' is needed.
    """

    def _extrasetup(self, name, func, intype=None):
        func._intype = intype


class templatefunc(_templateregistrarbase):
    """Decorator to register template function

    Usage::

        templatefunc = registrar.templatefunc()

        @templatefunc(b'myfunc(arg1, arg2[, arg3])', argspec=b'arg1 arg2 arg3',
                      requires={b'ctx'})
        def myfuncfunc(context, mapping, args):
            '''Explanation of this template function ....
            '''
            pass

    The first string argument is used also in online help.

    If optional 'argspec' is defined, the function will receive 'args' as
    a dict of named arguments. Otherwise 'args' is a list of positional
    arguments.

    Optional argument 'requires' should be a collection of resource names
    which the template function depends on.

    'templatefunc' instance in example above can be used to
    decorate multiple functions.

    Decorated functions are registered automatically at loading
    extension, if an instance named as 'templatefunc' is used for
    decorating in extension.

    Otherwise, explicit 'templatefuncs.loadfunction()' is needed.
    """

    _getname = _funcregistrarbase._parsefuncdecl

    def _extrasetup(self, name, func, argspec=None, requires=()):
        func._argspec = argspec
        func._requires = requires


class internalmerge(_funcregistrarbase):
    """Decorator to register in-process merge tool

    Usage::

        internalmerge = registrar.internalmerge()

        @internalmerge(b'mymerge', internalmerge.mergeonly,
                       onfailure=None, precheck=None,
                       binary=False, symlink=False):
        def mymergefunc(repo, mynode, orig, fcd, fco, fca,
                        toolconf, files, labels=None):
            '''Explanation of this internal merge tool ....
            '''
            return 1, False # means "conflicted", "no deletion needed"

    The first string argument is used to compose actual merge tool name,
    ":name" and "internal:name" (the latter is historical one).

    The second argument is one of merge types below:

    ========== ======== ======== =========
    merge type precheck premerge fullmerge
    ========== ======== ======== =========
    nomerge     x        x        x
    mergeonly   o        x        o
    fullmerge   o        o        o
    ========== ======== ======== =========

    Optional argument 'onfailure' is the format of warning message
    to be used at failure of merging (target filename is specified
    at formatting). Or, None or so, if warning message should be
    suppressed.

    Optional argument 'precheck' is the function to be used
    before actual invocation of internal merge tool itself.
    It takes as same arguments as internal merge tool does, other than
    'files' and 'labels'. If it returns false value, merging is aborted
    immediately (and file is marked as "unresolved").

    Optional argument 'binary' is a binary files capability of internal
    merge tool. 'nomerge' merge type implies binary=True.

    Optional argument 'symlink' is a symlinks capability of inetrnal
    merge function. 'nomerge' merge type implies symlink=True.

    'internalmerge' instance in example above can be used to
    decorate multiple functions.

    Decorated functions are registered automatically at loading
    extension, if an instance named as 'internalmerge' is used for
    decorating in extension.

    Otherwise, explicit 'filemerge.loadinternalmerge()' is needed.
    """

    _docformat = b"``:%s``\n    %s"

    # merge type definitions:
    nomerge = None
    mergeonly = b'mergeonly'  # just the full merge, no premerge
    fullmerge = b'fullmerge'  # both premerge and merge

    def _extrasetup(
        self,
        name,
        func,
        mergetype,
        onfailure=None,
        precheck=None,
        binary=False,
        symlink=False,
    ):
        func.mergetype = mergetype
        func.onfailure = onfailure
        func.precheck = precheck

        binarycap = binary or mergetype == self.nomerge
        symlinkcap = symlink or mergetype == self.nomerge

        # actual capabilities, which this internal merge tool has
        func.capabilities = {b"binary": binarycap, b"symlink": symlinkcap}
