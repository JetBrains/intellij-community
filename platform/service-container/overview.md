This document is a more like a draft, that will be finally moved to [IntelliJ Platform SDK](https://www.jetbrains.org/intellij/sdk/docs/welcome.html)

## Service

Please see [Service](https://plugins.jetbrains.com/docs/intellij/plugin-services.html).

To replace (mock) service in tests, use [ServiceContainerUtil](https://github.com/JetBrains/intellij-community/blob/master/platform/testFramework/src/com/intellij/testFramework/ServiceContainerUtil.kt).

## Preloading Activity

An activity to be executed in background on startup (regardless if some project was opened or not).

See [PreloadingActivity](https://github.com/JetBrains/intellij-community/blob/master/platform/platform-impl/src/com/intellij/openapi/application/PreloadingActivity.java).

To register:

```xml
<extensions defaultExtensionNs="com.intellij">
  <preloadingActivity implementation="com.example.CatPreloadingActivity"/>
</extensions>
```