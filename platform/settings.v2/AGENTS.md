# Settings v2

Settings v2 is the new settings engine. A typed key names a setting, a context says where it is read from, and
layers supply the values. The vault resolves and stores them.

| Module                                  | Folder     |
|-----------------------------------------|------------|
| `intellij.platform.settings.v2` (API)   | `src/`     |
| `intellij.platform.settings.v2.shared`  | `shared/`  |
| `intellij.platform.settings.v2.backend` | `backend/` |
| `intellij.platform.settings.v2.test`    | `test/`    |

Some modules of the feature live outside `community/`. Their own `AGENTS.md` points here, and `spec/` and `adr/`
cover them too.

## Where each kind of text goes

| Kind of text | Home |
| --- | --- |
| Behavior that a caller or a user can observe | a spec in `spec/` |
| A decision and its reason | a decision record (ADR) in `adr/` |
| How the code works | KDoc on the symbol |
| A rule for future changes | this file |

A sentence is moved to its home, never dropped.

## Before a change

- Read the specs whose `targets` cover the files you change. Read the ADRs that they link.
- The specs are the source of truth for behavior. An updated test does not replace an updated spec.
- A behavior change updates the owning spec in the same commit. If no spec owns the behavior, add one.
- A refactoring with no behavior change updates no spec. Say so in the summary.
- A decision that the work agrees on gets a new ADR.

## Tests

- The tests of `intellij.platform.settings.v2.test` run only under Bazel. `tests.cmd` refuses the module. Run them
  from the ultimate root with `./bazel.cmd test @community//platform/settings.v2/test:test_test`, and from the
  community root with `//platform/settings.v2/test:test_test`. Add `--test_filter=<FQN>` for one class.

## Specs

Specs follow [`community/.ai/spec/SPEC_GUIDE.md`](../../.ai/spec/SPEC_GUIDE.md). The rules below add to it or
override it.

- A spec is `spec/<feature>.spec.md`. Keep the folder flat until it needs subfolders by area.
- Every spec declares `style: plain-1`.
- Paths in `targets` and `[@test]` are relative to the spec file: `../src/...`, `../shared/src/...`,
  `../backend/src/...`, `../test/src/...`.
- A main target file carries a backlink in its header, relative to that file. From
  `src/com/intellij/platform/settings/v2/` it is `// @spec ../../../../../../spec/<feature>.spec.md`.
- Only a Kotlin or a Java target carries a backlink. An XML target, such as a module descriptor, has none. A spec or
  an ADR may still name an XML file in `targets` or in a link.
- Every path is relative, never rooted at a repository. The `community/` tree is also its own repository, so a
  path rooted at either repository breaks in the other one.
- Override: there is no concept glossary yet. Use the terms of the existing specs and KDoc, and define a new term
  in the spec that introduces it.
- `SettingsDocsTest` checks every link between the specs, the ADRs and the sources. A link that goes outside the
  checked-out repository is skipped, so a run from `community/` does not check the links to the ultimate part. When
  you move, rename or delete a file, update its old path in the same commit, and run the test.

## Decision records

- An ADR is `adr/NNNN-<slug>.md`.
- Numbers are sequential and never reused: each number from `0001` up to the highest belongs to exactly one ADR
  file or one fold entry `### NNNN,`. `SettingsDocsTest` checks it. After a rebase, a clash with another branch
  fails the check. Then renumber your ADR and its links.
- The title states the decision as a sentence, not a topic.
- Sections: `Status`, `Context`, `Decision`, `Consequences`, and `Rejected alternatives` when there were real
  alternatives. A `Date:` line follows the title.
- Status values: `Proposed`, `Accepted`, `Accepted. Amended by [ADR NNNN](link)`, `Superseded by [ADR NNNN](link)`,
  `Deprecated`. A relation is a link, so the chain check sees it. Add the issue link when the ADR belongs to an
  issue.
- An agent writes a new ADR as `Proposed`. Only the user accepts it. The agent sets `Accepted` only after the user
  says so, in the conversation. Code and specs may follow a `Proposed` ADR.
- A `Proposed` ADR does not go to master. `SettingsDocsTest` fails on each `Proposed` ADR, also during the work on a
  branch. Such a failure is expected until the user accepts the ADR.
