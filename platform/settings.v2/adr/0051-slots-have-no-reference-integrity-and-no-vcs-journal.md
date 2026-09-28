# 0051. Slots have no reference integrity and no VCS journal

Date: 2026-07-20

## Status

Accepted ([IJPL-228928](https://youtrack.jetbrains.com/issue/IJPL-228928)).

## Context

After a slot moves or is dropped, a stored discriminator elsewhere can still name the old slot. A rename could
also be journaled so another branch replays it.

## Decision

**The engine does not fix a context whose stored discriminator names a moved or dropped slot; that is the
owner's job. A slot operation is an ordinary edit of a versioned config, and VCS is the truth: a branch switch
brings that branch's slots, not a state to replay.**

## Consequences

- No hidden cross-context rewriting, no journal to keep.
- A rename that must reach other branches is a commit on them.
- The resulting behavior is in the [multidimensional settings spec](../spec/multidimensional-settings.spec.md).
