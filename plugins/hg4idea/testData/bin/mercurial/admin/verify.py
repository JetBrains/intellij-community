# admin/verify.py - better repository integrity checking for Mercurial
#
# Copyright 2023 Octobus <contact@octobus.net>
#
# This software may be used and distributed according to the terms of the
# GNU General Public License version 2 or any later version.

import collections
import copy
import functools

from ..i18n import _
from .. import error, pycompat, registrar, requirements
from ..utils import stringutil


verify_table = {}
verify_alias_table = {}
check = registrar.verify_check(verify_table, verify_alias_table)


# Use this to declare options/aliases in the middle of the hierarchy.
# Checks like these are not run themselves and cannot have a body.
# For an example, see the `revlogs` check.
def noop_func(*args, **kwargs):
    return


@check(b"working-copy.dirstate", alias=b"dirstate")
def check_dirstate(ui, repo, **options):
    ui.status(_(b"checking dirstate\n"))

    parent1, parent2 = repo.dirstate.parents()
    m1 = repo[parent1].manifest()
    m2 = repo[parent2].manifest()
    errors = 0

    is_narrow = requirements.NARROW_REQUIREMENT in repo.requirements
    narrow_matcher = repo.narrowmatch() if is_narrow else None
    for err in repo.dirstate.verify(m1, m2, parent1, narrow_matcher):
        ui.warn(err)
        errors += 1

    return errors


# Tree of all checks and their associated function
pyramid = {}


def build_pyramid(table, full_pyramid):
    """Create a pyramid of checks of the registered checks.
    It is a name-based hierarchy that can be arbitrarily nested."""
    for entry, func in sorted(table.items(), key=lambda x: x[0], reverse=True):
        cursor = full_pyramid
        levels = entry.split(b".")
        for level in levels[:-1]:
            current_node = cursor.setdefault(level, {})
            cursor = current_node
        if cursor.get(levels[-1]) is None:
            cursor[levels[-1]] = (entry, func)
        elif func is not noop_func:
            m = b"intermediate checks need to use `verify.noop_func`"
            raise error.ProgrammingError(m)


def find_checks(name, table=None, alias_table=None, full_pyramid=None):
    """Find all checks for a given name and returns a dict of
    (qualified_check_name, check_function)

    # Examples

    Using a full qualified name:
    "working-copy.dirstate" -> {
        "working-copy.dirstate": CF,
    }

    Using a *prefix* of a qualified name:
    "store.revlogs" -> {
        "store.revlogs.changelog": CF,
        "store.revlogs.manifestlog": CF,
        "store.revlogs.filelog": CF,
    }

    Using a defined alias:
    "revlogs" -> {
        "store.revlogs.changelog": CF,
        "store.revlogs.manifestlog": CF,
        "store.revlogs.filelog": CF,
    }

    Using something that is none of the above will be an error.
    """
    if table is None:
        table = verify_table
    if alias_table is None:
        alias_table = verify_alias_table

    if name == b"full":
        return table
    checks = {}

    # is it a full name?
    check = table.get(name)

    if check is None:
        # is it an alias?
        qualified_name = alias_table.get(name)
        if qualified_name is not None:
            name = qualified_name
            check = table.get(name)
        else:
            split = name.split(b".", 1)
            if len(split) == 2:
                # split[0] can be an alias
                qualified_name = alias_table.get(split[0])
                if qualified_name is not None:
                    name = b"%s.%s" % (qualified_name, split[1])
                    check = table.get(name)
    else:
        qualified_name = name

    # Maybe it's a subtree in the check hierarchy that does not
    # have an explicit alias.
    levels = name.split(b".")
    if full_pyramid is not None:
        if not full_pyramid:
            build_pyramid(table, full_pyramid)

        pyramid.clear()
        pyramid.update(full_pyramid.items())
    else:
        build_pyramid(table, pyramid)

    subtree = pyramid
    # Find subtree
    for level in levels:
        subtree = subtree.get(level)
        if subtree is None:
            hint = error.getsimilar(list(alias_table) + list(table), name)
            hint = error.similarity_hint(hint)

            raise error.InputError(_(b"unknown check %s" % name), hint=hint)

    # Get all checks in that subtree
    if isinstance(subtree, dict):
        stack = list(subtree.items())
        while stack:
            current_name, entry = stack.pop()
            if isinstance(entry, dict):
                stack.extend(entry.items())
            else:
                # (qualified_name, func)
                checks[entry[0]] = entry[1]
    else:
        checks[name] = check

    return checks


