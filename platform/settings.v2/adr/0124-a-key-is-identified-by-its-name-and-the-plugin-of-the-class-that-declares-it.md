# 0124. A key is identified by its name and the plugin of the class that declares it

Date: 2026-09-30. Merged: 2026-10-15.

## Status

Accepted ([IJPL-228928](https://youtrack.jetbrains.com/issue/IJPL-228928)). Amended by
[ADR 0096](./0096-a-mirrored-key-needs-an-explicit-prefix-or-a-shared-module.md).

## Context

Today a setting has no addressable identity: it is a field inside a `PersistentStateComponent` bean. The new
model reads and writes through keys, so a key needs an identity that is unique across the platform and all
plugins. Consultations (@nik) asked for the plugin id to be implicit in the key name, to prevent clashes and
to show ownership. Per-key XML registration would be heavy.

In the discussion with Yuri Artamonov, the plugin id did not look like the best namespace. An author may want to
declare the prefix, for example in the plugin XML. Remote dev needs one identity for a two-plugin feature
([ADR 0096](./0096-a-mirrored-key-needs-an-explicit-prefix-or-a-shared-module.md)). The platform's own namespace
names a vendor, so two plugins of one vendor would collide on a namespace prefix.

The owner of a key must come from the code that declares it. The platform also declares keys in the settings
package, for example the language key and the team editing key. A static initializer runs at the first use of its
class, so a plugin class can be the first frame that touches a platform key.

## Decision

**A key is identified by its string name plus the plugin that owns it. The owner is the plugin of the class that
declares the key. The prefix comes from the declaration that the owner makes in XML, else from the owner plugin
id. A second key with the same owner and name is a runtime error at its construction.**

- `SettingsKey` is a platform class that plugins instantiate and do not extend. It is `@NonExtendable`, and its
  only subclass is `SettingsMapKey`.
- On disk, a key appears as `prefix.name`. A key that a platform class declares has no owner and keeps its bare
  name.
- The declaring class is the first stack frame that does not build the key. Only these frames build a key:
  - a constructor of `SettingsKey` or of a subclass;
  - a function of the file that holds the key factory functions.

  The package of a frame has no effect. The plugin class loader of the declaring class knows its plugin.
- There is no third prefix source, such as the plugin namespace.
- A test declares an owner through a test-only hook. No call site passes a plugin id.

## Consequences

- No per-key registration, and a plugin cannot define a key it does not own.
- Two plugins can use the same short name without a clash.
- A typo or copy-paste duplicate fails early, at the declaration.
- A plugin can keep the on-disk names of its keys stable across a plugin id change, through the XML declaration.
- The prefix no longer tells the owner by itself.
- A key that a platform class declares has no owner, also when a plugin class is the first to use the key.
- A plugin class in the settings package owns the key it declares.
- The static initializer of a `SettingsKey` subclass can declare a key. It is not a constructor, so it is the
  declaring class.
- A new factory function outside that file must be added to the frames that build a key.
- The prefix rules change on-disk key names, so they land before more settings move onto the engine.
- The resulting behavior is in the [setting keys spec](../spec/setting-keys.spec.md).

## Rejected alternatives

### Per-key XML registration

Explicit, but heavy for thousands of keys, and it separates the key from its declaration.

### Skip every frame of a `SettingsKey` subclass

A static initializer of a subclass can declare a key. Its frame would be skipped, and the class that first used the
subclass would become the owner. This is the same fault as the package rule.

## Earlier decisions

### 0007, 2026-05-27: a key's identity is its name plus its owning plugin

A key was identified by its string name plus the plugin that owned it. Ownership was inferred from the module that
instantiated the key, and on disk a key appeared with its plugin id as a prefix. Inferring the owner at run time
needed a reliable call-site-to-plugin mapping. The later decisions below changed the source of the owner and of the
prefix.

### 0035, 2026-07-11: the prototype takes an explicit plugin id

Walking the stack to the caller class and resolving its plugin looked brittle, and the first prototype settings,
block caret and code style, were core settings. So a key took an explicit, optional `pluginId` that defaulted to a
core platform owner. Ownership stayed in the model, so the collision check could key by `(owner, name)`. A plugin
could claim another plugin's namespace until inference landed. 0108 replaced it with the inference from the
declaring class.

### 0068, 2026-07-30: the prefix comes from a declaration, then the namespace, then the plugin id

The prefix had three sources: an explicit declaration, else the plugin namespace, else the plugin id. The owner was
still detected at the call site where possible. 0108 dropped the namespace source, because a namespace names a
vendor, so two plugins of one vendor would collide on it.

### 0108, 2026-09-24: the owner is the first frame outside the settings package

The prefix came from the XML declaration or the plugin id, and the owner from the first stack frame outside the
settings package. The current decision replaced the package rule, because a platform key got a plugin owner when
a plugin class used it first.