- A `Proposed` ADR that amends or supersedes another ADR does not change the status of that ADR. The old status
  changes when the new ADR is accepted.
- An accepted ADR is not rewritten. A change to the decision is a new ADR that amends or supersedes it, and only
  the status of the old ADR changes.
- A chain of amendments and supersessions holds at most 2 ADRs. `SettingsDocsTest` checks it. Merge a longer
  chain into one ADR, as [Merging an ADR chain](#merging-an-adr-chain) says. This is the only case where an
  accepted ADR is rewritten.
- A folded ADR has no file in `adr/`. A link or a mention of an ADR that is not in `adr/` points to a folded ADR.
  Search `adr/` for `### NNNN,` to find the merged ADR. The old text is in the git history of `adr/`.
- One decision per ADR. An ADR links the spec that holds the resulting behavior.
- KDoc may cite a decision as `ADR NNNN` when the code follows from it.
- In `adr/` and `spec/`, every mention of an ADR or a spec is a relative link, not plain text. This applies to
  each mention, not only to the first one.

### Merging an ADR chain

A merge folds a chain into its newest ADR. The merged ADR states the current decision, and it keeps every earlier
decision with its reason. A merge only reorganizes what the ADRs already say. It never changes a decision.

1. Find the group. Start from the long path that `SettingsDocsTest` reports. Look at the whole connected group
   around it: one ADR can amend two ADRs of the group, and an ADR outside the path can amend one inside it. Merge
   the ADRs that are one decision. Leave out an ADR that is its own decision and only touches the chain. It keeps
   its relation to the merged ADR.
2. Write the merged ADR into the file of the newest ADR of the chain:
   - Number and status: those of the newest ADR. When the newest ADR is `Proposed`, the merged ADR is `Proposed`.
   - Title and file name: the merged decision as a sentence, not the narrow title of the newest ADR. Rename the file
     when the title changes.
   - `Date:` the date of the newest ADR, then `Merged: <date>`.
   - `Status`: the issue link, and every relation of the chain to an ADR outside the chain, as links. Drop the
     relations inside the chain.
   - `Context`: the forces of all the merged ADRs, with no repetition.
   - `Decision`: the current decision in full. A later amendment wins over an earlier text.
   - `Consequences`: the consequences that are still true.
   - `Rejected alternatives`: the alternatives of the current decision. A replaced decision is not a rejected
     alternative.
   - `Earlier decisions`: the last section. It has one entry per folded ADR, in time order. An entry keeps the
     folded decision, its consequences and its rejected alternatives, and it says why the decision changed. A part
     that a folded ADR added and a later ADR changed goes to the entry of the folded ADR. The newest ADR has no
     entry. Write the folded number in plain digits, because a plain `ADR NNNN` mention fails the link check:

     ```markdown
     ## Earlier decisions

     ### 0035, 2026-07-11: the prototype takes an explicit plugin id

     The decision and its consequences. Why it changed, and which later decision replaced it.
     ```

     A folded ADR can be a merged ADR itself. Then its fold entries move into the new merged ADR. Every entry, moved
     or new, stays in time order. `SettingsDocsTest` fails on a fold entry that the merge lost.
3. Compare each folded ADR with the merged ADR. Every reason, consequence, example, rejected alternative and name
   of a person who agreed must be in the merged ADR. Then delete the files of the folded ADRs.
4. Re-point the references in `adr/`, `spec/`, the sources of all the settings v2 modules, and the `AGENTS.md`
   files. A link to a folded ADR, or to the old file name of the merged ADR, points to the merged file. A link text
   or an `ADR NNNN` mention of a folded number takes the merged number.
5. Read each changed sentence again:
   - A sentence can now be false. "Older ADRs put a user node into the chain" is false of the merged ADR when the
     idea is only in its `Earlier decisions`. Say that in the sentence.
   - An older ADR that names the merged ADR can read as if it came later. Write
     `the key identity rule ([ADR 0124](...))`, not `[ADR 0124](...) allowed`.
   - An ADR that named two ADRs of the chain now names the merged ADR twice. Keep one.
6. Run `SettingsDocsTest`. Every check must pass. Commit one chain per commit. The subject names the decision and
   the merged number, and the message lists the folded numbers.
