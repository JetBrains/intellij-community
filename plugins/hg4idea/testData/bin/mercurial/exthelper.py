# Copyright 2012 Logilab SA        <contact@logilab.fr>
#                Pierre-Yves David <pierre-yves.david@ens-lyon.org>
#                Octobus <contact@octobus.net>
#
# This software may be used and distributed according to the terms of the
# GNU General Public License version 2 or any later version.

#####################################################################
### Extension helper                                              ###
#####################################################################


from . import (
    commands,
    error,
    extensions,
    registrar,
)

from hgdemandimport import tracing


class exthelper:
    """Helper for modular extension setup

    A single helper should be instantiated for each module of an
    extension, where a command or function needs to be wrapped, or a
    command, extension hook, fileset, revset or template needs to be
    registered.  Helper methods are then used as decorators for
    these various purposes.  If an extension spans multiple modules,
    all helper instances should be merged in the main module.

    All decorators return the original function and may be chained.

    Aside from the helper functions with examples below, several
    registrar method aliases are available for adding commands,
    configitems, filesets, revsets, and templates.  Simply decorate
    the appropriate methods, and assign the corresponding exthelper
    variable to a module level variable of the extension.  The
    extension loading mechanism will handle the rest.

    example::

        # ext.py
        eh = exthelper.exthelper()

        # As needed (failure to do this will mean your registration will not
        # happen):
        cmdtable = eh.cmdtable
        configtable = eh.configtable
        filesetpredicate = eh.filesetpredicate
        revsetpredicate = eh.revsetpredicate
        templatekeyword = eh.templatekeyword

        # As needed (failure to do this will mean your eh.wrap*-decorated
        # functions will not wrap, and/or your eh.*setup-decorated functions
        # will not execute):
        uisetup = eh.finaluisetup
        extsetup = eh.finalextsetup
        reposetup = eh.finalreposetup
        uipopulate = eh.finaluipopulate

        @eh.command(b'mynewcommand',
            [(b'r', b'rev', [], _(b'operate on these revisions'))],
            _(b'-r REV...'),
            helpcategory=command.CATEGORY_XXX)
        def newcommand(ui, repo, *revs, **opts):
            # implementation goes here

        eh.configitem(b'experimental', b'foo',
            default=False,
        )

        @eh.filesetpredicate(b'lfs()')
        def filesetbabar(mctx, x):
            return mctx.predicate(...)

        @eh.revsetpredicate(b'hidden')
        def revsetbabar(repo, subset, x):
            args = revset.getargs(x, 0, 0, b'babar accept no argument')
            return [r for r in subset if b'babar' in repo[r].description()]

        @eh.templatekeyword(b'babar')
        def kwbabar(ctx):
            return b'babar'
    """

    def __init__(self):
        self._uipopulatecallables = []
        self._uicallables = []
        self._extcallables = []
        self._repocallables = []
        self._commandwrappers = []
        self._extcommandwrappers = []
        self._functionwrappers = []
        self.cmdtable = {}
        self.command = registrar.command(self.cmdtable)
        self.configtable = {}
        self.configitem = registrar.configitem(self.configtable)
        self.filesetpredicate = registrar.filesetpredicate()
        self.revsetpredicate = registrar.revsetpredicate()
        self.templatekeyword = registrar.templatekeyword()

    def merge(self, other):
        self._uicallables.extend(other._uicallables)
        self._uipopulatecallables.extend(other._uipopulatecallables)
        self._extcallables.extend(other._extcallables)
        self._repocallables.extend(other._repocallables)
        self.filesetpredicate._merge(other.filesetpredicate)
        self.revsetpredicate._merge(other.revsetpredicate)
        self.templatekeyword._merge(other.templatekeyword)
        self._commandwrappers.extend(other._commandwrappers)
        self._extcommandwrappers.extend(other._extcommandwrappers)
        self._functionwrappers.extend(other._functionwrappers)
        self.cmdtable.update(other.cmdtable)
        for section, items in other.configtable.items():
            if section in self.configtable:
                self.configtable[section].update(items)
            else:
                self.configtable[section] = items

    def finaluisetup(self, ui):
        """Method to be used as the extension uisetup

        The following operations belong here:

        - Changes to ui.__class__ . The ui object that will be used to run the
          command has not yet been created. Changes made here will affect ui
          objects created after this, and in particular the ui that will be
          passed to runcommand
        - Command wraps (extensions.wrapcommand)
        - Changes that need to be visible to other extensions: because
          initialization occurs in phases (all extensions run uisetup, then all
          run extsetup), a change made here will be visible to other extensions
          during extsetup
        - Monkeypatch or wrap function (extensions.wrapfunction) of dispatch
          module members
        - Setup of pre-* and post-* hooks
        - pushkey setup
        """
        for command, wrapper, opts in self._commandwrappers:
            entry = extensions.wrapcommand(commands.table, command, wrapper)
            if opts:
                for opt in opts:
                    entry[1].append(opt)
        for cont, funcname, wrapper in self._functionwrappers:
            extensions.wrapfunction(cont, funcname, wrapper)
        for c in self._uicallables:
            with tracing.log('finaluisetup: %s', repr(c)):
                c(ui)

    def finaluipopulate(self, ui):
        """Method to be used as the extension uipopulate

        This is called once per ui instance to:

        - Set up additional ui members
        - Update configuration by ``ui.setconfig()``
        - Extend the class dynamically
        """
        for c in self._uipopulatecallables:
            c(ui)

    def finalextsetup(self, ui):
        """Method to be used as the extension extsetup

        The following operations belong here:

        - Changes depending on the status of other extensions. (if
          extensions.find(b'mq'))
        - Add a global option to all commands
        """
        knownexts = {}

        for ext, command, wrapper, opts in self._extcommandwrappers:
            if ext not in knownexts:
                try:
                    e = extensions.find(ext)
                except KeyError:
                    # Extension isn't enabled, so don't bother trying to wrap
                    # it.
                    continue
                knownexts[ext] = e.cmdtable
            entry = extensions.wrapcommand(knownexts[ext], command, wrapper)
            if opts:
                for opt in opts:
                    entry[1].append(opt)

        for c in self._extcallables:
            with tracing.log('finalextsetup: %s', repr(c)):
                c(ui)

    def finalreposetup(self, ui, repo):
        """Method to be used as the extension reposetup

        The following operations belong here:

        - All hooks but pre-* and post-*
        - Modify configuration variables
        - Changes to repo.__class__, repo.dirstate.__class__
        """
        for c in self._repocallables:
            with tracing.log('finalreposetup: %s', repr(c)):
                c(ui, repo)

    def uisetup(self, call):
        """Decorated function will be executed during uisetup

        example::

            # Required, otherwise your uisetup function(s) will not execute.
            uisetup = eh.finaluisetup

            @eh.uisetup
            def setupbabar(ui):
                print('this is uisetup!')
        """
        self._uicallables.append(call)
        return call

    def uipopulate(self, call):
        """Decorated function will be executed during uipopulate

        example::

            # Required, otherwise your uipopulate function(s) will not execute.
            uipopulate = eh.finaluipopulate

            @eh.uipopulate
            def setupfoo(ui):
                print('this is uipopulate!')
        """
        self._uipopulatecallables.append(call)
        return call

    def extsetup(self, call):
        """Decorated function will be executed during extsetup

        example::

            # Required, otherwise your extsetup function(s) will not execute.
            extsetup = eh.finalextsetup

            @eh.extsetup
            def setupcelestine(ui):
                print('this is extsetup!')
        """
        self._extcallables.append(call)
        return call

    def reposetup(self, call):
        """Decorated function will be executed during reposetup

        example::

            # Required, otherwise your reposetup function(s) will not execute.
            reposetup = eh.finalreposetup

            @eh.reposetup
            def setupzephir(ui, repo):
                print('this is reposetup!')
        """
        self._repocallables.append(call)
        return call

    def wrapcommand(self, command, extension=None, opts=None):
        """Decorated function is a command wrapper

        The name of the command must be given as the decorator argument.
        The wrapping is installed during `uisetup`.

        If the second option `extension` argument is provided, the wrapping
        will be applied in the extension commandtable. This argument must be a
        string that will be searched using `extension.find` if not found and
        Abort error is raised. If the wrapping applies to an extension, it is
        installed during `extsetup`.

        example::

            # Required if `extension` is not provided
            uisetup = eh.finaluisetup
            # Required if `extension` is provided
            extsetup = eh.finalextsetup

            @eh.wrapcommand(b'summary')
            def wrapsummary(orig, ui, repo, *args, **kwargs):
                ui.note(b'Barry!')
                return orig(ui, repo, *args, **kwargs)

        The `opts` argument allows specifying a list of tuples for additional
        arguments for the command.  See ``mercurial.fancyopts.fancyopts()`` for
        the format of the tuple.

        """
        if opts is None:
            opts = []
        else:
            for opt in opts:
                if not isinstance(opt, tuple):
                    raise error.ProgrammingError(b'opts must be list of tuples')
                if len(opt) not in (4, 5):
                    msg = b'each opt tuple must contain 4 or 5 values'
                    raise error.ProgrammingError(msg)

        def dec(wrapper):
            if extension is None:
                self._commandwrappers.append((command, wrapper, opts))
            else:
                self._extcommandwrappers.append(
                    (extension, command, wrapper, opts)
                )
            return wrapper

        return dec

    def wrapfunction(self, container, funcname):
        """Decorated function is a function wrapper

        This function takes two arguments, the container and the name of the
        function to wrap. The wrapping is performed during `uisetup`.
        (there is no extension support)

        example::

            # Required, otherwise the function will not be wrapped
            uisetup = eh.finaluisetup

            @eh.wrapfunction(discovery, 'checkheads')
            def wrapcheckheads(orig, *args, **kwargs):
                ui.note(b'His head smashed in and his heart cut out')
                return orig(*args, **kwargs)
        """

        def dec(wrapper):
            self._functionwrappers.append((container, funcname, wrapper))
            return wrapper

        return dec
