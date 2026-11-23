# Kotlin JPS builder tests

The tests of the `intellij.kotlin.builder.jps.tests` module (`plugins/kotlin/jps/builder/tests`) build Kotlin projects
with the bundled JPS Kotlin builder through a pinned Build Tools API implementation. The implementation jars are not on
the test classpath: the test hands their directory to the builder, as the IDE hands it to the build process.

## The two pinned implementations

Every test compiles through one of two pinned `kotlin-build-tools-impl` versions.

| | bootstrap | stable |
| -- | -- | -- |
| `kotlin_test_dependencies.bzl` | `kotlinBuildToolsImplBootstrapVersion` | `kotlinBuildToolsImplStableVersion` |
| `KotlinJpsBuilderTestArtifacts` | `BUILD_TOOLS_IMPL_BOOTSTRAP_VERSION` | `BUILD_TOOLS_IMPL_STABLE_VERSION` |
| Suites | every suite whose name does not end in `StableTest` | the `*StableTest` suites |
| Updated | on every Kotlin bootstrap | catches up with `kotlincKotlinJpsPluginTestsVersion` and replaces it |

The versions do not follow the Kotlin compiler of the IDE, and the project model updater (`BazelKotlinDependencyFacade`)
changes none of them: they are updated by hand. When both versions are equal, the stable suites skip themselves
(`KotlinBtaImplementation.duplicatesBootstrap`).

## Where the versions live

Two files hold the same versions; a change always touches both.

- `plugins/kotlin/kotlin_test_dependencies.bzl` — the constants above and the `download_file` entries with their
  `sha256` values. Under Bazel the test target reads the jars from these entries: their names carry the bucket,
  `-bootstrap` or `-stable`, never the version, so a version bump changes no file name and no `BUILD.bazel` label.
- `plugins/kotlin/base/pluginTestFramework/src/org/jetbrains/kotlin/idea/artifacts/KotlinJpsBuilderTestArtifacts.kt` —
  the same versions, plus `TEST_DATA_VERSION`. Outside Bazel the tests download the same artifacts from Maven at run
  time.

## How to update a version

1. Set the new version in both files.
2. Update the `sha256` of every entry whose URL carries the version: all `*-bootstrap.jar` and `*-stable.jar` entries.
3. Move `TEST_DATA_VERSION` together with the bootstrap version: under Bazel the test data jar rides the bootstrap
   entry.
4. The fixed classpath entries — `annotations-13.0`, `kotlin-reflect-1.6.10`, `kotlinx-coroutines-core-jvm-1.8.0` —
   change only when the run-time classpath of the implementation changes.
5. Generate the tests and refresh the golden files (see below).

## Test data and golden files

- The cases come from `jps/jps-plugin/testData` of the Kotlin repository, published as
  `kotlin-jps-plugin-testdata-for-ide` at `TEST_DATA_VERSION`, and from the overlay
  `plugins/kotlin/jps/builder/tests/testData`. The overlay wins; a new case and every overwritten golden file go to
  the overlay, never to the artifact.
- A golden file is looked up as `<name>.bta-<full version>`, then `<name>.bta-<major.minor>`, then the plain name
  (`KotlinJpsTestData.goldenNames`). Run the tests with `-Didea.tests.overwrite.data=true` to write the golden files
  of a new version into the overlay.
- A case is muted by an `ij-muted.txt` file in its directory: one `<version prefix or *>: <reason>` per line.

## How the tests are generated

`GenerateKotlinJpsBuilderTests` writes `KotlinJpsIncrementalTestGenerated` from the union of the published test data
and the overlay.

- The "Generate Kotlin Tests" run configuration, or the `GenerateKotlinJpsBuilderTests` main class, regenerates it.
- `AllTestsGeneratedTest` and `FirGenerateTests` run the generator as an up-to-date check and fail when the class is
  out of date.
- Under Bazel the sandbox has no source tree: the check reads the shipped class from the run files of the `test-gen`
  file group of `plugins/kotlin/jps/builder/tests/BUILD.bazel`.
