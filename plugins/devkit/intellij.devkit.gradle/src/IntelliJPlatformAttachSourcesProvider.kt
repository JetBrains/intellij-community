// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.devkit.gradle

import com.intellij.codeInsight.AttachSourcesProvider
import com.intellij.codeInsight.AttachSourcesProvider.AttachSourcesAction
import com.intellij.java.library.MavenCoordinates
import com.intellij.java.library.getMavenCoordinates
import com.intellij.notification.BrowseNotificationAction
import com.intellij.notification.NotificationType
import com.intellij.openapi.application.EDT
import com.intellij.openapi.application.WriteAction
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.module.Module
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.LibraryOrderEntry
import com.intellij.openapi.roots.OrderRootType
import com.intellij.openapi.roots.impl.libraries.LibraryEx
import com.intellij.openapi.roots.libraries.Library
import com.intellij.openapi.util.ActionCallback
import com.intellij.openapi.vfs.JarFileSystem
import com.intellij.openapi.vfs.VfsUtilCore
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.VirtualFileManager
import com.intellij.platform.backend.workspace.WorkspaceModel
import com.intellij.platform.workspace.jps.entities.LibraryEntity
import com.intellij.platform.workspace.jps.entities.ModuleEntity
import com.intellij.psi.PsiFile
import com.intellij.workspaceModel.ide.legacyBridge.findLibraryBridge
import com.intellij.workspaceModel.ide.legacyBridge.findModule
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.future.await
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.jetbrains.annotations.Nls
import org.jetbrains.annotations.PropertyKey
import org.jetbrains.idea.devkit.projectRoots.IntelliJPlatformProduct
import org.jetbrains.idea.devkit.run.ProductInfo
import com.intellij.devkit.gradle.tooling.IntelliJPlatformSourceCoordinates
import org.jetbrains.idea.devkit.run.loadProductInfo
import org.jetbrains.plugins.gradle.GradleCoroutineScope.gradleCoroutineScope
import org.jetbrains.plugins.gradle.execution.build.CachedModuleDataFinder
import org.jetbrains.plugins.gradle.service.project.GradleNotification
import org.jetbrains.plugins.gradle.util.GradleArtifactDownloader
import org.jetbrains.plugins.gradle.util.GradleDependencySourceDownloaderErrorHandler
import org.jetbrains.plugins.gradle.util.GradleModuleData
import java.io.IOException
import java.nio.file.Path
import java.util.Properties
import java.util.zip.ZipFile
import kotlin.io.path.Path
import kotlin.io.path.exists
import kotlin.io.path.pathString

private val LOG = logger<IntelliJPlatformAttachSourcesProvider>()

internal enum class ApiSourceArchive(
  val id: String,
  @PropertyKey(resourceBundle = DevKitGradleBundle.BUNDLE) val displayName: String,
  val archiveName: String,
) {
  CSS("com.intellij.css", "attachSources.api.action.displayName.css", "src_css-api.zip"),
  JAM("com.intellij.java", "attachSources.api.action.displayName.jam", "src_jam-openapi.zip"),
  JAVAEE("com.intellij.javaee", "attachSources.api.action.displayName.javaee", "src_javaee-openapi.zip"),
  PERSISTENCE("com.intellij.persistence", "attachSources.api.action.displayName.persistence", "src_persistence-openapi.zip"),
  SPRING("com.intellij.spring", "attachSources.api.action.displayName.spring", "src_spring-openapi.zip"),
  SPRING_BOOT("com.intellij.spring.boot", "attachSources.api.action.displayName.springBoot", "src_spring-boot-openapi.zip"),
  TOMCAT("Tomcat", "attachSources.api.action.displayName.tomcat", "src_tomcat.zip"),
  LSP("LSP", "attachSources.api.action.displayName.lsp", "src_lsp-openapi.zip"),
}

