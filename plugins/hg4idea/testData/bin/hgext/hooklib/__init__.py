"""collection of simple hooks for common tasks (EXPERIMENTAL)

This extension provides a number of simple hooks to handle issues
commonly found in repositories with many contributors:
- email notification when changesets move from draft to public phase
- email notification when changesets are obsoleted
- enforcement of draft phase for all incoming changesets
- enforcement of a no-branch-merge policy
- enforcement of a no-multiple-heads policy

The implementation of the hooks is subject to change, e.g. whether to
implement them as individual hooks or merge them into the notify
extension as option. The functionality itself is planned to be supported
long-term.
"""
from __future__ import absolute_import
from . import (
    changeset_obsoleted,
    changeset_published,
)

# configtable is only picked up from the "top-level" module of the extension,
# so expand it here to ensure all items are properly loaded
configtable = {}
configtable.update(changeset_published.configtable)
configtable.update(changeset_obsoleted.configtable)
