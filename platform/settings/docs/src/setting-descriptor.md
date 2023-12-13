`SettingDescriptor` has two required properties:

 * key
 * pluginId

and two optional ones:

 * tags
 * serializer

The key provided is not the final effective key. The plugin ID is automatically and implicitly prepended to it. 
The concept of a component name does not exist. 

There is no need for you to use a special group for your plugin settings. The implicitly prepended plugin ID forms this implicit group.

[//]: # (explain how to create settings descriptor)

## Mutability
Mutability — we cannot enforce read-write on compile time. Mutability is not a part of option descriptor, but defined by a controller, so, changed in runtime. For example, TBE implementation of settings controller will forbid some settings, and… error will be thrown on set.

We want to throw an error on the set for transparent behavior. On settings UI page, such an error may be caught on the platform level.