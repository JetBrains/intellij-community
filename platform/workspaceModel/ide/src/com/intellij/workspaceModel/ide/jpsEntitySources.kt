// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.workspaceModel.ide

import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.ExternalProjectSystemRegistry
import com.intellij.openapi.roots.ProjectModelExternalSource
import com.intellij.project.isDirectoryBased
import com.intellij.workspaceModel.storage.EntitySource
import com.intellij.workspaceModel.storage.url.VirtualFileUrl
import com.intellij.workspaceModel.storage.url.VirtualFileUrlManager
import org.jetbrains.jps.util.JpsPathUtil
import java.util.concurrent.atomic.AtomicInteger

/**
 * Represents a file/directory where IntelliJ project is stored.
 */
sealed class JpsProjectConfigLocation {
  val baseDirectoryUrlString: String
    get() = baseDirectoryUrl.url

  abstract val baseDirectoryUrl: VirtualFileUrl
  abstract fun exists(): Boolean

  data class DirectoryBased(val projectDir: VirtualFileUrl) : JpsProjectConfigLocation() {
    override val baseDirectoryUrl: VirtualFileUrl
      get() = projectDir

    override fun exists() = JpsPathUtil.urlToFile(projectDir.url).exists()
  }
  data class FileBased(val iprFile: VirtualFileUrl, val iprFileParent: VirtualFileUrl) : JpsProjectConfigLocation() {
    override val baseDirectoryUrl: VirtualFileUrl
      get() = iprFileParent

    override fun exists() = JpsPathUtil.urlToFile(iprFile.url).exists()
  }
}

/**
 * Represents an xml file containing configuration of IntelliJ IDEA project in JPS format (*.ipr file or *.xml file under .idea directory)
 */
sealed class JpsFileEntitySource : EntitySource {
  abstract val projectLocation: JpsProjectConfigLocation

  /**
   * Represents a specific xml file containing configuration of some entities of IntelliJ IDEA project.
   */
  data class ExactFile(val file: VirtualFileUrl, override val projectLocation: JpsProjectConfigLocation) : JpsFileEntitySource() {
    override val virtualFileUrl: VirtualFileUrl
      get() = file
  }

  /**
   * Represents an xml file located in the specified [directory] which contains configuration of some entities of IntelliJ IDEA project.
   * The file name is automatically derived from the entity name.
   */
  data class FileInDirectory(val directory: VirtualFileUrl, override val projectLocation: JpsProjectConfigLocation) : JpsFileEntitySource() {
    /**
     * Automatically generated value which is used to distinguish different files in [directory]. The actual name is stored in serialization
     * structures and may change if name of the corresponding entity has changed.
     */
    val fileNameId: Int = nextId.getAndIncrement()

    companion object {
      private val nextId = AtomicInteger()
    }

    override val virtualFileUrl: VirtualFileUrl
      get() = directory

    override fun equals(other: Any?): Boolean {
      if (this === other) return true
      return other is FileInDirectory && directory == other.directory && projectLocation == other.projectLocation && fileNameId == other.fileNameId
    }

    override fun hashCode(): Int {
      return directory.hashCode() * 31 * 31 + projectLocation.hashCode() * 31 + fileNameId
    }
  }
}

/**
 * Represents entities which configuration is loaded from an JPS format configuration file (e.g. *.iml, stored in [originalSource]) and some additional configuration
 * files (e.g. '.classpath' and *.eml files for Eclipse projects).
 */
interface JpsFileDependentEntitySource {
  val originalSource: JpsFileEntitySource
}

/**
 * Represents entities imported from external project system.
 */
data class ExternalEntitySource(val displayName: String, val id: String) : EntitySource

/**
 * Represents entities imported from external project system and stored in JPS format. They may be stored either in a regular project
 * configuration [internalFile] if [storedExternally] is `false` or in an external file under IDE caches directory if [storedExternally] is `true`.
 */
data class JpsImportedEntitySource(val internalFile: JpsFileEntitySource,
                                   val externalSystemId: String,
                                   val storedExternally: Boolean) : EntitySource, JpsFileDependentEntitySource {
  override val originalSource: JpsFileEntitySource
    get() = internalFile
  override val virtualFileUrl: VirtualFileUrl?
    get() = internalFile.virtualFileUrl
}

fun JpsImportedEntitySource.toExternalSource(): ProjectModelExternalSource = ExternalProjectSystemRegistry.getInstance().getSourceById(externalSystemId)
/**
 * Represents entities which are added to the model automatically and shouldn't be persisted
 */
object NonPersistentEntitySource : EntitySource

/**
 * Represents entities which are imported from some external model, but still have some *.iml file associated with them. That iml file
 * will be used to save configuration of facets added to the module. This is a temporary solution, later we should invent a way to store
 * such settings in their own files.
 */
interface CustomModuleEntitySource : EntitySource {
  val internalSource: JpsFileEntitySource
}

/**
 * Returns `null` for the default project
 */
fun getJpsProjectConfigLocation(project: Project): JpsProjectConfigLocation? {
  return if (project.isDirectoryBased) {
    project.basePath?.let {
      val virtualFileUrlManager = VirtualFileUrlManager.getInstance(project)
      JpsProjectConfigLocation.DirectoryBased(virtualFileUrlManager.fromPath(it))
    }
  }
  else {
    project.projectFilePath?.let {
      val virtualFileUrlManager = VirtualFileUrlManager.getInstance(project)
      val iprFile = virtualFileUrlManager.fromPath(it)
      JpsProjectConfigLocation.FileBased(iprFile, virtualFileUrlManager.getParentVirtualUrl(iprFile)!!)
    }
  }
}