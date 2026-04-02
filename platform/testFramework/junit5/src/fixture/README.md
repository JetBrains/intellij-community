# JUnit 5 Fixture Framework Guide

This guide explains how to use the `com.intellij.testFramework.junit5.fixture` framework when writing new JUnit 5 tests.
It is written for agents and contributors who need practical setup recipes, not just API signatures.

The main sources are:

- public fixture APIs in `community/platform/testFramework/junit5/src/fixture`
- project-structure DSL in `community/platform/testFramework/junit5/projectStructure`
- showcase tests in `community/platform/testFramework/junit5/test/showcase`
- representative tests from Platform, Java, Python, JavaScript, LSP, DevKit, and CLion

## What This Framework Is

`TestFixture<T>` is a lazy handle to a resource. The resource is created by a `testFixture { ... }` initializer and is automatically torn down by the JUnit 5 extension installed by `@TestFixtures` or `@TestApplication`.

The important mental model is:

- Declare fixtures as fields.
- Compose fixtures from other fixtures inside `testFixture { ... }`.
- Inside a fixture initializer, use `otherFixture.init()`, not `otherFixture.get()`.
- In test methods, use `fixture.get()` or `val x by fixture`.
- Dependencies are tracked automatically, so dependents are torn down before their dependencies.

Core API:

- `testFixture(...)`: `community/platform/testFramework/junit5/src/fixture/testFixture.kt`
- `TestFixtureExtension`: `community/platform/testFramework/junit5/src/fixture/TestFixtureExtension.kt`
- common fixtures: `community/platform/testFramework/junit5/src/fixture/fixtures.kt`

## Required Annotations And Field Scope

For most tests, annotate the class with `@TestApplication`.

Why:

- it initializes the shared test application
- it installs `@TestFixtures`
- it enables fixture field initialization and teardown

Representative examples:

- Kotlin: `community/platform/testFramework/junit5/test/showcase/JUnit5ProjectFixtureTest.kt`
- Java: `community/java/java-tests/testSrc/com/siyeh/ig/migration/ForCanBeForeachInspectionTest.java`
- Python wrapper annotation built on top of `@TestApplication`: `community/python/python-test-env/junit5/src/com/intellij/python/junit5Tests/framework/env/PyEnvTestCase.kt`

If a test only needs the fixture extension and does not need the test application, `@TestFixtures` is enough.
Representative usage:

- `community/platform/testFramework/junit5/test/fixture/FixtureContextTest.kt`

Java tests are supported too. Use static imports from `FixturesKt` for top-level fixtures and keep `TestFixture<...>` fields on the test class.

Field scope matters:

- fixture fields in `companion object` or Java `static final` fields are class-level fixtures
- instance fields are recreated per test

Use class-level fixtures when:

- setup is expensive
- the state is intentionally shared between tests in one class

Use instance-level fixtures when:

- each test must start from a clean state
- the fixture mutates state in a way that is hard to reset safely

Representative examples:

- class-level vs instance-level project lifecycle: `community/platform/testFramework/junit5/test/showcase/JUnit5ProjectFixtureTest.kt`
- class-level vs instance-level module lifecycle: `community/platform/testFramework/junit5/test/showcase/JUnit5ModuleFixtureTest.kt`

## The Most Important API Surface

The public setup building blocks live in `community/platform/testFramework/junit5/src/fixture/fixtures.kt`.

The ones you will use most often are:

- `tempPathFixture()`
- `projectFixture(...)`
- `TestFixture<Project>.moduleFixture(...)`
- `TestFixture<Module>.sourceRootFixture(...)`
- `TestFixture<PsiDirectory>.psiFileFixture(...)`
- `TestFixture<PsiFile>.editorFixture()`
- `TestFixture<Project>.pathInProjectFixture(...)`
- `TestFixture<Project>.fileOrDirInProjectFixture(...)`
- `TestFixture<Project>.existingPsiFileFixture(...)`
- `disposableFixture()`
- `extensionPointFixture(...)`
- `registryKeyFixture(...)`
- `replacedServiceFixture(...)`

Higher-level setup APIs built on top of the same framework:

- `multiverseProjectFixture(...)`: `community/platform/testFramework/junit5/projectStructure/src/com/intellij/platform/testFramework/junit5/projectStructure/fixture/ProjectStructureFixtures.kt`
- `codeInsightFixture(...)`: `community/platform/testFramework/junit5/codeInsight/src/fixture/codeInsightFixture.kt`
- `javaCodeInsightFixture(...)`: `community/java/testFramework/src/com/intellij/testFramework/javaCodeInsightFixture.kt`

## Minimal Recipes

### Recipe 1: A Project Container Only

Use this when the test only needs a `Project` instance and does not depend on open-project lifecycle.

```kotlin
@TestApplication
class MyTest {
  private val project = projectFixture()

  @Test
  fun test() {
    val p = project.get()
  }
}
```

This creates a new project on a temp path, but does not open it.

Representative usage:

- `community/platform/platform-tests/testSrc/com/intellij/usages/impl/UsageViewManagerTest.java`

### Recipe 2: Open The Project After Creation

Use `openAfterCreation = true` when the test needs:

- startup activities
- project services that expect an opened project
- editors
- code insight
- indexing
- open-project configurators

```kotlin
private val project = projectFixture(openAfterCreation = true)
```

