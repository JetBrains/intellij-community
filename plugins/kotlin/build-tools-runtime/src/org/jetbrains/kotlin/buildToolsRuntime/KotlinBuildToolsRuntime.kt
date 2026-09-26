package org.jetbrains.kotlin.buildToolsRuntime

import com.intellij.openapi.application.PathManager
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.util.lang.UrlClassLoader
import org.jetbrains.kotlin.buildtools.api.ExecutionPolicy
import org.jetbrains.kotlin.buildtools.api.ExperimentalBuildToolsApi
import org.jetbrains.kotlin.buildtools.api.KotlinToolchains
import org.jetbrains.kotlin.buildtools.api.SharedApiClassesClassLoader
import org.jetbrains.kotlin.config.KotlinCompilerVersion
import org.jetbrains.kotlin.jsr223.Jsr223KotlincProvider
import org.jetbrains.kotlin.tooling.core.KotlinToolingVersion
import org.jetbrains.kotlin.utils.KotlinPaths
import org.jetbrains.kotlin.utils.KotlinPathsFromHomeDir
import java.net.URL
import java.net.URLClassLoader
import java.nio.file.Path

/**
 * App-level factory for a Kotlin Build Tools API (BTA) toolchain backed by the compiler closure this
 * plugin bundles, so any plugin can compile Kotlin in-IDE without shipping its own compiler libraries.
 * Callers pick an execution policy ([daemonExecutionPolicy] / [inProcessExecutionPolicy]) and own their
 * [KotlinToolchains.BuildSession]s.
 *
 * Everything here is keyed by the compiler version alone. [Jsr223KotlincProvider] resolves the for-ide
 * dist, which the plugin bundles or downloads from the JetBrains mirrors. The toolchain is therefore
 * cached app-wide and needs no project context. Note that the first call can block on that download.
 * Run it off the EDT.
 *
 * Isolation is the point: the toolchain classloader resolves the whole compiler closure at one version
 * and never reaches the IDE's older bundled standalone `kotlinc`, which would cause class skew.
 *
 * Reuse surface: [toolchains] — cached ready instance; [createToolchains] — fresh one with extra impls
 * (e.g. `kotlin-build-tools-cri-impl` for `session.kotlinToolchains.cri`); [runtimeClasspath] — the
 * bundled closure for callers assembling their own toolchain.
 */
@OptIn(ExperimentalBuildToolsApi::class)
@Service(Service.Level.APP)
class KotlinBuildToolsRuntime {
  companion object {
    @JvmStatic
    fun getInstance(): KotlinBuildToolsRuntime = service()
  }

  private val cached: Lazy<KotlinToolchains> = lazy(LazyThreadSafetyMode.SYNCHRONIZED) { createToolchains() }

  fun toolchains(): KotlinToolchains = cached.value

  fun daemonExecutionPolicy(): ExecutionPolicy = cached.value.daemonExecutionPolicyBuilder().build()

  fun inProcessExecutionPolicy(): ExecutionPolicy = cached.value.createInProcessExecutionPolicy()

  /**
   * Fresh [KotlinToolchains] over this plugin's closure ([runtimeClasspath]) plus [additionalClasspath]
   * (impls this module doesn't bundle, e.g. `kotlin-build-tools-cri-impl`). The for-ide dist is always
   * included, so `kotlin-stdlib` and the full compiler are present for any execution policy. NOT cached
   * ([toolchains] is the cached no-extra instance).
   *
   * In-process-only caveat: everything resolves from an isolated classloader (parent shares only
   * `buildtools.api.*`). If you instead want a minimal classpath that delegates `kotlin-stdlib` to
   * plugin/platform (as CRI does), build your own toolchain from [runtimeClasspath].
   */
  fun createToolchains(additionalClasspath: List<Path> = emptyList()): KotlinToolchains {
    val daemonJvmOrder = kotlinHomeClasspath(Jsr223KotlincProvider.ideKotlinc)
    // In-process closure = plugin jars first (so `build-tools-impl` resolves the exact `-for-ide`
    // classes it was compiled against) + the dist (supplies `kotlin-stdlib` and the rest
    // of the compiler, which this module doesn't bundle).
    val ideJvmOrder = (runtimeClasspath() + additionalClasspath + daemonJvmOrder).distinct()
    val toolchainClassLoader = HybridOrderUrlClassLoader(
      ideJvmOrder = ideJvmOrder.map { it.toUri().toURL() }.toTypedArray(),
      daemonJvmOrder = daemonJvmOrder.map { it.toUri().toURL() }.toTypedArray(),
      // Shares only `org.jetbrains.kotlin.buildtools.api.*` from the classloader that loaded the API
      // (this plugin's), so the ServiceLoaded impl is type-compatible with the `KotlinToolchains`
      // consumers hold.
      parent = SharedApiClassesClassLoader(),
    )
    return KotlinToolchains.loadImplementation(toolchainClassLoader)
  }

  /**
   * Filesystem jars of the BTA closure this plugin bundles (`build-tools-impl` + `compiler-common` +
   * `tooling-core`) — one self-consistent version set.
   */
  fun runtimeClasspath(): List<Path> {
    val classLoader = KotlinBuildToolsRuntime::class.java.classLoader
    check(classLoader is UrlClassLoader) {
      "Expected the KotlinBuildToolsRuntime module classloader to be ${UrlClassLoader::class.java.name}, but got ${classLoader.javaClass.name}"
    }
    return (classLoader.files + libraryModuleMarkers.map(::jarProviding)).distinct()
  }

  /** One class per closure jar that ships as a library module, used only to locate that jar on disk. */
  private val libraryModuleMarkers: List<Class<*>> = listOf(KotlinToolingVersion::class.java, KotlinCompilerVersion::class.java)

  private fun jarProviding(markerClass: Class<*>): Path =
    PathManager.getJarForClass(markerClass) ?: error("Cannot locate the jar providing ${markerClass.name}")
}

@Suppress("IO_FILE_USAGE")
internal fun kotlinHomeClasspath(home: Path): List<Path> =
  KotlinPathsFromHomeDir(home.toFile()).classPath(KotlinPaths.ClassPaths.CompilerWithScripting).map { it.toPath() }

/**
 * Needed because the in-process and daemon sides want different classpaths, yet BTA derives both from
 * one classloader: it resolves the toolchain impl through the loader directly, but builds the daemon's
 * `-cp` from that same loader's `getURLs()`. The two must differ — the daemon needs a complete, runnable
 * standalone for-ide dist as its `-cp`, while in-process resolution needs this module's `build-tools-impl`
 * closure ordered ahead of the dist. So we resolve from [ideJvmOrder] but report [daemonJvmOrder] from
 * `getURLs()`, handing the daemon the clean dist without disturbing in-process resolution order.
 */
private class HybridOrderUrlClassLoader(
  ideJvmOrder: Array<URL>,
  private val daemonJvmOrder: Array<URL>,
  parent: ClassLoader?,
) : URLClassLoader(ideJvmOrder, parent) {
  override fun getURLs(): Array<URL> = daemonJvmOrder.copyOf()
}
