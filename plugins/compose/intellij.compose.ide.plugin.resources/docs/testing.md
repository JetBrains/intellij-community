# Compose resources tests

The tests are in the `intellij.compose.ide.plugin.resources.tests` module, under
`tests/com/intellij/compose/ide/plugin/resources`.

## Which base class to use

Use `ComposeResourcesCodeInsightTestCase` for a new test. It is the JUnit 5 base class for a feature test:
completion, highlighting, folding, a gutter icon, a quick fix, a reference, a live template, or a PSI change.
It opens the test project one time, and it gives you the Gradle fixture and the code insight fixture.

Do not add a new test to `ComposeResourcesTestCase`. That class is the old JUnit 4 base class, and it extends
`KotlinGradleImportingTestCase`. Only `ComposeResourcesGradleImportTest` still uses it, because that test asserts
the result of the Gradle import itself. The class goes away when the last test moves to JUnit 5.

Use `BasePlatformTestCase`, or a plain JUnit test, when the code under test needs no Gradle project. The SVG
conversion tests and the XML comparison tests work this way, and they run in seconds.

| Test subject                                          | Base class                              |
|-------------------------------------------------------|-----------------------------------------|
| A code insight feature in a Compose resources project | `ComposeResourcesCodeInsightTestCase`   |
| The Gradle import and the project model               | `ComposeResourcesTestCase`              |
| Pure logic with no project                            | `BasePlatformTestCase`, or a plain test |

## How to write a feature test

Declare the source sets with an annotation, then put each assertion in a `testComposeResourcesProject` block.

```kotlin
@ComposeResourcesCommonMainOnly
class ComposeResourcesXmlAnnotatorTest : ComposeResourcesCodeInsightTestCase() {

  @Test
  fun `test highlighting in strings xml`() = testComposeResourcesProject {
    writeTextAndCommit(STRINGS_FILE_PATH, content)

    codeInsightFixture.configureFromExistingVirtualFile(canonicalProjectFile(STRINGS_FILE_PATH))
    assertHighlight(codeInsightFixture.doHighlighting())
  }
}
```

Open the file through `canonicalProjectFile`, and not through the file that `writeTextAndCommit` returns. A Compose
resources feature checks the file against the Compose resources directory of the Gradle model, and that model path is
canonical. With the fixture path the check fails, and the feature stays silent.

Two annotations select the source sets:

- `@ComposeResourcesAllSourceSets` runs the class for `commonMain`, `androidMain`, and `iosMain`.
- `@ComposeResourcesCommonMainOnly` runs the class for `commonMain` only. Use it when the feature does not
  depend on the source set, because it saves two thirds of the run time.

The `sourceSetName` property holds the current source set. `@ParameterizedClass` on the base class supplies it.

## The helpers on the base class

- `testComposeResourcesProject { }` opens the shared test project with the target Gradle version.
- `writeTextAndCommit` comes from `GradleProjectTestCase`. It writes a project file and snapshots it, so the
  fixture restores the file after the test.
- `snapshotProjectFile` snapshots a file that you change through another path.
- `canonicalProjectFile` resolves the real path before the VFS lookup. On macOS the fixture path starts with
  `/var`, and the real path starts with `/private/var`. `VirtualFile.getCanonicalFile` returns `null` while the
  real path is absent from the VFS.
- `projectRelativePath` is the inverse of `canonicalProjectFile`.
- `revertUnsavedDocuments` reloads an unsaved document from the disk. Call it when the rollback fails with a
  memory-disk conflict.

## The test project and the test data

The test project is `testData/ComposeResources`. The base class copies it into the Gradle fixture, and it skips
`build`, `.gradle`, `.idea`, `.kotlin`, and `local.properties`.

The fixture caches the copied project between runs. `TestFilesConfigurationImpl.areContentsEqual` compares only
the files that `withFile` declares, and it ignores a `withFiles` builder. The base class therefore writes a
SHA-256 fingerprint of the test data to `.gradle/testDataFingerprint`. When you change the test data, the
fingerprint changes, and the fixture rebuilds the project. You need no manual cache reset.

Use `COMPOSE_RESOURCES_TEST_DATA_RELATIVE_PATH` or `composeResourcesTestDataRoot()` from
`ComposeResourcesTestUtils.kt` to reach the test data. Do not repeat the path literal.

## The leak trackers

The teardown of `ComposeResourcesCodeInsightTestCase` removes the Kotlin SDK and each Android SDK that the
Gradle sync created. Keep that step when you override `tearDown`, and call the super method.

`registerLongRunningAndroidThreads` puts the Android ADB threads on the thread leak tracker allow list. It runs
in `@BeforeAll`, and `@AfterAll` removes the prefixes again. The allow list is static, so a class scope keeps a
later test class able to detect a leak of the same threads.

## How to run the tests

Run one test class:

```
./tests.cmd --module intellij.compose.ide.plugin.resources.tests --test com.intellij.compose.ide.plugin.resources.highlighting.ComposeResourcesXmlAnnotatorTest
```

The `--test` value must be a fully qualified name. A simple class name matches nothing. `tests.cmd` compiles
with Bazel, so you need no separate build step.

To check the compilation only, run this command from the `community` directory:

```
./bazel.cmd build //plugins/compose/intellij.compose.ide.plugin.resources:ide-plugin-resources-tests_test_lib
```

A feature test runs a real Gradle sync, and it needs about 80 seconds. A test with
`@ComposeResourcesAllSourceSets` needs more. Run the fast tests first while you develop.
