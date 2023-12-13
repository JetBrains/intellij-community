A `SettingDescriptor` can contain an arbitrary list of tags, while a `SettingTag` can be defined not only by the IntelliJ Platform but also from other sources. 

The implementation of a settings controller can significantly vary. Therefore, tags should be considered more as hints rather than fixed instructions.

The following diagram defines tags used by the `LocalSettingsController`, these tags are provided by the IntelliJ Platform.  

```d2
shareable: Shareable? {shape: hexagon}
configurable: Configurable by a user? {shape: hexagon}
cacheOrInternal: Is it cache? {shape: hexagon}

regular: Regular {
#   shape: stored_data
  explanation: No tags required {shape: text}
}

nonShareable: Non-Shareable {
#   shape: stored_data
  explanation: |md
   Use `NonShareableTag` tag
   
   (old: `RoamingType.DISABLED`)
  |
}

internal: Internal {
#   shape: stored_data
  explanation: |md
   Use `NonShareableInternalTag` tag
   
   (old: `StoragePathMacros.WORKSPACE_FILE`)
  |
}

cache: Cache {
#   shape: stored_data
  explanation: |md
   Use `CacheTag` tag
   
   (old: `StoragePathMacros.CACHE_FILE`)
  |
}

shareable -> regular: yes
shareable -> configurable: no

configurable -> nonShareable: yes
configurable -> cacheOrInternal: no

cacheOrInternal -> cache: yes
cacheOrInternal -> internal: no
```

Please refer to the Javadoc of the corresponding tags in question for more detailed information and implementation notes.

[//]: # (when remote dev will add tags, list it here and explain usage)

## Remote Dev

Remote Dev can introduce tag like "Merge Strategy" — merge to a client or host.

## Settings Sync

Settings Sync can introduce tag "Category" — to implement selective sync.


!!! question "Why term "tag" and not "attribute" is used?"

    * An attribute often has a value, but a tag does not.
    * A tag is typically used to categorize or label items.
    * We don't use a tag with an enum field like `RoamingType`, but rather a simple tag, which is more concise.
    * Multiple tags can be added in any circumstance, so there's no worry that conflicting tags might be specified to the same setting.
