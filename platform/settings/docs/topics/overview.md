# Overview

A Settings Controller is a service that encapsulates both the persistence and calculation of individual values. Each setting is represented by a distinct [key](setting-descriptor.md).

The Settings Controller returns a calculated value corresponding to a given key. A "calculated" value means that the actual value is computed based on the setting value derived from all participating sources.

```d2
client: Client

scm: Settings Controller Mediator {
  explanation: |md
  `SettingsController`
  |
}
firstSC: First Settings Controller {
  explanation: |md
  `DelegatedSettingsController`
  |
}
secondSC: Nth Settings Controller

xml: XML files {shape: document}
Database: {shape: stored_data}

custom: Custom Source {shape: stored_data}

client -> scm: getItem\nsetItem

scm -> firstSC

firstSC -> custom
scm -> secondSC: Delegate the request\nto the next controller if needed

secondSC -> xml
secondSC -> Database
```
{scale="0.8"}

The order of the extensions defines the sequence in which the controllers are processed. The mediator continues to process these controllers until a result is resolved.

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

To ensure that a network request isn't performed for each setting,
a setting storage blob may initially be prepared and sent to the remote setting controller.
The default IJ Platform local setting controller is designed with that in mind.

### Process per Connection
Local Only.
We do not want to clone the configuration directory for each connection. The same configuration directory is reused for all clients.

## TBE

- Set or get a specific setting (instead of only operating at the container level).
- Access settings without needing to know the implementation details of a container through a key-value API (see `RawSettingSerializerDescriptor`).
- Prevent certain keys from being set by the user (making them read-only settings). See [Mutability](setting-descriptor.md#mutability).
- Intercept the events that signal a setting has been modified.
- Instruct to use the "default" value for a certain key.

## Service

A service is an entity that implements specific IDE functionality. The Settings API, replacing the previous `PropertiesComponent`, can be used anywhere a suspend context is available.

Still, the term `Service` is chosen specifically, advocating that ideally, all code should be implemented as a service.