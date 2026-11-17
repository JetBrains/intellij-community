# 0178. An enum is stored by its constant name, with no conversion

Date: 2026-11-17

## Status

Accepted ([IJPL-261939](https://youtrack.jetbrains.com/issue/IJPL-261939)).

## Context

A feature that adopts settings v2 often has an enum setting. The API had no serializer for an enum, so each feature
wrote its own, and each one could choose another stored form.

- A user edits the config by hand, so the stored value must be readable. An ordinal is not.
- A key infers the serializer of a scalar from its default value. An enum default is as common.
- A serializer rejects an incorrect stored value with `IllegalArgumentException`, and the key then resolves to its
  default ([ADR 0047](./0047-a-wrongly-typed-value-falls-back-to-the-default.md)).

## Decision

**The API gives an enum serializer that stores an entry by its constant name, `Enum.name`, as a string value. It
converts the name in no way. A key with an enum entry as its eager default infers this serializer.**

- `enumSerializer<E>()` gives the serializer for explicit use, such as a nullable key or a key with a default
  factory. It composes with `nullable()`, `listOf()` and `mapOf()`.
- `enumSerializer(enumClass)` gives the same serializer where the enum is known only as a class, for example in Java
  code.
- The inference takes the enum from the declaring class of the entry, so an entry with a body works.
- A stored value that is not a string, or a name that no constant has, throws `IllegalArgumentException`.

## Consequences

- Every feature stores an enum in one form, and a config shows the names that the code declares.
- The stored name is unique within its enum, so two constants never share a stored value.
- A rename of a constant breaks the values stored under the old name. They fall back to the default.
- A later mechanism can let a constant declare its exact stored value. Then a rename of the constant does not
  break the stored values. It is not part of this decision.
- The resulting behavior is in the [setting values spec](../spec/setting-values.spec.md).

## Rejected alternatives

### The ordinal

A number tells a user nothing, and a new constant in the middle of the enum changes the meaning of every stored
value after it.

### A converted name, such as lower camel case

A conversion can make two constants collide: `A_B` and `a_b` both give `aB`. The constant name is unique by the rules
of the language. The conversion also adds a second spelling of each name, which a user has to translate.