Representative usages:

- showcase: `community/platform/testFramework/junit5/test/showcase/JUnit5ProjectFixtureTest.kt`
- LSP: `platform/lsp-impl/tests/testSrc/LspServerTest.kt`
- Python: `community/python/testSrc/com/intellij/python/junit5Tests/unit/PyInterpreterInspectionTest.kt`

### Recipe 3: Physical Module Root Equals Source Root

Use this when the module must have a real filesystem root and files live directly under it.
This is common in Python, LSP, YAML, and other file-root-oriented tests.

```kotlin
private val tempDir = tempPathFixture()
private val project = projectFixture(tempDir, openAfterCreation = true)
private val module = project.moduleFixture(tempDir, addPathToSourceRoot = true)
```

Representative usages:

- `community/python/testSrc/com/intellij/python/junit5Tests/env/venv/showCase/PyEnvWithVenvShowCaseTest.kt`
- `platform/lsp-impl/tests/testSrc/LspServerTest.kt`
- `community/java/java-tests/testSrc/com/siyeh/ig/migration/ForCanBeForeachInspectionTest.java`

### Recipe 4: Classic Project -> Module -> Source Root -> File -> Editor Chain

This is the canonical setup chain for file-based tests.

```kotlin
private val project = projectFixture()
private val module = project.moduleFixture("src")
private val sourceRoot = module.sourceRootFixture()
private val file = sourceRoot.psiFileFixture("Test.txt", "hello")
private val editor = file.editorFixture()
```

Representative usages:

- PSI file fixture: `community/platform/testFramework/junit5/test/showcase/JUnit5PsiFileFixtureTest.kt`
- editor fixture: `community/platform/testFramework/junit5/test/showcase/JUnit5EditorFixtureTest.kt`

## How `projectFixture(...)` Behaves

Signature:

```kotlin
fun projectFixture(
  pathFixture: TestFixture<Path> = tempPathFixture(),
  openProjectTask: OpenProjectTask = OpenProjectTask.build(),
  openAfterCreation: Boolean = false,
): TestFixture<Project>
```

Key behavior from implementation:

- if the target path already contains a valid `.idea` project, the fixture opens it instead of creating a new one
- otherwise it creates a new project on the given path
- it disables service preloading to avoid background service loading after disposal
- it waits for `RunManager` to finish loading before exposing the project
- teardown uses `forceCloseProjectAsync(project, save = false)`

Use `projectFixture(pathFixture)` when:

- the test prepares the directory tree before project creation
- the test copies testdata into a temp location
- the project location itself matters

Representative usages:

- existing `.idea` project showcase: `community/platform/testFramework/junit5/test/showcase/JUnit5ProjectFixtureTest.kt`
- copied project tree: `phpstorm/mcp/tests/src/debugger/XDebugProjectTest.kt`
- JS debugger project loader: `plugins/JavaScriptDebugger/testSrc/testFramework/junit5/projectFixtures.kt`

## `OpenProjectTask` Patterns

The default `OpenProjectTask.build()` is fine for simple tests.
Customize it only when the scenario explicitly depends on project-opening semantics.

### Wizard-Created Project

Use:

```kotlin
OpenProjectTask().copy(isProjectCreatedWithWizard = true)
```

Use this when the test must behave like a project freshly created by a wizard, not like a plain opened folder.

Representative usages:

- `community/python/python-pyproject/test/com/intellij/python/junit5Tests/unit/pyproject/PyProjectSyncActivityTest.kt`
- `python/junit5Tests/tests/com/intellij/python/junit5Tests/env/tests/v3/PyV3EmptyProjectGeneratorTest.kt`

### Do Not Auto-Create Default Module

Use:

```kotlin
OpenProjectTask { createModule = false }
```

Use this when the test will set up modules and roots manually after opening the project.

Representative usage:

- `community/plugins/devkit/intellij.devkit.workspaceModel/tests/testSrc/AbstractEntityCodeGenTest.kt`

### Run Configurators During Open

Use:

```kotlin
OpenProjectTask.build().copy(runConfigurators = true)
```

Representative usage:

- `community/platform/lang-impl/testSources/com/intellij/util/indexing/ProjectRootEntityNotIndexedTest.kt`

### Give The Project A Unique Name

Wrappers sometimes derive the name from `TestContext.uniqueId` to avoid collisions.

Representative usage:

- `plugins/JavaScriptDebugger/testSrc/testFramework/junit5/projectFixtures.kt`

## Existing Project Trees

There are two common patterns.

### Pattern A: Build A Minimal `.idea` Tree In Temp Dir

Use this when the test specifically wants to open a real IntelliJ project and the exact `.idea` contents matter.

Representative reference:

- `community/platform/testFramework/junit5/test/showcase/JUnit5ProjectFixtureTest.kt`

That showcase:

- creates a temp root
- writes `.idea/modules.xml`
- writes `.iml`
- writes `.idea/.name`
- passes the resulting path to `projectFixture(...)`

### Pattern B: Copy Testdata Into A Temp Dir, Then Open It

Use this when the project tree already exists in testdata and the test needs a writable copy.

Representative usages:

- `phpstorm/mcp/tests/src/debugger/XDebugProjectTest.kt`
- `plugins/JavaScriptDebugger/testSrc/testFramework/junit5/projectFixtures.kt`

