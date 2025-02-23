// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.workspace.jps

import com.esotericsoftware.kryo.kryo5.DefaultSerializer
import com.esotericsoftware.kryo.kryo5.Kryo
import com.esotericsoftware.kryo.kryo5.Serializer
import com.esotericsoftware.kryo.kryo5.io.Input
import com.esotericsoftware.kryo.kryo5.io.Output
import com.intellij.platform.workspace.storage.EntitySource
import com.intellij.platform.workspace.jps.GlobalStorageEntitySource
import com.intellij.platform.workspace.storage.url.VirtualFileUrl
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.TestOnly
import java.util.concurrent.atomic.AtomicInteger

/**
 * Represents an xml file containing configuration of IntelliJ Platform project in JPS format (*.ipr file or *.xml file under .idea directory)
 */
sealed class JpsFileEntitySource : EntitySource

/**
 * Entity source with the information about project location. Our serialization mechanism relies on it.
 * [virtualFileUrl] to detect the location for the entities' serialization. We support the serialization into
 * the iml/xml only concrete type of entities: [ModuleEntity][com.intellij.platform.workspace.jps.entities.ModuleEntity],
 * [LibraryEntity][com.intellij.platform.workspace.jps.entities.LibraryEntity], [FacetEntity][com.intellij.platform.workspace.jps.entities.FacetEntity],
 * [SdkEntity][com.intellij.platform.workspace.jps.entities.SdkEntity], [ContentRootEntity][com.intellij.platform.workspace.jps.entities.ContentRootEntity],
 * [SourceRootEntity][com.intellij.platform.workspace.jps.entities.SourceRootEntity] and some other entities
 * (see the implementations of [JpsFileEntitiesSerializer][com.intellij.platform.workspace.jps.serialization.impl.JpsFileEntitiesSerializer] to
 * check all supported entities). But unfortunately, we don't support the serialization of custom entities yet.
 *
 * If your entity has to be presented in the files in the .idea folder, please consider using this entity source
 * and its derivatives.
 */
sealed class JpsProjectFileEntitySource : JpsFileEntitySource() {
  abstract val projectLocation: JpsProjectConfigLocation

  /**
   * Represents a specific xml file containing configuration of some entities
   */
  data class ExactFile(val file: VirtualFileUrl, override val projectLocation: JpsProjectConfigLocation) : JpsProjectFileEntitySource() {
    override val virtualFileUrl: VirtualFileUrl
      get() = file
  }

  /**
   * Represents an xml file located in the specified [directory] which contains configuration of some entities.
   * The file name is automatically derived from the entity name.
   */
  @DefaultSerializer(FileInDirectorySerializer::class)
  data class FileInDirectory(val directory: VirtualFileUrl,
                             override val projectLocation: JpsProjectConfigLocation) : JpsProjectFileEntitySource() {
    /**
     * Automatically generated value which is used to distinguish different files in [directory]. The actual name is stored in serialization
     * structures and may change if name of the corresponding entity has changed.
     */
    val fileNameId: Int = nextId.getAndIncrement()

    companion object {
      private val nextId = AtomicInteger()

      /**
       * This method is temporary added for tests only
       */
      @ApiStatus.Internal
      @TestOnly
      fun resetId() {
        nextId.set(0)
      }
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

    override fun toString(): String {
      return "FileInDirectory(directory=$directory, fileNameId=$fileNameId, projectLocation=$projectLocation)"
    }
  }
}

/**
 * Represents a specific xml file containing configuration of global IntelliJ IDEA entities.
 */
data class JpsGlobalFileEntitySource(val file: VirtualFileUrl) : JpsFileEntitySource(), GlobalStorageEntitySource

/**
 * Represents entities which configuration is loaded from an JPS format configuration file (e.g. *.iml, stored in [originalSource]) and some additional configuration
 * files (e.g. '.classpath' and *.eml files for Eclipse projects).
 */
interface JpsFileDependentEntitySource {
  val originalSource: JpsFileEntitySource
}

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

internal class FileInDirectorySerializer : Serializer<JpsProjectFileEntitySource.FileInDirectory>(false, true) {
  override fun write(kryo: Kryo, output: Output, o: JpsProjectFileEntitySource.FileInDirectory) {
    kryo.writeClassAndObject(output, o.directory)
    kryo.writeClassAndObject(output, o.projectLocation)
  }

  override fun read(kryo: Kryo, input: Input, type: Class<out JpsProjectFileEntitySource.FileInDirectory>): JpsProjectFileEntitySource.FileInDirectory {
    val fileUrl = kryo.readClassAndObject(input) as VirtualFileUrl
    val location = kryo.readClassAndObject(input) as JpsProjectConfigLocation
    return JpsProjectFileEntitySource.FileInDirectory(fileUrl, location)
  }
}

/**
 * Represents entities which are imported from some external model, but still have some *.iml file associated with them. That iml file
 * will be used to save configuration of facets added to the module. This is a temporary solution, later we should invent a way to store
 * such settings in their own files.
 */
interface CustomModuleEntitySource : EntitySource {
  val internalSource: JpsFileEntitySource
}

/**
 * Special sources for entities which are temporal parents for entities in [the orphanage][com.intellij.workspaceModel.ide.EntitiesOrphanage].
 */
object OrphanageWorkerEntitySource : EntitySource