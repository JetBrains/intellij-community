package com.intellij.workspace.ide

import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.ProjectModelExternalSource
import com.intellij.project.isDirectoryBased
import com.intellij.util.PathUtil
import com.intellij.workspace.api.EntitySource
import com.intellij.workspace.api.VirtualFileUrl
import com.intellij.workspace.api.VirtualFileUrlManager
import org.jetbrains.jps.util.JpsPathUtil
import java.util.concurrent.atomic.AtomicInteger

/**
 * Represents a file/directory where IntelliJ project is stored.
 */
sealed class JpsProjectStoragePlace {
  val baseDirectoryUrlString: String
    get() = baseDirectoryUrl.url

  abstract val baseDirectoryUrl: VirtualFileUrl
  abstract fun exists(): Boolean

  data class DirectoryBased(val projectDir: VirtualFileUrl) : JpsProjectStoragePlace() {
    override val baseDirectoryUrl: VirtualFileUrl
      get() = projectDir

    override fun exists() = JpsPathUtil.urlToFile(projectDir.url).exists()
  }
  data class FileBased(val iprFile: VirtualFileUrl) : JpsProjectStoragePlace() {
    override val baseDirectoryUrl: VirtualFileUrl
      get() = iprFile.parent!!

    override fun exists() = JpsPathUtil.urlToFile(iprFile.url).exists()
  }
}

/**
 * Represents an xml file containing configuration of IntelliJ IDEA project in JPS format (*.ipr file or *.xml file under .idea directory)
 */
sealed class JpsFileEntitySource : EntitySource {
  abstract val projectPlace: JpsProjectStoragePlace

  /**
   * Represents a specific xml file containing configuration of some entities of IntelliJ IDEA project.
   */
  data class ExactFile(val file: VirtualFileUrl, override val projectPlace: JpsProjectStoragePlace) : JpsFileEntitySource()

  /**
   * Represents an xml file located in the specified [directory] which contains configuration of some entities of IntelliJ IDEA project.
   * The file name is automatically derived from the entity name.
   */
  data class FileInDirectory(val directory: VirtualFileUrl, override val projectPlace: JpsProjectStoragePlace) : JpsFileEntitySource() {
    /**
     * Automatically generated value which is used to distinguish different files in [directory]. The actual name is stored in serialization
     * structures and may change if name of the corresponding entity has changed.
     */
    val fileNameId: Int = nextId.getAndIncrement()

    companion object {
      private val nextId = AtomicInteger()
    }

    override fun equals(other: Any?): Boolean {
      if (this === other) return true
      return other is FileInDirectory && directory == other.directory && projectPlace == other.projectPlace && fileNameId == other.fileNameId
    }

    override fun hashCode(): Int {
      return directory.hashCode() * 31 * 31 + projectPlace.hashCode() * 31 + fileNameId
    }
  }
}

data class ExternalEntitySource(val displayName: String, val id: String) : EntitySource

fun ProjectModelExternalSource.toEntitySource() = ExternalEntitySource(displayName, id)

/**
 * Represents entities which are added to the model automatically and shouldn't be persisted
 */
object NonPersistentEntitySource : EntitySource

/**
 * Returns `null` for the default project
 */
val Project.storagePlace: JpsProjectStoragePlace?
  get() = if (isDirectoryBased) {
    basePath?.let { JpsProjectStoragePlace.DirectoryBased(VirtualFileUrlManager.fromPath(it)) }
  }
  else {
    projectFilePath?.let { JpsProjectStoragePlace.FileBased(VirtualFileUrlManager.fromPath(it)) }
  }