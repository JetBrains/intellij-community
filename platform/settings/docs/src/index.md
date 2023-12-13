A Settings Controller is a service that encapsulates both the persistence and calculation of individual values. Each setting is represented by a distinct [key](setting-descriptor.md).

The Settings Controller returns a calculated value corresponding to a given key. A "calculated" value means that the actual value is computed based on the setting value derived from all participating sources.

```d2
settingsController: SettingsController 

client: Client

client -> settingsController: getItem
client -> settingsController: setItem

custom: Custom Source

xml: XML files {shape: document}

Database: {shape: stored_data}

settingsController -> xml
settingsController -> Database
settingsController -> custom
```

## Use Cases

```d2
user: User {shape: person}
service: Service {shape: package}

settingsController: SettingsController {
  explanation: |kotlin
    suspend fun <T : Any> getItem(key: SettingDescriptor<T>): T?
    suspend fun <T : Any> setItem(key: SettingDescriptor<T>, value: T?)
  |
}

user -> service: Interacts
service -> settingsController

remoteDev: Remote Dev {
  clientAndHost: Client and Host
  processPerConnection: Process Per Connection

  clientAndHost -- _.settingsController: Sync settings both ways
  processPerConnection -- _.settingsController: Merge settings from local config dir and remote host settings
}

TBE -- settingsController: Enforce settings from a predefined profile
Cloud Env -- settingsController: Forbid some settings (e.g. keychain)
```

## Remote Dev
### Process per Connection
Local Only.
We do not want to clone the configuration directory for each connection. The same configuration directory is reused for all clients.

## Service

A service is an entity that implements specific IDE functionality. The Settings API, replacing the previous `PropertiesComponent`, can be used anywhere a suspend context is available.

Still the term `Service` is chosen specifically, advocating that ideally, all code should be implemented as a service.