## Copying Test Data

The base fixture framework does not automatically copy test data into temp directories.

Confirmed behavior:

- `tempPathFixture()` only creates and deletes a temp directory
- `projectFixture(...)` only creates or opens a project at the given path
- the only built-in copy mechanism in the public API is `sourceRootFixture(..., blueprintResourcePath = ...)`

Confirmed implementation references:

- temp dir creation: `community/platform/testFramework/junit5/src/fixture/fixtures.kt`
- project creation/opening: `community/platform/testFramework/junit5/src/fixture/fixtures.kt`
- source-root copying via `copyToRecursively(...)`: `community/platform/testFramework/junit5/src/fixture/fixtures.kt`

This means there are two separate recipes.

### Recipe A: Copy A Whole Project Tree, Then Open It

Use this for:

- `.idea` or `.iml` projects
- imported project layouts
- tests where files outside source roots matter

```kotlin
private val projectPath = testFixture {
  val tempDir = tempPathFixture().init()
  val copiedProject = tempDir.resolve("myProject")
  copyDirectory(testDataProjectRoot, copiedProject)
  initialized(copiedProject) { }
}

private val project = projectFixture(projectPath, openAfterCreation = true)
```

Confirmed wrappers using this pattern:

- JavaScript debugger project preparation:
  `plugins/JavaScriptDebugger/testSrc/testFramework/junit5/projectFixtures.kt`
- CLion temp-dir wrapper:
  `CIDR/clion-testFramework-nolang/junit5/core/src/com/intellij/clion/testFramework/nolang/junit5/core/fixtures.kt`

### Recipe B: Copy Only The Source Tree

Use this for:

- tests where project layout is simple
- tests where source root contents come from testdata
- Python- or language-heavy fixtures where the project base dir itself is the source root

```kotlin
private val project = projectFixture(openAfterCreation = true)
private val module = project.moduleFixture("src")
private val sourceRoot = module.sourceRootFixture(
  pathFixture = project.pathInProjectFixture(Path.of("")),
  blueprintResourcePath = testDataPath,
)
```

Confirmed usages:

- Python default bootstrap:
  `community/python/junit5Tests-framework/src/com/intellij/python/junit5Tests/framework/PyDefaultTestApplication.kt`
- Python typing conformance tests:
  `python/junit5Tests/tests/com/intellij/python/junit5Tests/env/tests/PyTypingConformanceTest.kt`

Rule of thumb:

- if you need a real project tree, copy it yourself into a temp dir before calling `projectFixture(...)`
- if you only need source files under a module root, prefer `blueprintResourcePath`

## Where To Put `testData` And How To Resolve It

Keep sample sources and other immutable test inputs in module-local `testData` directories, not in `testSrc`.

Confirmed repository patterns:

- plugin/module-local `testData` next to test sources:
  `plugins/css/tests/testData/...`
- community module-local `testData`:
  `community/java/java-tests/testData/...`
- subsystem-specific resource directories when a wrapper expects them:
  for example Python JUnit5 framework also uses `testResources` in its own module

Representative references:

- `plugins/css/tests/testSource/css/CssRenameTest.java`
- `community/java/java-tests/testSrc/com/siyeh/ig/migration/ForCanBeForeachInspectionTest.java`
- `community/python/junit5Tests-framework/src/com/intellij/python/junit5Tests/framework/PyDefaultTestApplication.kt`

### Generic Rule For New JUnit 5 Fixture Tests

If you are writing a new test around this fixture framework, the safest default is:

- put immutable sample sources under `<module>/testData/<feature-or-test-group>/...`
- refer to them from tests via absolute paths derived from `$PROJECT_ROOT/...`
- copy them into temp project roots or source roots when the test needs writable files

Example layout:

```text
community/my-plugin/tests/testSrc/com/intellij/myPlugin/MyFeatureTest.kt
community/my-plugin/tests/testData/myFeature/simpleProject/...
community/my-plugin/tests/testData/myFeature/singleFile/A.java
```

### Resolving `testData` In Generic JUnit 5 `codeInsightFixture(...)`

For the generic JUnit 5 `codeInsightFixture(...)`, use `@TestDataPath` on the class and `@TestSubPath` on methods when needed.

Important confirmed limitation:

- generic `codeInsightFixture(...)` supports `$PROJECT_ROOT`
- generic `codeInsightFixture(...)` does not support `$CONTENT_ROOT`

Confirmed source:

- `community/platform/testFramework/junit5/codeInsight/src/fixture/codeInsightFixture.kt`

Recommended pattern:

```kotlin
@TestApplication
@TestDataPath("$PROJECT_ROOT/community/my-plugin/tests/testData/myFeature")
class MyFeatureTest {
  private val tempDir = tempPathFixture()
  private val project = projectFixture(tempDir, openAfterCreation = true)
  private val module = project.moduleFixture(tempDir, addPathToSourceRoot = true)
  private val fixture by codeInsightFixture(project, tempDir)

  @Test
  @TestSubPath("simpleProject")
  fun testSomething() {
  }
}
```

Representative usages of `$PROJECT_ROOT` in tests:

- `community/java/java-tests/testSrc/com/siyeh/ig/migration/ForCanBeForeachInspectionTest.java`
- `plugins/css/tests/testSource/css/CssRenameTest.java`

