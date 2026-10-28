# 0159. A provider declares the context kinds it supplies layers at

Date: 2026-10-28

## Status

Accepted ([IJPL-259782](https://youtrack.jetbrains.com/issue/IJPL-259782)).

## Context

A reload installs the layers of a scope only after every provider has answered
([ADR 0116](./0116-the-vault-drives-loading-with-no-timeout-and-a-provider-returns-its-layers.md)). The file layer is
slow, because it must find the directory configs in the whole project. So the application and the project contexts
wait for the file layer too, although no file layer can change their values.

The vault can install the layers of a fast provider early only when it knows which contexts a slow provider can
still change. The vault must know this before the providers answer.

## Decision

**A `LayerProvider` declares the range of context kinds that it supplies layers at: its widest kind and its
narrowest kind. The narrowest kind can be open, when the provider cannot tell it.**

- The application layer and the application defaults declare `APPLICATION` to `APPLICATION`.
- The project layer declares `PROJECT` to `PROJECT`.
- The file layer declares `FILE` with an open narrowest kind.
- A provider that declares nothing covers every kind. It keeps the behavior of today.

A provider must not return a layer outside its range. The vault logs an error for such a layer and drops it, so the
layer never reaches its context.

## Consequences

- A provider bug that returns a layer outside its range is visible in the log and fails the tests. It does not
  change the values of a context that the provider did not declare.
- The vault can split a reload by context kind
  ([ADR 0160](./0160-a-reload-installs-the-layers-kind-by-kind.md)).
- A slow provider must declare a narrow range. A slow provider with a wide range delays every context it covers. The
  KDoc of the API says this, and a plugin author must take care of it.
- The resulting behavior is in the [layer loading spec](../spec/layer-loading.spec.md).

## Rejected alternatives

### A provider declares concrete contexts

The project context differs per project, and a directory context is not known before the provider answers. So a
provider cannot name its contexts in advance.

### The vault learns the range from the returned layers

The vault knows the returned layers only after the provider answers. That is too late to install the other
providers early.
