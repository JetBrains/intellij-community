# 0138. Named scopes are postponed, and their place in the chain is open

Date: 2026-10-01. Merged: 2026-10-14.

## Status

Accepted ([IJPL-228928](https://youtrack.jetbrains.com/issue/IJPL-228928)).

## Context

Scope support should work for any setting, not per feature. A scope cuts across the file and project axis, so its
place in resolution is not obvious. A file can match several overlapping scopes.

Greg Shrago pointed out that the current scopes implementation is bad, and per-scope layers built on it would
inherit its problems.

The context chain has a strict hierarchy: file, directories, root, project, application. A scope is flexible. A
file mask can match files anywhere in the tree, so a scope has no natural place in that hierarchy.

## Decision

**Named scopes are postponed. Their place in the chain is not decided. They come later, if a real need appears.
Scope support is not built on the current scopes. It waits for a scope rework, which is a separate effort.**

- A directory config gives a part of what a scope gives, for the files of one directory tree.
- One option is a node between the content roots and the project. It is not decided.

## Consequences

- The settings engine ships without scopes.
- A setting for a file mask has no home yet.
- The resolution of a scope can be expensive, and a read of a setting must be fast. A scope design must solve
  that first.
- Scope support depends on work outside this task.
- The resulting behavior is in the [settings contexts spec](../spec/settings-contexts.spec.md).

## Earlier decisions

### 0038, 2026-07-13: named scopes are context nodes between directories and the project

Named scopes were extra context nodes in the existing parent chain, not a new dimension. The order was `exact file
→ enclosing directories → named scopes → project → app → user`. Among scopes, the project's existing scope order
decided (the list file colors use), and the first match won. A scope node's identity was
`scope|<userEditing>|<projectId>|<scopeId>`, and a write for a scope targeted that scope's layer, created on demand.
The narrowest-wins walk handled scopes unchanged, and a user overrode a single file directly. A resolved value then
depended on mutable scope definitions, so the cache key needed a scopes generation. "Deepest directory against a
very narrow scope" had no objectively right answer, and directories won for predictability. 0138 left the place of
a scope open, because a scope has no natural place in the strict hierarchy.

That model rejected two alternatives. Scopes above the file would force the user to create another scope just to
beat one file. A new ordering for scopes was not needed, because the project already orders its scopes.

### 0049, 2026-07-20: scopes are on hold until scopes are reworked

Scope support was not built on the current scopes, and it waited for a scope rework. 0038 stayed the intended model.
0138 kept the hold, but no longer keeps 0038 as the model.
