# 0153. All v2 types and the key classes use the plural `Settings` name

Date: 2026-10-13. Merged: 2026-10-15.

## Status

Accepted ([IJPL-228928](https://youtrack.jetbrains.com/issue/IJPL-228928)).

## Context

The design doc uses plural names: `SettingsKey`, `SettingsContext`, `SettingsValue`. The v1 module uses singular
ones: `SettingDescriptor`, `SettingSerializerDescriptor`.

A key names one setting, so `SettingsKey("...")` can read wrong at a call site. The API goes to master, and other
teams migrate to it. After that, each rename breaks their code, so the names must be final before the merge.

## Decision

**The v2 API uses the plural `Settings*` names. The key classes are `SettingsKey` and `SettingsMapKey` too.**

- One prefix for one family. The key sits with `SettingsContext`, `SettingsVault` and the other v2 types. A search
  or a completion for `Settings` finds the whole API.
- A map key holds the values of many settings. So the plural is the correct name for `SettingsMapKey`, and
  `SettingMapKey` reads wrong.
- In English, "settings" before a noun is a usual form: a settings file, a settings dialog. `SettingsKey` reads
  as "a key into the settings".

## Consequences

- v2 reads as a distinct surface, and its names cannot collide with the v1 singular `Setting*` types.
- No rename is necessary. The adopters get the final names from the start.
- `SettingsKey("...")` names one setting with a plural word. We accept this for the sake of one prefix.
- The v1 API keeps its singular names, such as `SettingDescriptor`. The two APIs use different forms.
- The resulting behavior is in the [setting keys spec](../spec/setting-keys.spec.md).

## Rejected alternatives

### `SettingKey` and `SettingMapKey`

A key names one setting, so the singular is more exact for the plain key. But the map key holds many settings,
and the family gets two prefixes.

### `SettingKey` for the plain key only

The plain key takes the exact name, and the map key stays `SettingsMapKey`. The two key classes then have
different prefixes. This is the worst split.

## Earlier decisions

### 0037, 2026-07-11: the v2 types are named `Settings*`

The v2 API used the plural `Settings*` names of the design doc, so v2 read as a distinct surface next to the v1
singular `Setting*` types. It called the plural names a naming preference, not a technical constraint, which may
change with house style. The current decision made the names final, because a rename after the merge to master
breaks the adopters.

### 0070, 2026-08-03: the key class is `SettingKey`

The key classes were to be renamed to `SettingKey` and `SettingMapKey`, because a key identifies one setting and
`SettingsKey("...")` read wrong at call sites. The container and system types stayed plural. It was agreed with
Daniil Ovchinnikov, and the IDE rename was to be done when picked up. The rename was never done. The current
decision reverted it before the merge to master.
