# 0155. A mutation at a context of a project that is not open is dropped

Date: 2026-10-15

## Status

Accepted ([IJPL-228928](https://youtrack.jetbrains.com/issue/IJPL-228928)).

## Context

The vault holds a model only for open projects. A project layer and a file layer find their service through the
open projects, so they offer no locality at a context of a project that is not open
([ADR 0129](./0129-a-layer-filters-keys-and-offers-localities-per-context.md)). A write there walks up the chain and
lands at the application context.

The vault is not strict about the order of a mutation and a project close. A mutation can arrive after its project
closed, and its value then leaks into the settings of every project. The default project is never open, so a
mutation at its context leaks the same way.

## Decision

**A valid mutation at a context of a project that is not open is dropped with a warning. The other mutations of
its batch still apply.** A mutation is a write or a slot operation. The context can be the project or a file of it.
A closed project and the default project are such projects.

- An invalid mutation fails the whole batch, at any context, as before. The vault validates every mutation of a
  batch before it drops any of them.
- A read at such a context still resolves through the application layers.

## Consequences

- A late write of a closed project no longer changes the application settings.
- Code that writes for the default project, such as settings for new projects, writes at the application context.
- A mutation while a project opens is dropped too. The platform lists a project as open only after
  `prepareProject`, `beforeOpen`, `configureWorkspace` and `addProjectRoot` in `ProjectManagerImpl`. On 2026-10-15
  no code writes there: the API has no users outside its own modules yet.
- The resulting behavior is in the [settings writes spec](../spec/settings-writes.spec.md).

## Rejected alternatives

### Fall through to the application context

It is the old behavior. A value of one project changes the settings of every project.

### Fail the write with an exception

A write that races with a project close is not an error of the caller. An exception would break callers for a
timing they do not control.

### Drop the whole batch

The other entries of the batch can be at open contexts. Dropping them loses values that have a place.