/**
 * Attaches sources to the IntelliJ Platform dependencies in projects using IntelliJ Platform Gradle Plugin 2.x.
 * Some IDEs, like IntelliJ IDEA Ultimate or PhpStorm, don't provide sources for artifacts published to IntelliJ Repository.
 * To handle such a case, IntelliJ IDEA Community sources are attached.
 */
internal class IntelliJPlatformAttachSourcesProvider : AttachSourcesProvider {

  override fun getLibrariesActions(
    libraryEntities: Collection<LibraryEntity>,
    psiFile: PsiFile
  ): Collection<AttachSourcesAction?> {
    val storage = WorkspaceModel.getInstance(psiFile.project).currentSnapshot
    return libraryEntities.mapNotNull { it.findLibraryBridge(storage)?.getMavenCoordinates() }
      .firstNotNullOfOrNull { coordinates -> createAction(coordinates, psiFile) }
      .let { listOfNotNull(it) }
  }

  override fun getActions(orderEntries: List<LibraryOrderEntry>, psiFile: PsiFile) =
    orderEntries
      .mapNotNull { it.library?.getMavenCoordinates() }
      .firstNotNullOfOrNull { coordinates -> createAction(coordinates, psiFile) }
      .let { listOfNotNull(it) }

  private fun createAction(coordinates: MavenCoordinates, psiFile: PsiFile): AttachSourcesAction? {
    val product = IntelliJPlatformProduct.fromMavenCoordinates(coordinates.groupId, coordinates.artifactId)
                  ?: IntelliJPlatformProduct.fromCdnCoordinates(coordinates.groupId, coordinates.artifactId)

    val platformAction = when {
      // IntelliJ Platform dependency, such as `com.jetbrains.intellij.idea:ideaIC:2023.2.7`, `idea:ideaIC:aarch64:2024.3`, or `idea:ideaIC:2023.2.7`
      product != null -> resolveIntelliJPlatformAction(psiFile, coordinates.version)

      // IntelliJ Platform local installation, such as `localIde:IC:2023.2.7+445`
      coordinates.groupId == "localIde" -> createAttachLocalPlatformSourcesAction(psiFile, coordinates)

      // IntelliJ Platform bundled plugin, such as `bundledPlugin:org.intellij.groovy:IC-243.21565.193`, `bundledPlugin:Git4Idea:2023.2.7+445`
      coordinates.groupId == "bundledPlugin" -> createAttachBundledPluginSourcesAction(psiFile, coordinates)

      // IntelliJ Platform bundled module, such as `bundledModule:intellij.platform.coverage:IC-243.21565.193`
      coordinates.groupId == "bundledModule" -> createAttachBundledModuleSourcesAction(psiFile, coordinates)

      // Non-bundled JetBrains plugins, such as `com.jetbrains.plugins:PythonCore:243.21565.193`
      coordinates.groupId == IntelliJPlatformSourceCoordinates.JETBRAINS_PLUGIN_GROUP -> createAttachJetBrainsPluginSourcesAction(psiFile, coordinates)

      else -> null
    } ?: return null

    // A third-party library bundled with the IntelliJ Platform, such as `commons-cli:commons-cli:1.11.0` in `lib/intellij.libraries.commons.cli.jar`
    return createAttachLibrarySourcesAction(psiFile, platformAction) ?: platformAction
  }


