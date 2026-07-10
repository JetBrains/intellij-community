# intellij.gradle.tests.benchmark.main

The `intellij.gradle.tests.benchmark.main` is a meta-module that aggregates all benchmark (performance) tests from the Gradle plugin into a single test group so they can be executed as one TeamCity configuration.

It should not contain any test sources of its own — only module dependencies on `intellij.gradle.*.tests.benchmark` containing the tests.

All the benchmark tests should have a package matching the test group pattern: `com.intellij.gradle.*tests.benchmark.*` (see [testGroups.properties](../tests.benchmark.main/testResources/tests/testGroups.properties)), for being executed within the existing TeamCity configuration.

## Examples of existing benchmark modules

| Module                                              | Location                                                                     |
|-----------------------------------------------------|------------------------------------------------------------------------------|
| `intellij.gradle.tests.benchmark`                   | [../tests.benchmark](../tests.benchmark)                                     |
| `intellij.gradle.java.tests.benchmark`              | [../java/tests.benchmark](../java/tests.benchmark)                           |
| `intellij.gradle.completion.kotlin.tests.benchmark` | [../completion/kotlin/tests.benchmark](../completion/kotlin/tests.benchmark) |

## How to define a new benchmark tests module

### 1. Create the module directory

Next to the production sources of the area, create a `tests.benchmark/` directory. Use the same layout as the existing benchmark modules, e.g.:

```
community/plugins/gradle/<area>/tests.benchmark/
├── intellij.gradle.<area>.tests.benchmark.iml
└── test/
    └── <benchmark test>.kt
```

Rules:
- The module name must be `intellij.gradle.<area>.tests.benchmark` (a trailing `tests.benchmark` is required for the test group pattern to match).
- Put the `.iml` file directly inside the module directory.
- Test sources go into a single `test/` source root.

### 2. Configure the `.iml`

The `.iml` must:
- Declare `test/` as a test source root with a `packagePrefix` that ends with `tests.benchmark`, so classes get the fully qualified prefix `com.intellij.gradle.<area>.tests.benchmark.*` and match the pattern in [testGroups.properties](../tests.benchmark.main/testResources/tests/testGroups.properties).
- Depend on `intellij.tools.ide.metrics.benchmark` — the framework used to write benchmark tests.
- Depend on the corresponding modules needed by the tests.

Use [../tests.benchmark/intellij.gradle.tests.benchmark.iml](../tests.benchmark/intellij.gradle.tests.benchmark.iml) as a reference template.

### 3. Do all the common actions related to creating a new module  

Such as mentioning the iml file in `.idea/modules.xml` and `community/.idea/modules.xml`, generating a BAZEL file for it, etc.

### 4. Wire the new module into the aggregator module

Add a dependency on the new module to [`intellij.gradle.tests.benchmark.main.iml`](../tests.benchmark.main/intellij.gradle.tests.benchmark.main.iml):

```xml
<orderEntry type="module" module-name="intellij.gradle.<area>.tests.benchmark" scope="TEST" />
```

No changes to [testGroups.properties](../tests.benchmark.main/testResources/tests/testGroups.properties) are required — as long as the module's package prefix ends with `tests.benchmark`, its tests are automatically picked up by the `[GRADLE_BENCHMARK_TESTS]` group.

### 5. Write the benchmark tests

Use the `intellij.tools.ide.metrics.benchmark` framework (see existing tests such as [GradleJavaSyncPerformanceTest](../java/tests.benchmark/test/importing/GradleJavaSyncPerformanceTest.kt), [KotlinGradleDependenciesCompletionPerformanceTest](../completion/kotlin/tests.benchmark/test/KotlinGradleDependenciesCompletionPerformanceTest.kt) for reference). 

### 6. Make sure the module structure is correct
Before pushing the changes, it's worth running the tests that usually might fail on Dry Run after changes in the module structure:
- [ModuleDependenciesInIntellijProjectTest](../../../../tests/ideaProjectStructure/testSrc/com/intellij/ideaProjectStructure/fast/ModuleDependenciesInIntellijProjectTest.kt)
- [CommunityProjectConsistencyTest](../../../../tests/ideaProjectStructure/testSrc/com/intellij/ideaProjectStructure/fast/CommunityProjectConsistencyTest.kt)
- [UltimateProjectTestsStructureTest](../../../../tests/ideaProjectStructure/testSrc/com/intellij/ideaProjectStructure/slow/UltimateProjectTestsStructureTest.kt)
- [IdeaUltimatePackagingTest](../../../../.idea/runConfigurations/IdeaUltimatePackagingTest.xml)
- [IdeaUltimateProjectStructureTest](../../../../tests/ideaProjectStructure/testSrc/com/intellij/ideaProjectStructure/fast/IdeaUltimateProjectStructureTest.kt)
- [IntelliJConfigurationFilesFormatTest](../../../../tests/ideaProjectStructure/testSrc/com/intellij/ideaProjectStructure/slow/IntelliJConfigurationFilesFormatTest.kt)

### 7. TeamCity

All benchmark tests are executed by the single `[GRADLE_BENCHMARK_TESTS]` TeamCity configuration that runs this aggregator module — no new TeamCity configuration is required for a new `*.tests.benchmark` module.
If a completely separate benchmark suite is needed, create a new test group in [testGroups.properties](../tests.benchmark.main/testResources/tests/testGroups.properties) and a new TeamCity configuration for it in the [ultimate-teamcity-config](https://code.jetbrains.team/p/ij/repositories/ultimate-teamcity-config/files/master/README.md) repository.
