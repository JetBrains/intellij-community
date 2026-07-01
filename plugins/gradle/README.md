# Gradle plugin 
## Tests
### Test aggregating modules
To run Gradle tests on TeamCity, they are aggregated into "meta-modules" that only declare module dependencies and a `testGroups.properties` file:

| Aggregator module                                                                                     | Purpose                                                 | Test groups file                                                                                             | Readme                                                           |
|-------------------------------------------------------------------------------------------------------|---------------------------------------------------------|--------------------------------------------------------------------------------------------------------------|------------------------------------------------------------------|
| [intellij.gradle.tests.main](intellij.gradle.tests.main.iml)                                          | Regular (non-K2) Gradle tests.                          | [testGroups.properties](testResources/tests/testGroups.properties)                                           | –                                                                |
| [intellij.gradle.kotlin.tests.main](kotlin/intellij.gradle.kotlin.tests.main.iml)                     | K2-dependent Gradle tests.                              | [kotlin/testGroups.properties](kotlin/testResources/tests/testGroups.properties)                             | [kotlin/README.md](kotlin/README.md)                             |
| [intellij.gradle.tests.benchmark.main](tests.benchmark.main/intellij.gradle.tests.benchmark.main.iml) | All benchmark (performance) tests of the Gradle plugin. | [tests.benchmark.main/testGroups.properties](tests.benchmark.main/testResources/tests/testGroups.properties) | [tests.benchmark.main/README.md](tests.benchmark.main/README.md) |

### Where to put new tests?
#### Putting tests into a new module
Whenever possible, consider creating separate modules for different kinds of tests:
- intellij.gradle.\<area\>.**tests.integration** - for integration tests (the most part of tests in Gradle plugin)
- intellij.gradle.\<area\>.**tests.unit** - for real lightweight unit tests that don't call Gradle sync or run application, for example.
- intellij.gradle.\<area\>.**tests.benchmark** - for benchmark tests. Currently, all existing benchmark tests modules in the Gradle plugin follow this pattern. Follow the [step-by-step guide](tests.benchmark.main/README.md) for creating a new one if needed.

Advantages of this naming:
- Less unnecessary dependencies: e.g., unit tests don't have integration tests dependencies.
- Instead of messy test group definitions like we currently have in [testGroups.properties](testResources/tests/testGroups.properties) for `intellij.gradle.tests.main`, we could have simple ones like:
  ```properties
  # is not specified in any testGroups.properties yet
  [GRADLE_UNIT_TESTS]
  com.intellij.gradle.*tests.unit.*
  
  # is not specified in any testGroups.properties yet
  [GRADLE_INTEGRATION_TESTS]
  com.intellij.gradle.*tests.integration.*
  
  # tests.benchmark.main/testResources/tests/testGroups.properties
  [GRADLE_BENCHMARK_TESTS]
  com.intellij.gradle.*tests.benchmark*
  ```
- As a consequence, adding a new test anywhere most probably ensures that it will be picked up by a test group related to the test kind without changing the testGroups file. We only need to make sure that a related aggregating *.main module depends on the module containing the test.

#### Putting tests into the existing (old) module structure
If due to any reasons, new integration / unit tests suit better an existing `intellij.gradle.*.tests` module, make sure they are executable on TeamCity. For that, check if a fully qualified name of the test matches a test group for a desired TeamCity configuration. See [How to find a proper testGroups file for tests?](#how-to-find-a-test-group-definition-for-the-existing-teamcity-configuration)

### How to find a test group definition for the existing TeamCity Configuration?
- Find the desired TeamCity configuration in the [ultimate-teamcity-config](https://code.jetbrains.team/p/ij/repositories/ultimate-teamcity-config/files/master/README.md) repository by its name. It has the test group and the main module specified in `groups()`. For example:
  ```
  groups("GRADLE_KTS_CODE_INSIGHT_INTEGRATION_TESTS", GRADLE_KOTLIN_TESTS_MAIN_MODULE)
  ```
- In the ultimate repository, find the related `testGroups.properties` file. You can search by the known test group name (e.g., `GRADLE_KTS_CODE_INSIGHT_INTEGRATION_TESTS`) or explore the files listed in [Tests aggregating modules](#test-aggregating-modules)

In the similar way, you can determine in what configuration the test is executed:
- Find the test group by the test's package name in the ultimate repository.
- Find the configuration by the test group name in [ultimate-teamcity-config](https://code.jetbrains.team/p/ij/repositories/ultimate-teamcity-config/files/master/README.md) repository.