### Resolving `testData` Without `codeInsightFixture(...)`

If the test works directly with `Path`, `PathFixture`, or custom loaders, resolve test data explicitly.

Confirmed patterns:

- `PathManagerEx.getCommunityHomePath()` when the data lives under community-root-relative paths
- `PathManager.getHomeDirFor(TestClass::class.java)` when you want the repository home relative to the test class location

Representative usages:

- community-root-relative path:
  `community/plugins/devkit/intellij.devkit.workspaceModel/tests/testSrc/AbstractEntityCodeGenTest.kt`
- repository home via test class:
  `python/junit5Tests/tests/com/intellij/python/junit5Tests/env/tests/PyTypingConformanceTest.kt`

Typical examples:

```kotlin
val testData = Path.of(
  PathManagerEx.getCommunityHomePath(),
  "java/java-tests/testData/configurationStore"
)
```

```kotlin
val repoHome = PathManager.getHomeDirFor(MyTest::class.java)!!
val testData = repoHome.resolve("community/my-plugin/tests/testData/myFeature")
```

### Wrapper-Specific `@TestDataPath` Rules

Do not assume that all wrappers interpret `@TestDataPath` the same way.

Confirmed example:

- Python JUnit5 framework resolves `$CONTENT_ROOT` in its own metadata layer:
  `community/python/junit5Tests-framework/src/com/intellij/python/junit5Tests/framework/metaInfo/TestClassInfo.kt`

So:

- for generic fixture APIs from `community/platform/testFramework/junit5`, prefer `$PROJECT_ROOT`
- use `$CONTENT_ROOT` only when the subsystem wrapper explicitly documents or implements it

### When To Inline Data And When To Use `testData`

Inline text is fine when:

- the sample is tiny
- the structure is one file
- readability is better than maintaining a separate file

Use `testData` when:

- the sample is multi-file
- the sample tree should resemble a real project
- the file names and directory layout matter
- the same sample should be reused across tests

Use `blueprintResourcePath` or custom copy fixtures when:

- files must remain immutable in the repository
- the test needs to modify them after copying into a temp project

## Module And Root Setup

### `moduleFixture(name, moduleType)`

Use this overload when:

- you need a module object quickly
- physical module location does not matter yet
- you will add roots later

```kotlin
private val module = project.moduleFixture("myModule")
```

Representative usages:

- `community/platform/testFramework/junit5/test/showcase/JUnit5ModuleFixtureTest.kt`
- `community/plugins/mcp-server/tests/testSrc/com/intellij/mcpserver/McpToolsetTestBase.kt`

If a specific module type matters, pass it:

```kotlin
private val pyModule = project.moduleFixture(moduleType = PyNames.PYTHON_MODULE_ID)
```

Representative usages:

- `community/python/testSrc/com/intellij/python/junit5Tests/unit/PyInterpreterInspectionTest.kt`
- `python/junit5Tests/tests/com/intellij/python/junit5Tests/env/tests/PyTypingConformanceTest.kt`

### `moduleFixture(pathFixture, addPathToSourceRoot, moduleTypeId)`

Use this overload when:

- the module needs a real base directory
- the module root should also be a source root
- the module type influences subsystem logic

Representative usages:

- Python module root equals source root:
  `python/junit5Tests/tests/com/intellij/python/junit5Tests/env/tests/PySdkConfiguratorTest.kt`
- Java test in Java syntax:
  `community/java/java-tests/testSrc/com/siyeh/ig/migration/ForCanBeForeachInspectionTest.java`

### `sourceRootFixture(...)`

Use this to create and register a source root inside a module.

Signature summary:

- `isTestSource = false` by default
- `pathFixture` defaults to a fresh temp path
- `blueprintResourcePath` can copy an existing directory tree into the source root

Important notes:

- `sourceRootFixture(...)` is the main public helper for roots
- there is no public `contentRootFixture`
- if you need rich content-root layout, prefer `multiverseProjectFixture`
- `isTestSource = true` exists in the API, but repository usage is rare; when test/resource layout matters, teams more often use the multiverse DSL or local helpers
- `blueprintResourcePath` is the built-in way to copy an existing directory tree into the created source root

Representative usages:

- simple root: `community/platform/testFramework/junit5/test/showcase/JUnit5PsiFileFixtureTest.kt`
- project base dir as source root: `community/python/testSrc/com/intellij/python/junit5Tests/unit/PyInterpreterInspectionTest.kt`
- large testdata tree copied into project root: `python/junit5Tests/tests/com/intellij/python/junit5Tests/env/tests/PyTypingConformanceTest.kt`

### `pathInProjectFixture(...)`

Use this when the path must be under `project.stateStore.projectBasePath`.
This is especially useful for deterministic layout inside the project base directory.

Representative usages:

- Python test bootstrap uses the project base dir itself as the source root:
  `community/python/junit5Tests-framework/src/com/intellij/python/junit5Tests/framework/PyDefaultTestApplication.kt`
- CLion tests resolve concrete project-relative files such as `main.cpp` and `c_cpp_properties.json`:
  `CIDR/clion-openfolder/tests/testSrc/com/intellij/clion/openfolder/projectStatus/CCppPropertiesFixesProviderTest.kt`

### `fileOrDirInProjectFixture(...)` And `existingPsiFileFixture(...)`

Use these lookup fixtures when the file or directory already exists in the project.

