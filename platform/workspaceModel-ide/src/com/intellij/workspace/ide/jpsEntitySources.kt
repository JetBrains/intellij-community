package com.intellij.workspace.ide

import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.ProjectModelExternalSource
import com.intellij.project.isDirectoryBased
import com.intellij.workspace.api.EntitySource
import com.intellij.workspace.api.VirtualFileUrl
import com.intellij.workspace.api.VirtualFileUrlManager
import com.intellij.util.PathUtil
import org.jetbrains.jps.util.JpsPathUtil

/**
 * Represents a file/directory where IntelliJ project is stored.
 */
sealed class JpsProjectStoragePlace {
  abstract val baseDirectoryUrl: String
  abstract fun exists(): Boolean

  data class DirectoryBased(val projectDir: VirtualFileUrl) : JpsProjectStoragePlace() {
    override val baseDirectoryUrl: String
      get() = projectDir.url

    override fun exists() = JpsPathUtil.urlToFile(projectDir.url).exists()
  }
  data class FileBased(val iprFile: VirtualFileUrl) : JpsProjectStoragePlace() {
    override val baseDirectoryUrl: String
      get() = PathUtil.getParentPath(iprFile.url)

    override fun exists() = JpsPathUtil.urlToFile(iprFile.url).exists()
  }
}

/**
 * Represents a specific xml file containing configuration of IntelliJ IDEA project in JPS format (*.ipr file or *.xml file under .idea directory)
 */
data class JpsFileEntitySource(val file: VirtualFileUrl, val projectPlace: JpsProjectStoragePlace) : EntitySource

data class ExternalEntitySource(val source: ProjectModelExternalSource) : EntitySource

/**
 * Represents entities added by user in IDE (either via Project Structure or Settings dialog, or by invoking an action like 'Create Library from Files').
 */
// TODO It's required to resolve conflicts on library save when the file to write library to is not determined yet
object IdeUiEntitySource : EntitySource

/**
 * Returns `null` for the default project
 */
val Project.storagePlace: JpsProjectStoragePlace?
  get() {
    if (isDirectoryBased) {
      if (basePath != null) {
        return JpsProjectStoragePlace.DirectoryBased(VirtualFileUrlManager.fromUrl(JpsPathUtil.pathToUrl(basePath)))
      }
    }
    else if (projectFilePath != null) {
      return JpsProjectStoragePlace.FileBased(VirtualFileUrlManager.fromUrl(JpsPathUtil.pathToUrl(projectFilePath)))
    }
    return null
  }