# Settings v2

Settings v2 is the new settings engine. A typed key names a setting, a context says where it is read from, and
layers supply the values. The vault resolves and stores them.

| Module                                  | Folder     |
|-----------------------------------------|------------|
| `intellij.platform.settings.v2` (API)   | `src/`     |
| `intellij.platform.settings.v2.shared`  | `shared/`  |
| `intellij.platform.settings.v2.shared.essential` | `shared/essential/` |
| `intellij.platform.settings.v2.backend.baseline` | `backend/baseline/` |
| `intellij.platform.settings.v2.backend` | `backend/` |
| `intellij.platform.settings.v2.vcs` | `vcs/` |
| `intellij.platform.settings.v2.test`    | `test/`    |
| `intellij.platform.settings.v2.testKeys` | `testKeys/` |
| `intellij.platform.settings.v2.testKeys.plugin` | `testKeys/plugin/` |

The API module, `intellij.platform.settings.v2`, is an embedded module of the `core.platform` module set. So it loads
in the core class loader, and a module of that set can depend on it. The vault module,
`intellij.platform.settings.v2.shared`, is a content module of the same set. The other settings modules are in the
`essential` module set. See [ADR 0182](adr/0182-the-api-module-is-embedded-in-core-platform-and-the-vault-module-is-a-content-module-of-the-same-set.md).

A module that reads a key depends only on the API module. The vault module comes at run time: from the module set
in a product, and from the `RUNTIME` dependencies of `intellij.platform.main` and of
`intellij.platform.testFramework.common` in a launch from a module class path. An application with another
bootstrap must put the vault module on its class path itself.

The API module and the vault module depend only on modules of `core.platform` and of the boot jars.
`SettingsV2CoreDependenciesTest` checks it. Code of the vault that needs another module goes to
`intellij.platform.settings.v2.shared.essential`. See
[ADR 0181](adr/0181-the-vault-module-depends-only-on-core-platform-modules-and-the-code-that-needs-more-lives-in-the-shared-essential-module.md).

Some modules of the feature live outside `community/`. Their own `AGENTS.md` points here, and `spec/` and `adr/`
cover them too.

- A key that an integration test writes inside the IDE under test lives in `intellij.platform.settings.v2.testKeys`.
  A lambda test gets it through its wrapper plugin, and a driver test adds the plugin
  `com.intellij.platform.settings.v2.testKeys.plugin` to the product. See
  [ADR 0165](adr/0165-a-test-writes-a-key-of-the-test-keys-module-inside-the-ide-under-test.md).
- The descriptor of that plugin is a test resource. Keep it one: a plugin with a production descriptor and no test
  framework dependency is a compatible plugin, so every product build publishes it and fails on its descriptor. A
  driver test gets the module from `settingsTestKeysPluginModule()`, which also lets the dev build pack a test module.
  The test module of such a test depends on `intellij.platform.settings.v2.testKeys.plugin` and on the keys module
  with the `TEST` scope. The CI compiles only what a test module depends on, and the dev build packs only compiled
  output, so without the dependency the plugin has no descriptor on the CI.

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
- More tests of the feature exist only in the ultimate repository: the split-mode tests, and the tests of the light
  modes and of their upgrades. [`remote-dev/settings/v2/AGENTS.md`](../../../remote-dev/settings/v2/AGENTS.md) says
  where each kind is. Look there before you say that no test covers a product mode. The link resolves only in the
  ultimate repository.
- In the unit-test mode no migration check starts by itself. A unit test of the engine starts a check itself, and a
  unit test of a migration calls the migration.

## Specs

Specs follow [`community/.ai/spec/SPEC_GUIDE.md`](../../.ai/spec/SPEC_GUIDE.md). The rules below add to it or
override it.

- A spec is `spec/<feature>.spec.md`. Keep the folder flat until it needs subfolders by area.
- Every spec declares `style: plain-1`.
- Paths in `targets` and `[@test]` are relative to the spec file: `../src/...`, `../shared/src/...`,
  `../backend/baseline/src/...`, `../backend/src/...`, `../vcs/src/...`, `../testKeys/src/...`, `../test/src/...`.
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

- A new ADR is `adr/<ID>-<slug>.md`. Create it with `adr/new-adr.sh "<decision as a sentence>"`: the script computes
  the id and writes the template. See
  [ADR 00F5DT](adr/00F5DT-a-new-adr-takes-a-time-based-base-36-id-and-a-numbered-adr-keeps-its-number.md).
- The id is the number of seconds from `2026-11-18T05:20:25Z` to the creation of the ADR. It is written in uppercase
  base 36 (`0-9`, then `A-Z`), padded with zeros to six characters: `00K3F9`. The title is `# <ID>. <Title>`.