Representative usages:

- find VFS objects in a prepared project:
  `community/platform/lang-impl/testSources/com/intellij/psi/impl/file/impl/FileMoveTest.kt`
- find PSI in an existing imported project:
  `CIDR/clion-radler/tests-junit5/testSrc/com/intellij/clion/radler/tests/actions/RadReformatFileTest.kt`

## File, PSI, And Editor Setup

### `virtualFileFixture(...)` And `psiFileFixture(...)`

These helpers create a direct child of the receiver directory.

Use them when:

- one file is enough
- the file belongs directly under the chosen source root

Representative usages:

- `community/platform/testFramework/junit5/test/showcase/JUnit5PsiFileFixtureTest.kt`
- `community/platform/testFramework/junit5/test/showcase/JUnit5EditorFixtureTest.kt`

Limitation:

- the base API does not create nested relative paths in one call

For nested layouts, use one of these instead:

- `blueprintResourcePath`
- `multiverseProjectFixture { dir { file(...) } }`
- manual VFS writes in the test
- subsystem-specific extensions such as Python's nested-path helpers

One consequence of this limitation is that subsystems sometimes add their own nested-path helpers.
Representative example:

- `community/python/junit5Tests-framework/src/com/intellij/python/junit5Tests/framework/fixtures.kt`

### `editorFixture()`

`editorFixture()` opens an editor through `FileEditorManager`, supports `<caret>` markers, and removes editor/history state during teardown.

Representative usages:

- showcase with caret marker handling:
  `community/platform/testFramework/junit5/test/showcase/JUnit5EditorFixtureTest.kt`
- open an existing file in an already prepared project:
  `CIDR/clion-radler/tests-junit5/testSrc/com/intellij/clion/radler/tests/cmake/RadCMakeEditorActionsTest.kt`

### `fileEditorManagerFixture()`

Use this when the test needs the `FileEditorManager` service itself, not just an editor for one file.

Representative usage:

- `community/platform/testFramework/junit5/test/fixture/FileEditorManagerFixtureTest.kt`

## Code Insight Fixtures

`codeInsightFixture(projectFixture, tempDirFixture)` wraps the classic `CodeInsightTestFixture` into the JUnit 5 fixture model.

Important requirement:

- the project must contain at least one module

This is enforced by implementation in:

- `community/platform/testFramework/junit5/codeInsight/src/fixture/codeInsightFixture.kt`

Typical pattern:

```kotlin
private val tempDir = tempPathFixture()
private val project = projectFixture(tempDir, openAfterCreation = true)
private val module = project.moduleFixture(tempDir, addPathToSourceRoot = true)
private val fixture by codeInsightFixture(project, tempDir)
```

Representative usages:

- generic code insight: `platform/lsp-impl/tests/testSrc/LspServerTest.kt`
- Java code insight wrapper: `community/java/java-tests/testSrc/com/siyeh/ig/migration/ForCanBeForeachInspectionTest.java`
- Java-specific helper API: `community/java/testFramework/src/com/intellij/testFramework/javaCodeInsightFixture.kt`

## SDK And Interpreter Setup

There are three main patterns.

### Pattern A: Generic `sdkFixture(...)`

Use this when:

- you have a home path
- you only need an SDK in `ProjectJdkTable`
- the API under test reads SDKs from the table or from module dependencies

Representative usages:

- `community/platform/testFramework/junit5/projectStructure/test/com/intellij/platform/testFramework/junit5/projectStructure/fixture/JdkFixtureShowCaseTest.kt`
- multiverse DSL attaching SDK to a module:
  `community/platform/testFramework/junit5/projectStructure/test/com/intellij/platform/testFramework/junit5/projectStructure/fixture/MultiverseFixtureTest.kt`

### Pattern B: Manual JDK Attachment

Use this when:

- you need a mock JDK
- you must assign it to a specific module or project explicitly
- the subsystem already has an established helper

Representative usages:

- Java helper `setUpJdk(...)`:
  `community/java/testFramework/src/com/intellij/testFramework/javaCodeInsightFixture.kt`
- Java test calling that helper:
  `community/java/java-tests/testSrc/com/siyeh/ig/migration/ForCanBeForeachInspectionTest.java`
- manual mock JDK setup after multiverse creation:
  `community/java/java-tests/testSrc/com/intellij/java/psi/resolve/MultiverseSdkResolveTest.kt`

Confirmed details:

- `setUpJdk(level, project, module, disposable)` creates a mock JDK, adds it to `ProjectJdkTable`, sets it both on the project and on the module, and waits for indexes
- this is the most direct confirmed recipe when a JUnit 5 Java test needs Java PSI, resolve, inspections, or code insight

### Pattern B1: Java Test With JDK And `JavaCodeInsightTestFixture`

If the test needs a Java module with a JDK and then runs code insight on Java files, the best confirmed recipe is:

```java
@TestApplication
class MyJavaTest {
  private static final TestFixture<Disposable> disposable = disposableFixture();
  private static final TestFixture<Path> tempDir = tempPathFixture();
  private static final TestFixture<Project> project = projectFixture(tempDir, OpenProjectTask.build(), true);
  private static final TestFixture<Module> module = moduleFixture(project, tempDir, true);

  private final TestFixture<JavaCodeInsightTestFixture> fixture = javaCodeInsightFixture(project, tempDir);

  @BeforeAll
  static void beforeAll() {
    setUpJdk(LanguageLevel.JDK_17, project.get(), module.get(), disposable.get());
  }
}
```

