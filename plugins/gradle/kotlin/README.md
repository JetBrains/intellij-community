# intellij.gradle.kotlin.tests.main
The `intellij.gradle.kotlin.tests.main` is a meta-module to aggregate all the K2-depending tests from various modules of the Gradle plugin into common test groups.

**Probably, this module will be removed in the future, in favor of the [Alternative approach](#alternative-approach).**

### How to execute tests from a new K2-dependent module on TeamCity?
1. Add the new test module dependency to [intellij.gradle.kotlin.tests.main.iml](intellij.gradle.kotlin.tests.main.iml)
2. Add a new tests pattern into a test group in [testGroups.properties](testResources/tests/testGroups.properties).
   - If the new tests suit any existing TeamCity configuration – reuse the existing group.
   - Otherwise, create a new test group and configuration for it in the [ultimate-teamcity-config](https://code.jetbrains.team/p/ij/repositories/ultimate-teamcity-config/files/master/README.md) repository. See other Gradle configurations for a reference in [Idea_Tests_BuildToolsTests.kt](https://code.jetbrains.team/p/ij/repositories/ultimate-teamcity-config/files/master/.teamcity/src/idea/tests/buildTypes/buildTools/Idea_Tests_BuildToolsTests.kt)

### Alternative approach
Probably, instead of defining test groups meta-module, we should define `testGroups.properties` in each K2-dependent module separately.
In this case, we won't be able to combine tests from different modules into a single test group.
As an advantage, tests become more isolated from each other, and their classpath has fewer dependencies.
As a disadvantage, for each new test module we need to create a new TeamCity configuration.

### For tests, not dependent on K2
... consider adding dependency on their module to [intellij.gradle.tests.main.iml](../intellij.gradle.tests.main.iml) 
and including them in a test group in [testGroups.properties](../testResources/tests/testGroups.properties)