  /**
   * Resolve and attach IntelliJ Platform sources to the currently handled dependency in a requested version.
   *
   * Requests PyCharm Community sources if PyCharm Community or PyCharm.
   * Requests IntelliJ IDEA Ultimate sources if IntelliJ IDEA Ultimate 2024.2+.
   * In all other cases, requests IntelliJ IDEA Community sources.
   *
   * If the LSP API class is detected while targeting IntelliJ IDEA Ultimate <2024.2, attaches bundled LSP API sources archive file.
   *
   * @param psiFile The PSI file that represents the currently handled class.
   * @param version The version of the product.
   */
  private fun resolveIntelliJPlatformAction(
    psiFile: PsiFile,
    version: String,
  ): AttachSourcesAction? {
    val productInfo = resolveProductInfo(psiFile) ?: return null
    val product = IntelliJPlatformProduct.fromProductCode(productInfo.productCode) ?: return null
    val buildNumber = productInfo.buildNumber
    val majorVersion = buildNumber.substringBefore('.').toInt()
    val rangedVersion = "[$majorVersion,$buildNumber]!!$buildNumber"
    val productCoordinates = resolveProductCoordinates(product, majorVersion)

    return when {
      // We're handing LSP API class, but IU is lower than 242 -> attach a standalone sources file
      isLspApiSourcesArchive(psiFile, product, majorVersion) -> createAttachSourcesArchiveAction(psiFile, ApiSourceArchive.LSP)

      // Create the actual IntelliJ Platform sources attaching action
      else -> createAttachPlatformSourcesAction(psiFile, productCoordinates, version, rangedVersion)
    }
  }

  /**
   * Creates an action to attach sources of a local IntelliJ Platform.
   *
   * @param psiFile The PSI file representing the currently handled class.
   * @param coordinates The Maven coordinates of the IntelliJ Platform whose sources need to be attached.
   */
  private fun createAttachLocalPlatformSourcesAction(psiFile: PsiFile, coordinates: MavenCoordinates) =
    resolveIntelliJPlatformAction(psiFile, IntelliJPlatformSourceCoordinates.extractActualVersion(coordinates.version))

  /**
   * Creates an action to attach sources of bundled plugins for the IntelliJ Platform.
   *
   * @param psiFile The PSI file that represents the currently handled class.
   * @param coordinates The Maven coordinates of the bundled plugin whose sources need to be attached.
   */
  private fun createAttachBundledPluginSourcesAction(psiFile: PsiFile, coordinates: MavenCoordinates) =
    createAttachSourcesArchiveAction(psiFile, ApiSourceArchive.entries.firstOrNull { it.id == coordinates.artifactId })
    ?: resolveIntelliJPlatformAction(psiFile, IntelliJPlatformSourceCoordinates.extractActualVersion(coordinates.version))

  /**
   * Creates an action to attach sources of bundled modules for the IntelliJ Platform.
   *
   * @param psiFile The PSI file that represents the currently handled class.
   * @param coordinates The Maven coordinates of the bundled module whose sources need to be attached.
   */
  private fun createAttachBundledModuleSourcesAction(psiFile: PsiFile, coordinates: MavenCoordinates) =
    createAttachBundledPluginSourcesAction(psiFile, coordinates)

  /**
   * Creates an action to attach sources for non-bundled JetBrains plugins hosted on the JetBrains Maven repo.
   *
   * Currently, handles PythonCore and Pythonid. Their sources are published
   * as PyCharm Community sources (`com.jetbrains.intellij.pycharm:pycharmPC`).
   *
   * @param psiFile The PSI file that represents the currently handled class.
   * @param coordinates The Maven coordinates of the plugin whose sources need to be attached.
   */
  private fun createAttachJetBrainsPluginSourcesAction(psiFile: PsiFile, coordinates: MavenCoordinates): AttachSourcesAction? {
    if (coordinates.artifactId != "PythonCore" && coordinates.artifactId != "Pythonid") return null
    val version = IntelliJPlatformSourceCoordinates.extractActualVersion(coordinates.version)
    val majorVersion = IntelliJPlatformSourceCoordinates.extractMajorVersion(version)
    val rangedVersion = "[$majorVersion,$version]!!$version"
    return createAttachPlatformSourcesAction(psiFile, IntelliJPlatformSourceCoordinates.PYCHARM_COMMUNITY_SOURCES, version, rangedVersion)
  }

