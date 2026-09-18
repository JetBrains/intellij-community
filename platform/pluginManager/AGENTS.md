# Plugin Manager Specifications

Plugin manager behavior specifications start at the [specification index](../platform-impl/spec/plugin-manager/README.md).

Before you change behavior, compare the file with each specification `targets` field. Read each matching specification.

Update the owning specification in the same changelist when behavior changes.

When code moves into this directory, update its specification target and `// @spec` backlink in the same changelist.

An implementation-only refactor does not need a specification change. State this in the final report.

If you find existing drift, report the requirement and evidence.
Continue when the change remains safe. Repair the drift in a separate commit.

The `testFramework` module checks only plugin manager specifications.
Each reference test supplies its specification root and required sections.

A file without a matching target has no specification duty.
