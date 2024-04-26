# Setting Types

A [SettingDescriptor](setting-descriptor.md) can contain an arbitrary list of tags, while a `SettingTag` can be defined not only by the IntelliJ Platform but also from other sources. 

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