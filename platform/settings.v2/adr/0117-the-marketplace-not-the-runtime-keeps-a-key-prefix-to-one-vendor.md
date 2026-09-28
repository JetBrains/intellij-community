# 0117. The marketplace, not the runtime, keeps a key prefix to one vendor

Date: 2026-09-29

## Status

Accepted ([IJPL-228928](https://youtrack.jetbrains.com/issue/IJPL-228928)). Amends
[ADR 0111](./0111-the-registry-takes-keys-only-from-their-own-plugin-and-a-prefix-belongs-to-one-plugin.md).

## Context

[ADR 0111](./0111-the-registry-takes-keys-only-from-their-own-plugin-and-a-prefix-belongs-to-one-plugin.md) gives a
prefix to one plugin in a process. The first declaration wins. A second plugin that declares the prefix logs an
error and keeps its plugin id. The code never had this check.

A shared prefix does no harm at runtime while the key names differ. When two loaded plugins declare the same key
identity, the registry already refuses the second key and logs it. Two cases remain:

- A plugin that is not loaded leaves its values in the store. Another plugin with the same prefix and key name then
  reads a value with a wrong type or a wrong meaning. The registry cannot see it.
- A tool that starts from the stored data cannot map a prefix to one plugin, for example a schema lookup for a
  missing plugin.

A runtime check sees only the plugins of one process, so it cannot fix either case.

## Decision

**The runtime accepts the same prefix from several plugins and reports nothing. The marketplace keeps a prefix to
one vendor.** A vendor may use one prefix in several of its own plugins. The vendor keeps its own key names apart.

## Consequences

- Each plugin uses the prefix it declares, even when another plugin declares it too.
- A frontend and a backend plugin of one feature share a prefix with no special rule.
- The marketplace check is not built yet.
- A plugin from a custom repository or a local disk is never checked. Its vendor handles its collisions.
- The resulting behavior is in the [setting keys spec](../spec/setting-keys.spec.md).

## Rejected alternatives

### The first declaration wins in a process

This is the rule of
[ADR 0111](./0111-the-registry-takes-keys-only-from-their-own-plugin-and-a-prefix-belongs-to-one-plugin.md). It
makes the identity of a key depend on the plugin load order. It also misses a plugin that is not loaded, which is
the case that does harm.