  /**
   * Attach the provided sources archive.
   *
   * @param psiFile The PSI file that represents the currently handled class.
   * @param apiSourceArchive API sources archive metadata.
   */
  private fun createAttachSourcesArchiveAction(psiFile: PsiFile, apiSourceArchive: ApiSourceArchive?): AttachSourcesAction? {
    if (apiSourceArchive == null) {
      return null
    }

    if (apiSourceArchive == ApiSourceArchive.JAM && !isJamSourcesArchive(psiFile)) {
      return null
    }

    return resolveSourcesArchive(psiFile, apiSourceArchive.archiveName)?.let {
      object : AttachSourcesAction {

        override fun getName() = DevKitGradleBundle.message("attachSources.api.action.name", DevKitGradleBundle.message(apiSourceArchive.displayName))

        override fun getBusyText() = DevKitGradleBundle.message("attachSources.api.action.busyText", DevKitGradleBundle.message(apiSourceArchive.displayName))

        override fun perform(orderEntries: MutableList<out LibraryOrderEntry>): ActionCallback {
          val libraries = orderEntries.mapNotNull { entry -> entry.library }

          return performInternal(libraries)
        }

        override fun perform(libraryEntities: Collection<LibraryEntity>, project: Project): ActionCallback {
          val currentSnapshot = WorkspaceModel.getInstance(project).currentSnapshot
          val libraries = libraryEntities.mapNotNull { it.findLibraryBridge(currentSnapshot) }

          return performInternal(libraries)
        }

        private fun performInternal(libraries: Collection<Library>): ActionCallback {
          val executionResult = ActionCallback()

          psiFile.project.gradleCoroutineScope.launch {
            try {
              attachSources(it, libraries)
              executionResult.setDone()
            }
            catch (e: Throwable) {
              executionResult.setRejected()
              throw e
            }
          }

          return executionResult
        }
      }
    }
  }

  /**
   * Creates an action to attach sources of a third-party library bundled with the IntelliJ Platform.
   *
   * The IntelliJ Platform build keeps the `META-INF/maven/<groupId>/<artifactId>/pom.properties` file of the original library in the jar.
   * The action downloads the `sources` artifact for these coordinates instead of the IntelliJ Platform sources.
   * The action attaches the first archive that contains the package of the handled class.
   * A jar can merge IntelliJ Platform classes with a third-party library, such as `lib/intellij.platform.ide.impl.jar`.
   * When no archive contains the package, the action runs [platformAction] instead.
   * When no archive downloads at all, the action reports it first, because the library repository is missing in the project.
   *
   * @param psiFile The PSI file that represents the currently handled class.
   * @param platformAction The action that attaches the IntelliJ Platform sources of the same library.
   * @return The action, or `null` when the jar of [psiFile] carries no Maven metadata.
   */
  private fun createAttachLibrarySourcesAction(psiFile: PsiFile, platformAction: AttachSourcesAction): AttachSourcesAction? {
    val jarRoot = VfsUtilCore.getRootFile(psiFile.virtualFile)
    val coordinates = readLibraryCoordinates(jarRoot)
    if (coordinates.isEmpty()) {
      return null
    }

    val classPath = psiFile.virtualFile.path.substringAfter("!/")
    return createDownloadSourcesAction(
      psiFile,
      name = DevKitGradleBundle.message("attachSources.library.action.name"),
      busyText = DevKitGradleBundle.message("attachSources.library.action.busyText"),
      notations = coordinates.map { "$it:sources" },
      failure = DownloadFailure("attachSources.library.failed.title", "attachSources.library.failed.content"),
      accept = { path -> containsPackageSources(path, classPath) },
      fallback = platformAction,
    )
  }

