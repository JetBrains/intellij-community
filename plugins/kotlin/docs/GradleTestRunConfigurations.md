---

This writing is an attempt to accumulate knowledge.\
Should you have anything to add or correct, feel free to do it. It's highly appreciated.

---
# Table of Contents
1. [Abstract](#abstract)
2. [Project import](#project-import)
   1. [Gradle](#gradle)
   2. [IDEA](#idea)
3. [What happens when test file is opened](#what-happens-when-test-file-is-opened)
   1. [Line markers for editor gutter](#line-markers-for-editor-gutter)
   2. [Run configurations](#run-configurations)
4. [What happens when a test is launched](#what-happens-when-a-test-is-launched)
5. [How tests runner tab works](#how-test-runner-tab-works)
6. [Running test batches](#running-test-batches)
7. [Tests to take into account](#tests-to-take-into-account)
8. [Uncategorized knowledge](#uncategorized-knowledge)


<div id='abstract'></div>

# Abstract 
This article is a brief overview of Gradle Test Run Configurations. The idea is to give a big picture of how things work and help developers
and QA-s to better navigate the area.

<div id='project-import'></div>

# Project import
The first fundamental step on the way to launching tests in IDEA is project import. The fact is that the phase of import is responsible for
building project model including the part relevant for tests. Here it's important to note that it deals with a two side process.
Gradle builds a project model which is later imported on IDEA side.

<div id='gradle'></div>

## Gradle
Gradle is aware of Kotlin specifics thanks to
[Kotlin Gradle Plugin](https://github.com/JetBrains/kotlin/tree/master/libraries/tools/kotlin-gradle-plugin).

Project model represents a tree-like serializable data structure. Nodes are instances of  
`com.intellij.openapi.externalSystem.model.DataNode`. Class source code resides in `intellij` repository and
[Kotlin Gradle Plugin](https://github.com/JetBrains/kotlin/tree/master/libraries/tools/kotlin-gradle-plugin) has a binary dependency on
enclosing jar-file. Gradle's model is pretty abstract. After being transferred to IDEA it's parsed by a chain of components each aware
of its subtree only.

### How to debug Kotlin-Gradle-Plugin
To enable debug mode, place `org.gradle.debug=true` in `gradle.properties` of your project.
After that Gradle will wait for an incoming connection on `localhost:5005`.

#### Where to start
There is a set of s.c. ModelBuilders, inheritors of `org.jetbrains.plugins.gradle.tooling.ModelBuilderService`. Among points of interest 
could be e.g. `KotlinMPPGradleModelBuilder.buildAll` and `KotlinMPPGradleModelBuilder.buildTestRunTasks`. 

<div id='idea'></div>

## IDEA 
IDEA Gradle related functionality resides in [Gradle IDEA Plugin](https://github.com/JetBrains/intellij-community/tree/master/plugins/gradle).

As it was said before IDEA starts working with a project in the form of `DataNode`-tree - abstract project representation got from Gradle.
`GradleProjectResolver.ProjectConnectionDataNodeFunction.fun` can be considered as an entry point in this regard.

Later `GradleProjectResolver` passes control over extension point to the instances of `GradleProjectResolverExtension`. These are resolvers
aware of some specifics, e.g. Android, Kapt, Commonizer, Quodana, etc.
Resolver-extensions extend `DataNode`-tree adding nodes per project module (`GradleProjectResolverExtension#createModule`). In particular,
they discover and **fill available Gradle run tasks**.

After that `com.intellij.openapi.externalSystem.service.project.manage.ProjectDataService`-s come into play. Among other things they are
responsible for **filling Kotlin-Facets with Gradle run tasks**. Thus, they become discoverable for editor gutter logic.
Start looking a data-services from `ProjectDataService.importData`.

<div id='what-happens-when-test-file-is-opened'></div>

# What happens when test file is opened

<div id='line-markers-for-editor-gutter'></div>

## Line markers for editor gutter  

File opened in editor is analysed and highlighted by `HighlightingPass`-es, set of objects able to tell about the code through colors,
icons, etc. `LineMarkersPass` is the one capable of run-markers painting on editor gutter. To be more precise it does it with the help
of `LineMarkerProvider`-s and `RunLineMarkerProvider` in particular. Responsibility split doesn't stop there because `RunLineMarkerProvider`
delegates to `RunLineMarkerContributor`-s. It's the final point where the border between the platform and plugin passes through. 
`KotlinTestRunLineMarkerContributor` is Kotlin specific entry point in this chain.

Run icon obtaining is platform dependent. See `IdePlatformKindTooling.getTestIcon`.
IDEA tracks tests execution status interacting with Gradle over proprietary protocol. The latter manipulates s.c. test locators
(see `JavaTestLocator`). To generate the one properly target platform is taken into account. Thus, an icon is not just an image but a
container supplied with protocol details.

To assign run marker to a class or method it's necessary to understand whether it's testable. In other words, we need to detect a test
framework and be aware of its conventions. This logic is encapsulated in `KotlinTestFramework` heavily used in
`KotlinTestRunLineMarkerContributor`

### @kotlin.test.Test
In addition to other signs of being a test `KotlinTestRunLineMarkerContributor` considers `@Test` annotation.  
Kotlin [@Test](https://kotlinlang.org/api/latest/kotlin.test/) one is considered along with framework specific ones (JUnit, TestNG, etc.).
The only difference is that it's resolved into concrete framework via typealiases mechanism. Concrete declaration can be added to the module
dependencies during the project import phase. For JVM platforms (jvm, Android) details can be found in
[Kotlin Gradle Plugin](https://github.com/JetBrains/kotlin/tree/master/libraries/tools/kotlin-gradle-plugin) at
`KotlinDependenciesManagement.kt, kotlinTestCapabilityForJvmSourceSet()`.

<div id='run-configurations'></div>

## Run configurations
Run markers don't come alone. When a run marker is pressed a set of Run/Debug options become available. These options are associated with
run configurations. If none of them are found run markers remain just icons without ability to run tests.

### How configurations are built
Process starts at `PreferredProducerFind.getConfigurationsFromContext()` and leads to traversing extensions of type
`RunConfigurationProducer` each offering what it can.

For MPP its `KotlinMultiplatformCommonTestClassGradleConfigurationProducer`.
For MPP an important point participating in this interaction is `MultiplatformTestTasksChooser.listAvailableTasks()`. It takes Gradle 
tasks from `FacetUtilsKt.externalSystemTestRunTasks` for corresponding module.

<div id='what-happens-when-a-test-is-launched'></div>

# What happens when a test is launched

Test execution is initiated at `RemoteExternalSystemTaskManagerImpl.executeTasks()` and then at `GradleTaskManager.executeTasks()`
([Gradle IDEA Plugin](https://github.com/JetBrains/intellij-community/tree/master/plugins/gradle)). Tests themselves are executed in a
separate Gradle JVM. To track execution Gradle-Kotlin-Plugin and IDEA interact via a special protocol.

## Gradle-IDEA interaction

Gradle reports tests execution status sending XML messages to IDEA.

[Gradle Initialization Scripts](https://docs.gradle.org/current/userguide/init_scripts.html) is a mechanism we use to listen to test tasks
execution and reporting their status. See `GradleConstants.INIT_SCRIPT_CMD_OPTION` (Gradle Plugin).
`KotlinTestTasksResolver.enhanceTaskProcessing()` and `JavaGradleProjectResolver.enhanceTaskProcessing()` are the places where init script
is assembled. The first one adds `addKotlinMppTestListener.groovy` and `KotlinMppTestLogger.groovy`, the second - `addTestListener.groovy`
and `IjTestEventLogger`. Loggers are the classes responsible for XML messages generation. 

An important part related to test naming resides in `TCServiceMessagesClient` (class resides in
[Kotlin Gradle Plugin](https://github.com/JetBrains/kotlin/tree/master/libraries/tools/kotlin-gradle-plugin)) 

On IDEA side messages are handled at `GradleTestsExecutionConsoleOutputProcessor.onOutput()`. Here one can find raw XML and
understand tags semantics.

<div id='how-test-runner-tab-works'></div>

# How test runner tab works

Tests runner tab displays execution progress and tests status.

`ExternalSystemUtil.getConsoleManagerFor` is a method used to choose a proper console for Gradle task. The one relevant for tests is
`GradleTestsExecutionConsoleManager`. Its `isApplicableFor` is based on `GradleTasksIndices`. Should it contain the task in question,
user gets desired console, see `findTasks()`. The index is built during the project import phase.\
It happens that instead of the dedicated console users gets a general one displaying Gradle log messages. Usually the reason lies in the
broken index, project re-import resolves the issue.

<div id='#running-test-batches'></div>

# Running test batches

Tests can be launched either individually or in batches (all tests in a class or package).

Classes responsible for preparing corresponding configurations are descendants of `RunConfigurationProducer`. For instance,
`KotlinMultiplatformAllInPackageConfigurationProducer` or `KotlinMultiplatformCommonTestMethodGradleConfigurationProducer`. 

<div id='tests-to-take-into-account'></div>

# Tests to take into account 

Test task names:
- `NewMultiplatformProjectImportingTest`
- `TestGradleConfigurationProducerUtilTest`

Multiplatform test run configurations:
- `GradleMppJvmRunConfigurationProducersTest`
- `GradleMppNoJvmRunConfigurationProducersTest`

Line markers
- `MultiModuleLineMarkerTestGenerated`
- `LineMarkersTestGenerated`
- `LightTestRunLineMarkersTestGenerated`

Android & HMPP
- `AndroidImportAndCheckHighlightingTest`


<div id='uncategorized-knowledge'></div>

# Uncategorized knowledge
Q: For MPP tasks often come as a pair, where the first one has "clean" prefix e.g. (cleanJvmTest, jvmTest). Facet in its turn contains the second
one only. Is the first one generated?\
A: `MultiplatformTestTasksChooser.getTaskNames`

Q: How are Gradle tasks are in IDE?\
A: `ExternalSystemTestRunTask`

Q: Test configurations contain filters, e.g. `--tests Class.testName`. Where are they built?\
A: `GradleExecutionSettingsUtil.createTestFilterFrom`

Q: Where does tests rerun functionality reside?\
A: `GradleRerunFailedTestsAction`

Q: Is there anything special in the first test run?\
A: `AbstractKotlinMultiplatformTestClassGradleConfigurationProducer.onFirstRun`