Confirmed reference:

- `community/java/java-tests/testSrc/com/siyeh/ig/migration/ForCanBeForeachInspectionTest.java`

If the module already exists in an opened `.idea` project, use `project.moduleInProjectFixture("moduleName")` instead of creating a new one.
That lookup API is confirmed by:

- `community/platform/testFramework/junit5/src/fixture/fixtures.kt`
- `community/platform/lang-impl/testSources/com/intellij/psi/impl/file/impl/FileMoveTest.kt`

### Pattern B2: Java Or IML Project Compilation With `CompilerTester`

For actual compilation, the confirmed test utility is `CompilerTester`.

Confirmed references:

- implementation: `community/java/testFramework/src/com/intellij/testFramework/CompilerTester.java`
- compile helper around a project scope: `community/platform/external-system-impl/testSrc/com/intellij/openapi/externalSystem/test/JavaCompileTestUtil.kt`
- project-level compile preserving existing model/output: `community/plugins/devkit/devkit-java-tests/testSrc/org/jetbrains/idea/devkit/build/RuntimeModuleRepositoryCompilationTest.kt`

Important behavior from `CompilerTester` implementation:

- `CompilerTester(project, modules, disposable)` and `CompilerTester(module)` default to `overrideJdkAndOutput = true`
- in that mode `CompilerTester` sets compiler output to its own temp directory and, for local projects, assigns the internal JDK to the modules it compiles
- if the test already configured the desired JDK or output paths, use the overload with `overrideJdkAndOutput = false`

This leads to two practical recipes.

#### Recipe: compile and let `CompilerTester` provide an internal JDK

Use this when:

- the test only needs successful Java compilation
- the exact JDK is not the subject of the test

```kotlin
val messages = CompilerTester(project.get(), listOf(module.get()), disposable.get()).make()
```

This pattern is confirmed by `CompilerTester` itself and by many compilation-oriented tests built on legacy fixtures.

#### Recipe: compile an existing Java or IML project with your own JDK

Use this when:

- the project already has an `.iml` layout or an imported project model
- the test explicitly configured a JDK and wants compilation to use that JDK
- the test wants to preserve its own compiler output settings

```kotlin
private val disposable = disposableFixture()
private val projectPath = tempPathFixture()
private val project = projectFixture(projectPath, openAfterCreation = true)
private val module = project.moduleInProjectFixture("myModule")

@BeforeAll
fun setUp() {
  setUpJdk(LanguageLevel.JDK_17, project.get(), module.get(), disposable.get())
}

@Test
fun compileModule() {
  val messages = CompilerTester(project.get(), listOf(module.get()), disposable.get(), false).make()
  // assert no ERROR messages
}
```

This exact combination is derived from confirmed pieces:

- JDK setup via `setUpJdk(...)`: `community/java/testFramework/src/com/intellij/testFramework/javaCodeInsightFixture.kt`
- keep existing JDK/output by passing `false`: `community/java/testFramework/src/com/intellij/testFramework/CompilerTester.java`
- real project compilation with `CompilerTester(..., false)`: `community/plugins/devkit/devkit-java-tests/testSrc/org/jetbrains/idea/devkit/build/RuntimeModuleRepositoryCompilationTest.kt`

If you want a ready-made helper for asserting compilation errors over a compile scope, use the pattern from:

- `community/platform/external-system-impl/testSrc/com/intellij/openapi/externalSystem/test/JavaCompileTestUtil.kt`

### Pattern C: Python Venv And SDK Fixtures

Use subsystem-specific wrappers for Python environment tests.

Representative usage:

- `community/python/testSrc/com/intellij/python/junit5Tests/env/venv/showCase/PyEnvWithVenvShowCaseTest.kt`
- `python/junit5Tests/tests/com/intellij/python/junit5Tests/env/tests/sdk/SdkConfigurationUtilTest.kt`

Typical pattern:

```kotlin
private val tempPath = tempPathFixture()
private val module = projectFixture().moduleFixture(tempPath, addPathToSourceRoot = true)
private val venv = pySdkFixture().pyVenvFixture(
  where = tempPath,
  addToSdkTable = true,
  moduleFixture = module,
)
```

## `multiverseProjectFixture(...)`

Use `multiverseProjectFixture` when the project structure itself is the test subject, or when the setup is complex enough that piecing it together manually becomes noisy.

This is the public DSL for:

- multiple modules
- nested modules
- content roots
- source roots
- shared source roots
- SDK declarations
- module dependencies
- file trees

Representative DSL tests:

- simple project: `community/platform/testFramework/junit5/projectStructure/test/com/intellij/platform/testFramework/junit5/projectStructure/fixture/SimpleProjectTest.kt`
- module dependencies: `community/platform/testFramework/junit5/projectStructure/test/com/intellij/platform/testFramework/junit5/projectStructure/fixture/ProjectWithDependenciesTest.kt`
- shared roots and SDK DSL: `community/platform/testFramework/junit5/projectStructure/test/com/intellij/platform/testFramework/junit5/projectStructure/fixture/MultiverseFixtureTest.kt`
- rich multi-module tree: `community/platform/testFramework/junit5/projectStructure/test/com/intellij/platform/testFramework/junit5/projectStructure/fixture/JUnit5MultiverseFixtureTest.kt`