  /**
   * Creates an action to attach IntelliJ Platform sources to a library within the project.
   *
   * @param psiFile The PSI file that represents the currently handled class.
   * @param productCoordinates The Maven coordinates of the IntelliJ Platform whose sources we load.
   * @param version The version of the product.
   * @param closeVersion The alternative version of the product used for resolving sources of a close version.
   */
  private fun createAttachPlatformSourcesAction(psiFile: PsiFile, productCoordinates: String, version: String, closeVersion: String): AttachSourcesAction {
    val snapshotVersion = version.substringBefore('.') + "-SNAPSHOT"
    return createDownloadSourcesAction(
      psiFile,
      name = DevKitGradleBundle.message("attachSources.intellijPlatform.action.name"),
      busyText = DevKitGradleBundle.message("attachSources.intellijPlatform.action.busyText"),
      notations = listOf(
        "$productCoordinates:$version:sources",
        "$productCoordinates:$closeVersion:sources",
        "$productCoordinates:$snapshotVersion:sources", // last chance, nightly snapshot
      ),
      failure = DownloadFailure("attachSources.intellijPlatform.failed.title", "attachSources.intellijPlatform.failed.content"),
    )
  }

  /**
   * The notification shown when a download action downloads no archive.
   *
   * @param titleKey The bundle key of the notification title.
   * @param contentKey The bundle key of the notification content. It takes the notation, the Gradle project, and the documentation URL.
   */
  private class DownloadFailure(
    @PropertyKey(resourceBundle = DevKitGradleBundle.BUNDLE) val titleKey: String,
    @PropertyKey(resourceBundle = DevKitGradleBundle.BUNDLE) val contentKey: String,
  )

  /**
   * Creates an action that downloads the first available sources archive from [notations] and attaches it to the library.
   *
   * @param psiFile The PSI file that represents the currently handled class.
   * @param name The name of the action.
   * @param busyText The text shown while the action runs.
   * @param notations The Gradle artifact notations to try in order.
   * @param failure The notification shown when no archive from [notations] downloads.
   * @param accept Checks a downloaded archive. The action skips the archive and tries the next notation when it returns `false`.
   * @param fallback The action to run when no archive from [notations] is attached.
   */
  private fun createDownloadSourcesAction(
    psiFile: PsiFile,
    name: @Nls String,
    busyText: @Nls String,
    notations: List<String>,
    failure: DownloadFailure,
    accept: (Path) -> Boolean = { true },
    fallback: AttachSourcesAction? = null,
  ) =
    object : AttachSourcesAction {
      override fun getName() = name

      override fun getBusyText() = busyText

      @Deprecated("Deprecated in Java")
      override fun perform(orderEntries: MutableList<out LibraryOrderEntry>): ActionCallback {
        return performInternal(orderEntries.first().ownerModule, orderEntries.mapNotNull { it.library }) {
          @Suppress("DEPRECATION")
          fallback?.perform(orderEntries)
        }
      }

      override fun perform(libraryEntities: Collection<LibraryEntity>, project: Project): ActionCallback {
        val snapshot = WorkspaceModel.getInstance(project).currentSnapshot
        val module = libraryEntities.flatMap { snapshot.referrers(it.symbolicId, ModuleEntity::class.java) }
          .firstOrNull()
          ?.findModule(snapshot)
        if (module != null) {
          return performInternal(module, libraryEntities.mapNotNull { it.findLibraryBridge(snapshot) }) {
            fallback?.perform(libraryEntities, project)
          }
        }
        return ActionCallback.REJECTED
      }

