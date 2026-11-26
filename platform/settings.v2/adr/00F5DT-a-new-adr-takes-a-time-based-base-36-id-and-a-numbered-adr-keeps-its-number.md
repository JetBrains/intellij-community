# 00F5DT. A new ADR takes a time-based base 36 id, and a numbered ADR keeps its number

Date: 2026-11-26T09:40:42Z

## Status

Accepted ([IJPL-262099](https://youtrack.jetbrains.com/issue/IJPL-262099)).

## Context

An ADR had a sequential number, and `SettingsDocsTest` checked that each number from `0001` up to the highest
belongs to one ADR. A sequential number has no allocator. Two branches that work in parallel take the same next
number, and git reports nothing at the merge. The check fails after the rebase, and the author gives the ADR a new
number: in its file name, in its title and in each link and mention. It happened several times.

The existing numbers are in the code, the specs, the commit messages and the issues.

## Decision

**A new ADR takes a time-based base 36 id, and a numbered ADR keeps its number.**

- The id is the number of seconds from the base `2026-11-18T05:20:25Z` to the creation of the ADR.
- It is written in base 36 with the digits `0-9`, then `A-Z`, in uppercase, padded with zeros to six characters:
  `00K3F9`.
- The file is `adr/<ID>-<slug>.md`. The title is `# <ID>. <Title>`. A mention is `ADR <ID>`.
- The `Date:` line holds the creation time in UTC with seconds. The id equals that time minus the base.
- When the id is taken in the folder, it moves forward by one second until it is free. The `Date:` line moves
  with it.
- An ADR with a number `NNNN` keeps its file name, its title and its `Date:` line. It holds the id `00NNNN`, so
  no new id can equal an old number. No new ADR takes a number.
- `adr/new-adr.sh` creates a new ADR with its id.

`SettingsDocsTest` checks the shape of each file name, that no number is above the last number, that no two ADRs
or fold entries hold one id, and that the id of a new ADR matches its title and its `Date:` line.

## Consequences

- Two branches no longer take the same id, unless they create an ADR in the same second. Then the test finds the
  duplicate after the merge, and the ADR that is not in master moves one second forward.
- Every link and every `ADR NNNN` mention stays valid. Nothing is renamed.
- The ids are not sequential, so the test no longer checks for a gap. It cannot find a fold entry that a merge of
  an ADR chain lost. The author of a merge checks it by hand.
- The file names do not sort by time across the two shapes: `0173-…` sorts before `01ABCD-…`, and `0200-…` would
  sort after it. Code that orders the ADRs orders them by the id, with `NNNN` as `00NNNN`.
- An id says nothing about the order of two ADRs to a reader. The `Date:` line does.
- The rules are in the [settings v2 rules](../AGENTS.md).

## Rejected alternatives

### Rename every ADR to the new id

All ADRs would have one shape. It breaks every reference to an ADR number outside the repository tree: in the
commit messages and in the issues. It also changes about 170 files and each link to them for no gain.

### Keep the numbers and renumber after a clash

It is the state before this decision. The clash costs a rename of the file, of the title and of each link, and it
comes back with each parallel branch.

### A random id

A random id needs no clock, but it carries no information. A time-based id sorts in the order of creation, and a
test can check it against the `Date:` line.
