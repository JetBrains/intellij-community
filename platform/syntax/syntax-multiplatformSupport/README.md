# Syntax Multiplatform Support 

A glue code used by KMP expects-compiler-plugin to bind an `expect` declaration with its `actual` implementation for JVM/WasmJs targets in JPS projects.

# Usage:

common source root:
```kotlin
fun myFunction(param1: String, param2: String): String = linkToActual()
```
   
jvm source root:
```kotlin
@Actual("myFunction")
fun myFunctionJvm(param1: String, param2: String): String {
  // JVM implementation
}
```

WasmJs source root:
```kotlin
@Actual("myFunction")
fun myFunctionWasmJs(param1: String, param2: String): String {
  // WasmJs implementation
}
```
      
# Registration of expects-compiler-plugin in your module

Your iml file must mention the following fragment in Kotlin facet:
```xml
<facet type="kotlin-language" name="Kotlin">
  <configuration version="5" platform="JVM 1.8" allPlatforms="JVM [1.8]" useProjectSettings="false">
    ...
    <arrayArguments>
      <arrayArg name="pluginClasspaths">
        <args>$MAVEN_REPOSITORY$/jetbrains/fleet/expects-compiler-plugin/<$$$real-version-here$$$>/expects-compiler-plugin-$$$real-version-here$$$.jar</args>
      </arrayArg>
    </arrayArguments>
    ...
  </configuration>
</facet>
```

# Removal notice 

TODO: Eventually, this module should be removed in favor of `fleet.util.mutltiplatform`.
Right now it's impossible because `fleet.util.mutltiplatform` must be compiled with at least Java 10 (to make it compatible with JPMS),
but some IntelliJ modules require Java 1.8.