      /**
       * Downloads and attaches the first accepted archive.
       * When no archive downloads, reports [failure]. A downloaded archive that [accept] rejects is not a failure.
       * When no archive is attached, runs [performFallback] on the EDT and joins its result,
       * or rejects the result when it returns `null`.
       *
       * The work runs in a coroutine of the project, and not in a continuation of the download future.
       * The continuation runs in the context of the completed download job, which cancels an IDE service initialization.
       */
      private fun performInternal(module: Module, libraries: Collection<Library>, performFallback: () -> ActionCallback?): ActionCallback {
        val moduleData = CachedModuleDataFinder.getGradleModuleData(module)
                         ?: return ActionCallback.REJECTED

        val executionResult = ActionCallback()
        val project = psiFile.project

        project.gradleCoroutineScope.launch {
          try {
            var downloaded = false
            for (artifactNotation in notations) {
              val path = GradleArtifactDownloader.downloadArtifact(project, name, artifactNotation, moduleData,
                                                                   GradleDependencySourceDownloaderErrorHandler.Noop).await()
                         ?: continue
              downloaded = true
              if (accept(path)) {
                attachSources(path, libraries)
                executionResult.setDone()
                return@launch
              }
            }
            withContext(Dispatchers.EDT) {
              if (!downloaded) {
                notifyDownloadFailed(project, moduleData, notations.first(), failure)
              }
              performFallback()?.notify(executionResult) ?: executionResult.setRejected()
            }
          }
          catch (e: Throwable) {
            executionResult.setRejected()
            throw e
          }
        }

        return executionResult
      }
    }

  /**
   * Attaches the sources archive in [path] to [libraries].
   * Refreshes the VFS for [path] on a background thread.
   * Then adds the archive root as a sources root of [libraries] on the EDT.
   */
  private suspend fun attachSources(path: Path, libraries: Collection<Library>) {
    val archiveRoot = withContext(Dispatchers.IO) {
      VirtualFileManager.getInstance().refreshAndFindFileByNioPath(path)
        ?.let { JarFileSystem.getInstance().getJarRootForLocalFile(it) }
    } ?: return
    withContext(Dispatchers.EDT) {
      addSourcesRoot(archiveRoot, libraries)
    }
  }

  private fun addSourcesRoot(root: VirtualFile, libraries: Collection<Library>) {
    WriteAction.run<RuntimeException> {
      for (library in libraries) {
        if (library is LibraryEx && library.isDisposed) continue
        if (root in library.getFiles(OrderRootType.SOURCES)) continue
        val model = library.modifiableModel
        model.addRoot(root, OrderRootType.SOURCES)
        model.commit()
      }
    }
  }

  private fun notifyDownloadFailed(project: Project, moduleData: GradleModuleData, notation: String, failure: DownloadFailure) {
    val gradleProject = moduleData.gradlePathOrNull ?: moduleData.gradleProjectDir
    GradleNotification.gradleNotificationGroup
      .createNotification(
        title = DevKitGradleBundle.message(failure.titleKey),
        content = DevKitGradleBundle.message(failure.contentKey, notation, gradleProject, REPOSITORIES_DOCUMENTATION_URL),
        type = NotificationType.WARNING
      )
      .setDisplayId("devkit.gradle.attachSources.intellijPlatform.failed")
      .addAction(BrowseNotificationAction(
        DevKitGradleBundle.message("attachSources.intellijPlatform.failed.documentation"),
        REPOSITORIES_DOCUMENTATION_URL,
      ))
      .notify(project)
  }

  /**
   * Resolve the [ProductInfo] of the current IntelliJ Platform.
   */
  private fun resolveProductInfo(psiFile: PsiFile): ProductInfo? {
    val jarPath = Path(psiFile.virtualFile.path.substringBefore('!'))
    return generateSequence(jarPath) { it.parent }
      .takeWhile { it != it.root }
      .firstNotNullOfOrNull { loadProductInfo(it.pathString) }
  }

  /**
   * Resolve the [ProductInfo] of the current IntelliJ Platform.
   */
  private fun resolveSourcesArchive(psiFile: PsiFile, archiveName: String): Path? {
    val path = VfsUtilCore.getVirtualFileForJar(psiFile.virtualFile)?.toNioPath() ?: return null
    return generateSequence(path) { it.parent }
      .takeWhile { it != it.root }
      .firstNotNullOfOrNull { it.resolve("lib/src/$archiveName").takeIf(Path::exists) }
  }

