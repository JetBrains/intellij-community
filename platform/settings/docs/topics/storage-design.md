# Storage Design

Please note these are technical notes, not end user documentation.

## StateStorageBackedByController

`StateStorageBackedByController` is a special implementation of `StateStorage`. 
It's another PSC bridge implementation and currently, it's only used for cache (`StoragePathMacros.CACHE_FILE`).

You might wonder why it's not used for [internal](setting-types.md) settings. 

* It lacks numerous features like support for `StreamProvider` (settings sync, settings import). While we could potentially overcome this, the return on investment isn't evident for now.
* Regarding backward compatibility — would it be appropriate to transfer internal settings from the old storage to a new one upon opening the new IDE version?

So, how does it work? It utilizes the same [unified format](bridge-to-old-api.md#unified-format) and also employs `JsonElementSettingSerializerDescriptor` as the setting key serializator. 
Although the implementation shares some similarities with the PSC bridge, it's not completely identical. As `StateStorageBackedByController` is an exclusive storage, we don't need to handle local data (e.g., merging).

Currently, `LocalSettingsController` only supports keys with `CacheTag` and `NonShareableInternalTag` tags. 
It uses [MVStore](https://www.h2database.com/html/mvstore.html) to store data on the disk. Specifically, we use `MVMap<String, ByteArray>` — where the setting key is transformed into a string (plugin id + setting key). Any type of value is serialized into CBOR. This allows us to deserialize the value later without knowing the data type - whether it's a string, number, etc. As of now, this feature is utilized by `DumpDevIdeaCacheDb` to dump the MVStore database into YAML.

For `JsonElementSettingSerializerDescriptor`, where the serializer cannot be used for any format beyond JSON, we encode the JSON element into a string, and subsequently encode it to CBOR. So, the value gets stored as a CBOR string.