Typical DSL shape:

```kotlin
private val project = multiverseProjectFixture(openAfterCreation = true) {
  sdk("sdk", SomeSdkType) {
    file("lib.jar", byteArrayOf())
  }

  module("a") {
    dependencies {
      useSdk("sdk")
    }
    contentRoot("root") {
      sourceRoot("src", sourceRootId = "shared") {
        file("A.java", "class A {}")
      }
    }
  }

  module("b") {
    dependencies {
      module("a")
    }
    sharedSourceRoot("shared")
  }
}
```

Notes:

- the DSL is implemented on top of `projectFixture(...)`
- public content-root setup is exposed through this DSL, not through standalone content-root fixtures

Implementation reference:

- `community/platform/testFramework/junit5/projectStructure/src/com/intellij/platform/testFramework/junit5/projectStructure/fixture/impl/MultiverseFixtureInitializer.kt`

## Shared Source Roots

There are two separate patterns.

### Pattern A: `withSharedSourceEnabled()`

Use `projectFixture(...).withSharedSourceEnabled()` when the project needs shared-source support turned on.

Representative usages:

- `community/platform/lang-impl/testSources/com/intellij/openapi/module/impl/scopes/ModuleScopeTest.kt`
- `community/platform/lang-impl/testSources/com/intellij/psi/impl/file/impl/FileContextTest.kt`
- `community/java/java-tests/testSrc/com/intellij/java/codeInsight/codeVision/JavaMultiverseCodeVisionProviderTest.kt`

### Pattern B: Custom Shared Root Fixture

Some tests add the same source root to multiple modules explicitly.

Representative helper:

- `community/platform/lang-impl/testSources/com/intellij/psi/impl/file/impl/FileContextTest.kt`

This is a good pattern when:

- the DSL would be overkill
- the test wants a small local helper
- the shared-root semantics are part of the test itself

## State Replacement Fixtures

These fixtures are not about project structure, but agents should know them because they are frequently part of realistic setup.

### `disposableFixture()`

Use this for anything that needs a dedicated disposable.

Representative usages:

- `community/platform/testFramework/junit5/test/showcase/JUnit5InheritanceFixture.kt`
- `community/python/testSrc/com/intellij/python/junit5Tests/unit/PyInterpreterInspectionTest.kt`

### `registryKeyFixture(...)`

Use this when the test temporarily changes a registry value and wants automatic restoration.

Representative usages:

- `community/plugins/git4idea/tests/git4idea/tests/GitCommitTest.kt`
- `community/plugins/git4idea/tests/git4idea/test/GitPlatformTestContext.kt`

### `replacedServiceFixture(...)`

Use this to replace an application- or project-level service with a test double.

Representative usages:

- project-level replacement in VCS context:
  `community/platform/vcs-tests/src/com/intellij/vcs/test/VcsPlatformTestContext.kt`
- application- and project-level replacements in Git context:
  `community/plugins/git4idea/tests/git4idea/test/GitPlatformTestContext.kt`
- formatting notification service replacement:
  `community/platform/platform-tests/testSrc/com/intellij/formatting/StructuredAsyncDocumentFormattingTest.kt`

### `extensionPointFixture(...)`

Use this when a test needs to register a temporary extension.

Representative pattern:

- compose it with `projectFixture` or `disposableFixture` in a custom setup fixture

## Ecosystem-Specific Wrappers Built On Top Of This Framework

These wrappers are good references because they show how teams turn low-level fixtures into reusable setup APIs.

### JavaScript Debugger

`prepareProjectFixture(...)` prepares a project tree, `openProjectFixture(...)` opens it with a custom `OpenProjectTask`, and `jsDebuggerProjectFixture(...)` adds index waiting and cleanup.

Reference:

- `plugins/JavaScriptDebugger/testSrc/testFramework/junit5/projectFixtures.kt`

### Python

Python uses the same core fixtures plus:

- `@PyEnvTestCase`
- `pySdkFixture()`
- `pyVenvFixture(...)`
- local helper extensions for nested file creation and project bootstrap

Representative references:

- `community/python/junit5Tests-framework/src/com/intellij/python/junit5Tests/framework/PyDefaultTestApplication.kt`
- `community/python/testSrc/com/intellij/python/junit5Tests/env/venv/showCase/PyEnvWithVenvShowCaseTest.kt`

### LSP

LSP tests use:

- `tempPathFixture`
- `projectFixture(..., openAfterCreation = true)`
- `moduleFixture(tempDir, addPathToSourceRoot = true)`
- `codeInsightFixture(...)`
- additional LSP support fixtures

Reference:

- `platform/lsp-impl/tests/testSrc/LspServerTest.kt`

### CLion

CLion has wrappers because plain `projectFixture` is not enough when the test must go through open processors, VFS root access, toolchain setup, workspace customizers, and project import behavior.

Representative reference:

- `CIDR/clion-testFramework-nolang/junit5/core/src/com/intellij/clion/testFramework/nolang/junit5/core/fixtures.kt`

This is the right model when your subsystem has extra invariants around project open and close.

### EEL

If the project must live on an EEL-backed filesystem, compose:

