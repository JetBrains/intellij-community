# 0118. The kotlinx serializer is a public utility of the shared module

Date: 2026-09-29

## Status

Accepted ([IJPL-228928](https://youtrack.jetbrains.com/issue/IJPL-228928)).

## Context

`kotlinxSerializer()` builds a key serializer for any `@Serializable` type. It stores the value as clean nested
maps and lists. It was internal to the shared module, so only the tests could use it. A plugin with a data-class
setting had to write its serializer by hand.

The bridge goes through a `Json` tree. The API module depends only on `kotlinx.serialization.core`, and the API is
format neutral ([ADR 0003](./0003-the-settings-api-is-format-neutral.md)).

## Decision

**`kotlinxSerializer()` and its reified overload are public and `@Experimental` in the shared module. The API
module does not hold them.** They are an implementation utility that builds a serializer, not a part of the
settings model.

## Consequences

- A plugin can build a serializer for a `@Serializable` type without code of its own.
- A plugin that uses it depends on the shared module.
- The API module stays free of `Json`.
- The resulting behavior is in the [setting values spec](../spec/setting-values.spec.md).

## Rejected alternatives

### Move the bridge to the API module

The API module would need the `Json` dependency. The API would also hold a utility that is not a part of the
settings model.

### A thin API entry point that delegates to a service in the shared module

It keeps plugins on the API module only. It adds a service and an indirection for one utility.