  /**
   * When targeting IntelliJ IDEA Ultimate, it is possible to attach LSP module sources.
   * If the compiled class belongs to `com/intellij/platform/lsp/`, suggest attaching the relevant ZIP archive with LSP sources.
   *
   * LSP API sources are provided only with IU.
   */
  private fun isLspApiSourcesArchive(psiFile: PsiFile, product: IntelliJPlatformProduct, majorVersion: Int) =
    when {
      majorVersion >= 242 -> false
      product != IntelliJPlatformProduct.IDEA -> false
      else -> {
        val classPath = psiFile.virtualFile.path.substringAfter('!')
        when {
          classPath.startsWith("/com/intellij/platform/lsp/impl/") -> false
          classPath.startsWith("/com/intellij/platform/lsp/") -> true
          else -> false
        }
      }
    }

  /**
   * Checks if [psiFile] belongs to the `/com/intellij/jam/` package
   */
  private fun isJamSourcesArchive(psiFile: PsiFile): Boolean {
    val classPath = psiFile.virtualFile.path.substringAfter('!')
    return classPath.startsWith("/com/intellij/jam/")
  }

  private companion object {
    const val REPOSITORIES_DOCUMENTATION_URL = "https://jb.gg/ijpgp-docs#configuration.repositories"
  }

  private fun resolveProductCoordinates(product: IntelliJPlatformProduct, majorVersion: Int) =
    when (product) {
      IntelliJPlatformProduct.PYCHARM, IntelliJPlatformProduct.PYCHARM_PC ->
        IntelliJPlatformSourceCoordinates.PYCHARM_COMMUNITY_SOURCES
      IntelliJPlatformProduct.IDEA ->
        IntelliJPlatformSourceCoordinates.ideaUltimateSources(majorVersion)
      else ->
        IntelliJPlatformSourceCoordinates.defaultPlatformSources(majorVersion)
    }
}

/**
 * Reads the Maven coordinates of the libraries packed into the jar with the given [jarRoot].
 * The coordinates come from every `META-INF/maven/<groupId>/<artifactId>/pom.properties` entry.
 *
 * @return The coordinates in the `groupId:artifactId:version` form, or an empty list when the jar has no Maven metadata.
 */
internal fun readLibraryCoordinates(jarRoot: VirtualFile?): List<String> {
  val mavenRoot = jarRoot?.findFileByRelativePath("META-INF/maven") ?: return emptyList()
  return mavenRoot.children
    .flatMap { group -> group.children.asList() }
    .mapNotNull { artifact -> artifact.findChild("pom.properties") }
    .mapNotNull { file ->
      val properties = Properties().apply { file.inputStream.use { load(it) } }
      val groupId = properties.getProperty("groupId") ?: return@mapNotNull null
      val artifactId = properties.getProperty("artifactId") ?: return@mapNotNull null
      val version = properties.getProperty("version") ?: return@mapNotNull null
      "$groupId:$artifactId:$version"
    }
}

/**
 * Checks if the sources archive in [path] contains a source file in the package of the class in [classPath].
 * The [classPath] is relative to the jar root, such as `org/apache/commons/cli/Option$Builder.class`.
 *
 * The check uses the package directory and not the class name,
 * because a Kotlin or Groovy source file can declare classes with other names, such as `FlowKt` in `Flow.kt`.
 */
internal fun containsPackageSources(path: Path, classPath: String): Boolean {
  val packagePrefix = classPath.substringBeforeLast('/', "").let { if (it.isEmpty()) "" else "$it/" }
  return try {
    ZipFile(path.pathString).use { zip ->
      zip.entries().asSequence().any { entry ->
        !entry.isDirectory && entry.name.startsWith(packagePrefix) && '/' !in entry.name.substring(packagePrefix.length)
      }
    }
  }
  catch (e: IOException) {
    LOG.warn("Cannot read the sources archive $path", e)
    false
  }
}