- `eelFixture(...)`
- `eel.tempDirFixture()`
- `projectFixture(tempDir, openAfterCreation = true)`

Representative references:

- `community/platform/testFramework/junit5/eel/src/fixture/fixtures.kt`
- `community/platform/testFramework/junit5/eel/test/showcase/EelProjectShowcase.kt`
- `community/platform/testFramework/junit5/eel/test/params/api/TestApplicationWithEel.kt`

## How To Choose The Right Setup

Use this decision tree.

### Choose `projectFixture()` directly when

- only a project container is needed
- no startup activities or open-project behavior is required

### Choose `projectFixture(..., openAfterCreation = true)` when

- code insight, editors, indexing, or project-open side effects matter

### Add `moduleFixture()` when

- code insight or language logic expects at least one module
- the subsystem resolves files through module roots

### Use `moduleFixture(pathFixture, addPathToSourceRoot = true)` when

- the module root must exist physically
- the source root should equal the physical project/module directory

### Use `sourceRootFixture(blueprintResourcePath = ...)` when

- the test needs a prebuilt tree of files under a source root
- one-file helpers are too limited

### Use `multiverseProjectFixture(...)` when

- you need multiple modules
- you need content roots
- you need shared roots
- you need module dependencies or SDKs in the setup itself

### Use subsystem wrappers when

- the subsystem already has a canonical project bootstrap sequence
- plain `projectFixture` would miss open/import/customizer logic

## Common Mistakes

- Forgetting `@TestApplication` or another annotation that includes `@TestFixtures`.
- Calling `fixture.get()` inside another fixture initializer instead of `fixture.init()`.
- Using `codeInsightFixture(...)` without creating at least one module first.
- Using `projectFixture()` without `openAfterCreation = true` in tests that depend on open-project lifecycle.
- Expecting `psiFileFixture(...)` or `virtualFileFixture(...)` to create nested relative paths.
- Looking for a public `contentRootFixture` API. There is no public one; use the multiverse DSL for content-root-heavy layouts.
- Reimplementing a subsystem's project bootstrap when a dedicated wrapper already exists, for example in JS debugger, Python env tests, CLion, or Gradle.

## Recommended Reference Tests

If you need concrete examples, start with these.

Core framework showcase:

- `community/platform/testFramework/junit5/test/showcase/JUnit5ProjectFixtureTest.kt`
- `community/platform/testFramework/junit5/test/showcase/JUnit5ModuleFixtureTest.kt`
- `community/platform/testFramework/junit5/test/showcase/JUnit5ModuleFixtureOnTempFixtureTest.kt`
- `community/platform/testFramework/junit5/test/showcase/JUnit5PsiFileFixtureTest.kt`
- `community/platform/testFramework/junit5/test/showcase/JUnit5EditorFixtureTest.kt`

Project-structure DSL:

- `community/platform/testFramework/junit5/projectStructure/test/com/intellij/platform/testFramework/junit5/projectStructure/fixture/SimpleProjectTest.kt`
- `community/platform/testFramework/junit5/projectStructure/test/com/intellij/platform/testFramework/junit5/projectStructure/fixture/ProjectWithDependenciesTest.kt`
- `community/platform/testFramework/junit5/projectStructure/test/com/intellij/platform/testFramework/junit5/projectStructure/fixture/MultiverseFixtureTest.kt`
- `community/platform/testFramework/junit5/projectStructure/test/com/intellij/platform/testFramework/junit5/projectStructure/fixture/JUnit5MultiverseFixtureTest.kt`

Cross-language and subsystem examples:

- Java: `community/java/java-tests/testSrc/com/siyeh/ig/migration/ForCanBeForeachInspectionTest.java`
- Java with multiverse plus manual JDK: `community/java/java-tests/testSrc/com/intellij/java/psi/resolve/MultiverseSdkResolveTest.kt`
- Python env: `community/python/testSrc/com/intellij/python/junit5Tests/env/venv/showCase/PyEnvWithVenvShowCaseTest.kt`
- Python large source-tree bootstrap: `python/junit5Tests/tests/com/intellij/python/junit5Tests/env/tests/PyTypingConformanceTest.kt`
- JS debugger wrapper: `plugins/JavaScriptDebugger/testSrc/testFramework/junit5/projectFixtures.kt`
- EEL-backed project: `community/platform/testFramework/junit5/eel/test/showcase/EelProjectShowcase.kt`
- LSP plus code insight: `platform/lsp-impl/tests/testSrc/LspServerTest.kt`
- CLion wrappers: `CIDR/clion-testFramework-nolang/junit5/core/src/com/intellij/clion/testFramework/nolang/junit5/core/fixtures.kt`

## Final Guidance For Agents

Prefer composition over custom setup code.

Good:

- start from `projectFixture`
- add `moduleFixture`
- add `sourceRootFixture`
- add `psiFileFixture`, `editorFixture`, `codeInsightFixture`, `sdkFixture`, or subsystem wrappers as needed

Also good:

- write a small local helper fixture when the subsystem repeats the same setup many times
- use `multiverseProjectFixture` when structure is the main concern

Avoid:

- open-coded teardown when a fixture can own that lifecycle
- mutable global setup unless it is wrapped in `registryKeyFixture`, `replacedServiceFixture`, or a subsystem-specific fixture
- rebuilding the same project bootstrap logic in every test class