- The `Date:` line holds the creation time in UTC with seconds. The id equals that time minus the base.
- Never choose an id by hand. The only exception is the repair of a duplicate id, below.
- An older ADR has a number: `adr/NNNN-<slug>.md`. It keeps its file name, its title and its `Date:` line. It holds
  the id `00NNNN`. Do not pad an old number to six characters in a link or a mention. No new ADR takes a number.
- `SettingsDocsTest` checks the shape of each file name, and that no number is above the last number. It checks that
  no two ADRs or fold entries hold one id. It checks that a six-character id matches its title and its `Date:` line.
- Two branches that create an ADR in the same second get the same id. The test finds it after the merge. Then move
  the ADR that is not in master one second forward: its file name, its title, its `Date:` line and each link and
  mention.
- Below, `<ID>` stands for a six-character id or for an old number.
- The title states the decision as a sentence, not a topic.
- Sections: `Status`, `Context`, `Decision`, `Consequences`, and `Rejected alternatives` when there were real
  alternatives. A `Date:` line follows the title.
- Status values: `Proposed`, `Accepted`, `Accepted. Amended by [ADR <ID>](link)`, `Superseded by [ADR <ID>](link)`,
  `Deprecated`. A relation is a link, so the chain check sees it. Add the issue link when the ADR belongs to an
  issue.
- An agent writes a new ADR as `Proposed`. Only the user accepts it. The agent sets `Accepted` only after the user
  says so, in the conversation. Code and specs may follow a `Proposed` ADR.
- A `Proposed` ADR does not go to master. `SettingsDocsTest` fails on each `Proposed` ADR, also during the work on a
  branch. Such a failure is expected until the user accepts the ADR.
- A `Proposed` ADR that amends or supersedes another ADR does not change the status of that ADR. The old status
  changes when the new ADR is accepted.
- The rules below about an accepted ADR apply to an ADR that is in master: its file exists in `origin/master`. An
  ADR that is not in master yet belongs to the branch. Edit it in place, merge it with another ADR of the branch, or
  drop it, whatever its status is. Never amend or supersede it with a new ADR in the same branch.
- An accepted ADR keeps its decision. You may reword it, clarify it, or add details to it, when the decision stays
  the same. A missed detail or a wrong statement about the code is fixed in place. You may also add a missed
  consequence or rejected alternative.
- An addition that changes the meaning of an ADR is a new ADR. A sign of it: the title no longer states the
  decision, and the ADR would need a rename.
- A change to the decision is a new ADR that amends or supersedes it, and only the status of the old ADR changes.
- A chain of amendments and supersessions holds at most 2 ADRs. `SettingsDocsTest` checks it. Merge a longer
  chain into one ADR, as [Merging an ADR chain](#merging-an-adr-chain) says.
- A folded ADR has no file in `adr/`. A link or a mention of an ADR that is not in `adr/` points to a folded ADR.
  Search `adr/` for `### <ID>,` to find the merged ADR. The old text is in the git history of `adr/`.
- One decision per ADR. An ADR links the spec that holds the resulting behavior.
- KDoc may cite a decision as `ADR <ID>` when the code follows from it.
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
   - Id and status: those of the newest ADR. When the newest ADR is `Proposed`, the merged ADR is `Proposed`.
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
     entry. Write the folded id plain, with no `ADR` before it, because a plain `ADR <ID>` mention fails the link
     check:

     ```markdown
     ## Earlier decisions

     ### 0035, 2026-07-11: the prototype takes an explicit plugin id

     The decision and its consequences. Why it changed, and which later decision replaced it.
     ```

     A folded ADR can be a merged ADR itself. Then its fold entries move into the new merged ADR. Every entry, moved
     or new, stays in time order. Check by hand that the merge lost no fold entry: the test cannot find a lost one.
3. Compare each folded ADR with the merged ADR. Every reason, consequence, example, rejected alternative and name
   of a person who agreed must be in the merged ADR. Then delete the files of the folded ADRs.
4. Re-point the references in `adr/`, `spec/`, the sources of all the settings v2 modules, and the `AGENTS.md`
   files. A link to a folded ADR, or to the old file name of the merged ADR, points to the merged file. A link text
   or an `ADR <ID>` mention of a folded id takes the merged id.
5. Read each changed sentence again:
   - A sentence can now be false. "Older ADRs put a user node into the chain" is false of the merged ADR when the
     idea is only in its `Earlier decisions`. Say that in the sentence.
   - An older ADR that names the merged ADR can read as if it came later. Write
     `the key identity rule ([ADR 0124](...))`, not `[ADR 0124](...) allowed`.
   - An ADR that named two ADRs of the chain now names the merged ADR twice. Keep one.
6. Run `./community/tools/bt.cmd --module intellij.platform.settings.v2.test --filter com.intellij.platform.settings.v2.docs.SettingsDocsTest`. Every check must pass. Commit one chain per commit. The subject names the decision and
   the merged id, and the message lists the folded ids.
