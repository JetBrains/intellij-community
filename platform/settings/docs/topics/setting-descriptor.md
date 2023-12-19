# Setting Descriptor

`SettingDescriptor` has two required properties:

 * key
 * pluginId

and two optional ones:

 * tags (`SettingTag`)
 * serializer (`SettingSerializerDescriptor`)

The key provided is not the final effective key. The plugin ID is automatically and implicitly prepended to it. 
The concept of a component name does not exist. 

There is no need for you to use a special group for your plugin settings. The implicitly prepended plugin ID forms this implicit group.

[//]: # (explain how to create settings descriptor)

## Serializer

The serializer merely serves as a descriptor for what to serialize, it doesn't implement the serialization itself. 
The Settings Controller will determine the appropriate serialization format based on the setting tags.

* [Kotlin serialization](https://github.com/Kotlin/kotlinx.serialization/blob/master/docs/serialization-guide.md) is utilized. 
  The setting values class must be annotated with `kotlinx.serialization.Serializable`. 
* You should always specify default values.

## Mutability
Mutability — we cannot enforce read-write on compile time.
Mutability is not a part of option descriptor, but defined by a controller, so, changed in runtime. 
For example, TBE implementation of settings controller will forbid some settings, and an error will be thrown on set.

We want to throw an error on the set for transparent behavior. On a Settings UI page, such an error may be caught on the platform level.