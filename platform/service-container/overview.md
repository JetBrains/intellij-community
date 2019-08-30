This document is a more like a draft, that will be finally moved to [IntelliJ Platform SDK](https://www.jetbrains.org/intellij/sdk/docs/welcome.html)

## Service

Please see [Service](https://www.jetbrains.org/intellij/sdk/docs/basics/plugin_structure/plugin_services.html).

To replace (mock) service in tests, use [ServiceContainerUtil](https://github.com/JetBrains/intellij-community/blob/master/platform/testFramework/src/com/intellij/testFramework/ServiceContainerUtil.kt).

### Light Service

Service that is not going to be overridden. No need to register it in a `plugin.xml`.

To register: annotate class using [@Service](https://github.com/JetBrains/intellij-community/blob/master/platform/core-api/src/com/intellij/openapi/components/Service.java) annotation. If service is written in Java and not Kotlin, mark class as `final`.
 
Restrictions:

* constructor injection is not supported (since it is deprecated), but project level service can define constructor that accepts `Project`, and module level `Module`.
* if service it is a [PersistentStateComponent](https://www.jetbrains.org/intellij/sdk/docs/basics/persisting_state_of_components.html), roaming must be disabled (`roamingType` is set to `RoamingType.DISABLED`).
* service class must be `final`.

## Preloading Activity

An activity to be executed in background on startup (regardless is some project opened or not).

See [PreloadingActivity](https://github.com/JetBrains/intellij-community/blob/master/platform/platform-impl/src/com/intellij/openapi/application/PreloadingActivity.java).

To register:

```xml
<extensions defaultExtensionNs="com.intellij">
  <preloadingActivity implementation="com.example.CatPreloadingActivity"/>
</extensions>
```

## Startup Activity

An activity to be executed after project opening.

See [StartupActivity](https://github.com/JetBrains/intellij-community/blob/master/platform/core-api/src/com/intellij/openapi/startup/StartupActivity.java). Do not forget to implement `DumbAware` to indicate that activity can be performed not in EDT and not during indexing.

There are two extension points:

* `postStartupActivity`
* `backgroundPostStartupActivity` (see `BACKGROUND_POST_STARTUP_ACTIVITY` docs).

To register:

```xml
<extensions defaultExtensionNs="com.intellij">
  <postStartupActivity implementation="com.example.CatColoringStartupActivity"/>
</extensions>
```