def pass_options(
    ui,
    checks,
    options,
    table=None,
    alias_table=None,
    full_pyramid=None,
):
    """Given a dict of checks (fully qualified name to function), and a list
    of options as given by the user, pass each option down to the right check
    function."""
    ui.debug(b"passing options to check functions\n")
    to_modify = collections.defaultdict(dict)

    if not checks:
        raise error.Error(_(b"`checks` required"))

    for option in sorted(options):
        split = option.split(b":")
        hint = _(
            b"syntax is 'check:option=value', "
            b"eg. revlogs.changelog:copies=yes"
        )
        option_error = error.InputError(
            _(b"invalid option '%s'") % option, hint=hint
        )
        if len(split) != 2:
            raise option_error

        check_name, option_value = split
        if not option_value:
            raise option_error

        split = option_value.split(b"=")
        if len(split) != 2:
            raise option_error

        option_name, value = split
        if not value:
            raise option_error

        path = b"%s:%s" % (check_name, option_name)

        matching_checks = find_checks(
            check_name,
            table=table,
            alias_table=alias_table,
            full_pyramid=full_pyramid,
        )
        for name in matching_checks:
            check = checks.get(name)
            if check is None:
                msg = _(b"specified option '%s' for unselected check '%s'\n")
                raise error.InputError(msg % (name, option_name))

            assert hasattr(check, "func")  # help Pytype

            if not hasattr(check.func, "options"):
                raise error.InputError(
                    _(b"check '%s' has no option '%s'") % (name, option_name)
                )

            try:
                matching_option = next(
                    (o for o in check.func.options if o[0] == option_name)
                )
            except StopIteration:
                raise error.InputError(
                    _(b"check '%s' has no option '%s'") % (name, option_name)
                )

            # transform the argument from cli string to the expected Python type
            _name, typ, _docstring = matching_option

            as_typed = None
            if isinstance(typ, bool):
                as_bool = stringutil.parsebool(value)
                if as_bool is None:
                    raise error.InputError(
                        _(b"'%s' is not a boolean ('%s')") % (path, value)
                    )
                as_typed = as_bool
            elif isinstance(typ, list):
                as_list = stringutil.parselist(value)
                if as_list is None:
                    raise error.InputError(
                        _(b"'%s' is not a list ('%s')") % (path, value)
                    )
                as_typed = as_list
            else:
                raise error.ProgrammingError(b"unsupported type %s", type(typ))

            if option_name in to_modify[name]:
                raise error.InputError(
                    _(b"duplicated option '%s' for '%s'") % (option_name, name)
                )
            else:
                assert as_typed is not None
                to_modify[name][option_name] = as_typed

    # Manage case where a check is set but without command line options
    # it will later be set with default check options values
    for name, f in checks.items():
        if name not in to_modify:
            to_modify[name] = {}

    # Merge default options with command line options
    for check_name, cmd_options in to_modify.items():
        check = checks.get(check_name)
        func = checks[check_name]
        merged_options = {}
        # help Pytype
        assert check is not None
        assert check.func is not None
        assert hasattr(check.func, "options")

        if check.func.options:
            # copy the default value in case it's mutable (list, etc.)
            merged_options = {
                o[0]: copy.deepcopy(o[1]) for o in check.func.options
            }
            if cmd_options:
                for k, v in cmd_options.items():
                    merged_options[k] = v
        options = pycompat.strkwargs(merged_options)
        checks[check_name] = functools.partial(func, **options)
        ui.debug(b"merged options for '%s': '%r'\n" % (check_name, options))

    return checks


def get_checks(
    repo,
    ui,
    names=None,
    options=None,
    table=None,
    alias_table=None,
    full_pyramid=None,
):
    """Given a list of function names and optionally a list of
    options, return matched checks with merged options (command line options
    values take precedence on default ones)

    It runs find checks, then resolve options and returns a dict of matched
    functions with resolved options.
    """
    funcs = {}

    if names is None:
        names = []

    if options is None:
        options = []

    # find checks
    for name in names:
        matched = find_checks(
            name,
            table=table,
            alias_table=alias_table,
            full_pyramid=full_pyramid,
        )
        matched_names = b", ".join(matched)
        ui.debug(b"found checks '%s' for name '%s'\n" % (matched_names, name))
        funcs.update(matched)

    funcs = {n: functools.partial(f, ui, repo) for n, f in funcs.items()}

    # resolve options
    checks = pass_options(
        ui,
        funcs,
        options,
        table=table,
        alias_table=alias_table,
        full_pyramid=full_pyramid,
    )

    return checks
