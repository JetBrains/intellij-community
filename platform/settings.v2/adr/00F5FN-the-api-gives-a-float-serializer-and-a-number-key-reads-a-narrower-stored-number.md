# 00F5FN. The API gives a Float serializer, and a number key reads a narrower stored number

Date: 2026-11-26T09:41:48Z

## Status

Accepted ([IJPL-262687](https://youtrack.jetbrains.com/issue/IJPL-262687)).

## Context

The API has serializers for the scalars `Int`, `Long`, `Double`, `Boolean` and `String`, and a key infers one of
them from its default value. It has none for `Float`.

- Platform settings hold `Float` values. The first adopter was the UI settings: the IDE scale, the presentation
  mode scale and the size of the custom font. It declared a private serializer.
- A settings value has one floating point kind, a double. A float that is widened to a double shows the binary
  error of the float: `1.1f` becomes `1.100000023841858`. A user edits the config by hand, so the stored number
  must be the number that the user set.
- A user who edits the config by hand writes `2`, not `2.0`. The stored value is then an integer value.
- The JSON reader of a config gives the smallest kind that holds a number: an int, a long or a double.
- A serializer rejects an incorrect stored value with `IllegalArgumentException`, and the key then resolves to its
  default ([ADR 0047](./0047-a-wrongly-typed-value-falls-back-to-the-default.md)).

## Decision

**The API gives `FloatSerializer`, which stores a float as the double of its shortest decimal form. A key of a number
type reads a stored number of a narrower kind.**

The serializer:

- `1.1f` is stored as `1.1`. For the few floats whose shortest decimal form does not narrow back to the same float,
  it stores the exact double of the float. So a key reads back every float that it stored.
- A key with a `Float` default infers this serializer. A key with the static type `Float` or `Float?` infers it too.
- It composes with `nullable()`, `listOf()` and `mapOf()`, as the other scalar serializers do.

The read of a number:

- A float key reads a double value as the nearest float. It also reads an int value and a long value.
- A double key reads an int value and a long value.
- A long key reads an int value. A config file gives the smallest kind of a number, so a small long comes back as
  an int.
- A finite number that is out of the range of a float throws `IllegalArgumentException`, so the key resolves to its
  default. A stored infinity or NaN reads as the same float.
- Any other stored value throws `IllegalArgumentException`.

## Consequences

- A feature with a `Float` setting needs no serializer of its own, and every feature stores a float in one form.
- A config shows the number that the user set, not the binary neighbor of it.
- A whole number in a hand-edited config works for a `Float` key and for a `Double` key.
- A `Long` key keeps a small value across a reload of its config file. Before, it resolved to its default.
- A stored double that has more digits than a float can hold loses them at the read. A feature that needs them
  uses a `Double` key.
- An int key does not read a long value or a double value, and a long key does not read a double value. A value
  that can lose its meaning at the read stays an error.
- The resulting behavior is in the [setting values spec](../spec/setting-values.spec.md).

## Rejected alternatives

### Widen the float to a double

It is the simplest conversion, and it is exact. It stores `1.100000023841858` for `1.1f`, which a user does not
expect to see in a config and would not write.

### Each feature keeps a private serializer

It is the state before this decision. Each feature can choose another stored form, and each one repeats the code.

### No `Float` in the API, a feature uses a `Double` key

The API stays smaller. The platform code that holds these values works with `Float`, so each adopter would convert
at every read and write.
