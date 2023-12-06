# Kotlin Multiplatform Tests Cookbook

> This document provides short Q/A-like recipes for common requests.   
> It's an explicit non-goal to give a comprehensive overview of the tests 
> infrastructure, as well as answer advanced requests. For that, consult the KDocs in code
>


### Which tests to run after changes

Most of relevant test runners can be discovered by inspecting the subtypes of `AbstractKotlinMppGradleImportingTest`. 
How to choose from them:

1. 🔴**Absolutely required minimum**. Re-run all Tier-0 and Tier-1 tests. They check only very-very core cases, no advanced 
  configurations or corner-cases. If they fail, it's usually a **critical**.
   * `KotlinMppTierOneCasesImportingTests`
   * `KotlinMppTierZeroCasesImportingTests`

2. 🔴 **Recommended minimum**. Run other tiers. They contain important cases too, and failures there often critical as well.
To give you some intuition, most of structures of the JB-projects (like Ktor, Space, Coroutines) are not in Tier-0 and Tier-1. 
   * `KotlinMppTierTwoCasesImportingTests` 

3. 🟡 **Moderately thorough check**. Run Misc. Tests (`KotlinMppMiscCasesImportingTests`)

4. 🟢 **Thorough check**.  
   * check tests on specific functionality (`multiplatform/core/features`), run them if you believe they are relevant to your
   changes
   * Regress: `KotlinMppRegressionTests`  

Of course, you can run all inheritors of `AbstractKotlinMppGradleImportingTest` or the whole package 
`org.jetbrains.kotlin.gradle.idea.importing.multiplatformTests`

> 💡 Pro-tip. First tests in the test run are quite slow (dozens of seconds), but further are very quick (1-2s). So at some point running 
> all tests actually **saves** time as opposed to running, say, half of the suites 

### How to handle different behavior in different versions of KGP/AGP/Gradle?

Tests infrastructure provides a handful of so-called "test-classifiers", that allow to provide a test-data specific
for some environment. Main ones are:

* KGP Version classifiers, in format of KGP version, e.g. `1.7.10`, `1.8.0`

* Gradle Version classifiers, in format of respective Gradle version without all stability modifiers. 
  E.g. `7.5.1` for Gradle 7.5.1; `8.0` for Gradle 8.0-rc-5

* AGP Version classifiers, in format of "agp" followed by respective AGP version without all stability modifiers.  
  E.g. `agp7.2.1` for AGP 7.2.1; `agp8.0` for AGP 8.0-alpha-7

Adding dash-separated classifiers to the respective testdata file will make this testdata specific for that specific version.
If classifier for some version omitted, then it's assumed that it works for any version of that component. 

Examples:
* `dependencies.txt` is shared across all versions of everything
* `dependencies-7.5.1.txt` will be used in tests with Gradle = 7.5.1 and any KGP/AGP versions
* `dependencies-1.8.0-7.5.1.txt` will be used in tests with Gradle = 7.5.1, KGP = 1.8.0 and any AGP
* `dependencies-1.8.0-7.5.1-agp7.2.1.txt` will be used only in one parametrization: KGP = 1.8.0, Gradle = 7.5.1, AGP = 7.2.1

Classifiers are **ordered**, so `dependencies-1.8.0-7.5.1.txt` works, but `dependencies-7.5.1-1.8.0.txt` doesn't. 
Refer to `community/plugins/kotlin/gradle/gradle-java/tests/test/org/jetbrains/kotlin/gradle/multiplatformTests/workspace/findTestdata.kt`
for the specific order.

> ⚠️Never introduce a version-specific testdata for the latest (bootstrap, usually) KGP. Instead, introduce testdata for previous versions,
> even if that leads to creation of more files. This way, as the support for older versions is dropped, eventually
> test will return to only one non-suffixed testdata file

### How to change the versions of KGP/AGP/Gradle used in local test runs

Two options:

