### Module `intellij.kotlin.base.plugin.testFramework`

Kotlin compiler artifact provisioning for tests.

This module is responsible for downloading, caching, and providing access to Kotlin toolchain artifacts
that are required during test execution. It does **not** provide test base classes or test utilities —
its sole concern is making Kotlin binaries available.

Key components:

- **`TestKotlinArtifacts`** — central singleton with 80+ lazily resolved properties for Kotlin artifacts:
  stdlib (JVM, JS, Wasm), compiler, reflect, script runtime, test libraries, annotations, coroutines,
  JPS plugin, and Kotlin/Native prebuilt distributions. Handles both Bazel-runtime and manual-download paths.
- **`KmpLightFixtureDependencyDownloader`** — resolves Kotlin Multiplatform library dependencies
  (JARs, platform klibs, metadata) from Maven repositories for manual test fixture configuration.
- **`KotlinGradlePluginDownloader`** — downloads specific versions of the Kotlin Gradle Plugin JAR
  from Maven Central, Kotlin Bootstrap, or IntelliJ Dependencies repositories.
- **`KotlinNativePrebuiltDownloader`** / **`KotlinNativeHostSupportDetector`** — downloads and extracts
  Kotlin/Native prebuilt distributions for the current host platform, with host compatibility validation.
- **`TestKotlinArtifactsProviderImpl`** — SPI implementation of `TestKotlinArtifactsProvider`,
  exposing JPS plugin classpath and kotlinc distribution path for use by other modules.
- **Project descriptors** — `KotlinJvmLightProjectDescriptor` for setting up in-memory test projects with Kotlin stdlib,
  specific JDK versions, and JetBrains annotations.

All downloads are cached under `out/kotlin-from-sources-deps/` with SHA-256 checksum verification.
The module includes special handling for cooperative Kotlin compiler development
(version `255-dev-255` and custom compiler builds).
