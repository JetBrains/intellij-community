// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.devkit.gradle

import com.intellij.codeInsight.AttachSourcesProvider
import com.intellij.codeInsight.AttachSourcesProvider.AttachSourcesAction
import com.intellij.jarFinder.InternetAttachSourceProvider
import com.intellij.java.library.MavenCoordinates
import com.intellij.java.library.getMavenCoordinates
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.externalSystem.util.ExternalSystemApiUtil
import com.intellij.openapi.roots.LibraryOrderEntry
import com.intellij.openapi.util.ActionCallback
import com.intellij.openapi.vfs.VfsUtilCore
import com.intellij.openapi.vfs.findFile
import com.intellij.psi.PsiFile
import org.jetbrains.idea.devkit.projectRoots.IntelliJPlatformProduct
import org.jetbrains.plugins.gradle.util.GradleDependencySourceDownloader
import java.io.File
import kotlin.io.path.Path

/**
 * Attaches sources to the IntelliJ Platform dependencies in projects using IntelliJ Platform Gradle Plugin 2.x.
 * Some IDEs, like IntelliJ IDEA Ultimate or PhpStorm, don't provide sources for artifacts published to IntelliJ Repository.
 * To handle such a case, IntelliJ IDEA Community sources are attached.
 */
class IntelliJPlatformAttachSourcesProvider : AttachSourcesProvider {

  override fun getActions(orderEntries: MutableList<out LibraryOrderEntry>, psiFile: PsiFile): List<AttachSourcesAction> {
    // Search for a product that matches any of the entry coordinates. Return both product and coordinates, to refer to the same version.
    val (product, libraryCoordinates) = orderEntries.mapNotNull {
      it.library?.getMavenCoordinates()
    }.firstNotNullOfOrNull { coordinates ->
      val product = IntelliJPlatformProduct.fromMavenCoordinates(coordinates.groupId, coordinates.artifactId)
      if (product == null) {
        return@firstNotNullOfOrNull null
      }
      product to coordinates
    } ?: return emptyList()

    // We're checking if the compiled class belongs to the `/com/intellij/platform/lsp/` package, but not `.../impl` as it's not part of the API
    val isLspApi = with(psiFile.virtualFile.path.substringAfter('!')) {
      when {
        startsWith("/com/intellij/platform/lsp/impl/") -> false
        startsWith("/com/intellij/platform/lsp/") -> true
        else -> false
      } && product == IntelliJPlatformProduct.IDEA // LSP API sources are provided only with IU
    }

    val action = when {
      isLspApi -> createAttachLSPSourcesAction(psiFile)
      else -> createAttachPlatformSourcesAction(psiFile, product, libraryCoordinates)
    }

    return action?.let { listOf(it) } ?: emptyList()
  }

  /**
   * Resolve and attach IntelliJ IDEA Community or PyCharm Community sources to the IntelliJ Platform dependency in requested version.
   */
  private fun createAttachPlatformSourcesAction(
    psiFile: PsiFile,
    product: IntelliJPlatformProduct,
    libraryCoordinates: MavenCoordinates,
  ): AttachSourcesAction? {
    val productCoordinates =
      when (product) {
        IntelliJPlatformProduct.PYCHARM, IntelliJPlatformProduct.PYCHARM_PC -> IntelliJPlatformProduct.PYCHARM_PC
        else -> IntelliJPlatformProduct.IDEA_IC
      }.mavenCoordinates ?: return null

    return object : AttachSourcesAction {
      override fun getName() = DevKitGradleBundle.message("attachSources.action.name")

      override fun getBusyText() = DevKitGradleBundle.message("attachSources.action.busyText")

      override fun perform(orderEntries: MutableList<out LibraryOrderEntry>): ActionCallback {
        val externalProjectPath = orderEntries.first().ownerModule.let {
          ExternalSystemApiUtil.getExternalRootProjectPath(it)
        } ?: return ActionCallback.REJECTED

        val executionResult = ActionCallback()
        val project = psiFile.project
        val sourceArtifactNotation = "$productCoordinates:${libraryCoordinates.version}:sources"

        GradleDependencySourceDownloader.downloadSources(project, name, sourceArtifactNotation, externalProjectPath).whenComplete { path, error ->
          if (error != null) {
            executionResult.setRejected()
          }
          else {
            attachSources(path, orderEntries) {
              executionResult.setDone()
            }
          }
        }

        return executionResult
      }
    }
  }

  /**
   * When targeting IntelliJ IDEA Ultimate, it is possible to attach LSP module sources.
   * If the compiled class belongs to `com/intellij/platform/lsp/`, suggest attaching relevant ZIP archive with LSP sources.
   */
  private fun createAttachLSPSourcesAction(psiFile: PsiFile): AttachSourcesAction? {
    val jarFile = VfsUtilCore.getVirtualFileForJar(psiFile.virtualFile) ?: return null
    val sources = jarFile.parent.findFile("src/src_lsp-openapi.zip") ?: return null

    return object : AttachSourcesAction {
      override fun getName() = DevKitGradleBundle.message("attachLSPSources.action.name")

      override fun getBusyText() = DevKitGradleBundle.message("attachLSPSources.action.busyText")

      override fun perform(orderEntries: MutableList<out LibraryOrderEntry>): ActionCallback {
        val executionResult = ActionCallback()

        attachSources(sources.toNioPath().toFile(), orderEntries) {
          executionResult.setDone()
        }

        return executionResult
      }
    }
  }

  /**
   * Attaches sources jar to the specified libraries and executes the provided block of code.
   */
  private fun attachSources(path: File, orderEntries: MutableList<out LibraryOrderEntry>, block: () -> Unit) {
    ApplicationManager.getApplication().invokeLater {
      InternetAttachSourceProvider.attachSourceJar(path, orderEntries.mapNotNull { it.library })
      block()
    }
  }
}