1. **👍 Most popular way**. Tweak default values of `overrideAgpVersion` in `DevTweaksImpl`
```kotlin
// ...
class DevModeTweaksImpl : DevModeTweaks {
    override var overrideGradleVersion = GradleVersionTestsProperty.Value.ForAlphaAgp.version
        get() = field.checkNotOverriddenOnTeamcity()
// ...

```


2. Locally for one test: use `dev`-block in `doTest { ... }`, it provides properties like `overrideAgpVersion`:
```kotlin
@Test
fun testFoo() {
  doTest {
    dev { 
      overrideAgpVersion = AndroidGradlePluginVersionTestsProperty.Value.Alpha.version
    }
  }
}
```

### Which test tweaks/test options exist?

All those "tweaks" or "options" are called `TestFeature` and inherit that interface. If they can be configured, they
will provide a DSL, accessible in `doTest { ... }`-block like that:

```kotlin
fun testFoo() {
  doTest {
    someFeatureOption = "value"
  }
}
```

Enumerating them here is not feasible, so for the comprehensive overview refer to sources in 
`org.jetbrains.kotlin.gradle.newTests.testFeatures`-package. 

To get familiar with the tests infra, we recommend to spend 5 minutes and take a look into:
* `DevTweaks` - provides `dev`-block with various nice things for local test development/debugging
* `WorkspaceChecksDsl` - general configuration of workspace checks runs
* Subclasses of `AbstractTestChecker`, to see which checks are available
  

### How to create a new test suite

Inherit `AbstractKotlinMppGradleImportingTest` and refer to its documentation

### How to debug Gradle daemon

Launch daemon with suspend-argument:

```kotlin
doTest {
  dev {
      enableGradleDebugWithSuspend()
  }
}
```
Attach with usual `Remote JVM Debug`-Run configuration in IDEA

### How to disable/enable some checks in my test

Enable only some checkers and disable everything else*:
```kotlin
doTest {
  onlyCheckers(OrderEntriesChecker, KotlinFacetSettingsChecker)
}
```

Disable some specific checker(s):
```kotlin
doTest {
  disableCheckers(ContentRootsChecker)
}
```

> ⚠️ Highlighting checker won't be disabled by `onlyCheckers` and needs to be disabled explicitly via `disableCheckers`.   
> Motivation: if you have sources, you **should** want to highlight them.

### How to filter testdata a.k.a. "Testdata output is too huge!" 

1. Filter irrelevant modules by using `onlyModules`/`excludeModules` 
2. Most checkers provide their own similar filtering methods. E.g. for dependencies, you can use `onlyDependencies`/`excludeDependencies`
3. Take a look at `hideStdlib` and `hideKotlinNativeDistributon`
4. If you want to hide/filter some diagnostics/errors/warnings in code, use `hideLineMarkers` or `hideHighlightsBelow`

### How to use versions/test properties

Versions and some other code snippets are not stored directly in the testdata, instead "macro"-like patterns are used:
```
org.jetbrains.kotlin:kotlin-stdlib-common:{{KGP_VERSION}} (TEST)
```

There are three built-in properties:
- `{{kgp_version}}`
- `{{agp_version}}`
- `{{gradle_version}}`

All the rest are declared in `SimpleProperties.kt`. If you want to declare a new property, add it there.

### How to have a custom test folder for the test

Annotate test method with `@TestMetadata("$pathToFolderRelativeToTestRoot")`

### How to run two (or more) tests for one testdata

Example: you have some special feature-flag, and you want to run two tests on the same testdata: one with the feature enabled, another one 
with the feature disabled.

Use `@TestMetadata` to change the folder of the second tests, and use the `testClassifier` to disambiguate testdata files

```kotlin
@TestMetadata("foo")
fun `testFoo-featureDisabled`() {
  doTest {
    testClassifier = "disabled"
  }
}

@TestMetadata("foo")
fun `testFoo-featureEnabled`() {
    doTest {
        testClassifier = "enabled"
    }
}
```
It will produce:
```
<root>
 |
 - foo
   |
   - ...
   - dependencies-disabled.txt
   - dependencies-enabled.txt
   - ...
```
