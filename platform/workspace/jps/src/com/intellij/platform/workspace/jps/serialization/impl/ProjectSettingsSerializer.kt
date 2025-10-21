// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.workspace.jps.serialization.impl

import com.intellij.java.workspace.entities.JavaProjectSettingsEntity
import com.intellij.platform.diagnostic.telemetry.helpers.MillisecondsMeasurer
import com.intellij.platform.workspace.jps.JpsFileEntitySource
import com.intellij.platform.workspace.jps.entities.ProjectSettingsEntity
import com.intellij.platform.workspace.jps.entities.SdkId
import com.intellij.platform.workspace.storage.EntityStorage
import com.intellij.platform.workspace.storage.MutableEntityStorage
import com.intellij.platform.workspace.storage.WorkspaceEntity
import com.intellij.platform.workspace.storage.url.VirtualFileUrl
import com.intellij.platform.workspace.storage.url.VirtualFileUrlManager
import org.jdom.Element
import org.jetbrains.jps.model.serialization.JDomSerializationUtil

private const val TAG_PROJECT_ROOT_MANAGER = "ProjectRootManager"

private const val ATTR_VERSION = "version"

private const val ATTR_LANGUAGE_LEVEL = "languageLevel"

private const val ATTR_LANGUAGE_LEVEL_DEFAULT = "default"

private const val ATTR_PROJECT_JDK_NAME = "project-jdk-name"

private const val ATTR_PROJECT_JDK_TYPE = "project-jdk-type"

private const val TAG_OUTPUT = "output"

private const val ATTR_URL = "url"

class ProjectSettingsSerializer(
  override val fileUrl: VirtualFileUrl,
  override val internalEntitySource: JpsFileEntitySource,
) : JpsFileEntityTypeSerializer<ProjectSettingsEntity> {

  override val isExternalStorage: Boolean = false

  override val mainEntityClass: Class<ProjectSettingsEntity> = ProjectSettingsEntity::class.java
  override val additionalEntityTypes: List<Class<out WorkspaceEntity>> = listOf(JavaProjectSettingsEntity::class.java)

  override fun deleteObsoleteFile(fileUrl: String, writer: JpsFileContentWriter) {
    writer.saveComponent(fileUrl, TAG_PROJECT_ROOT_MANAGER, null)
  }

  override fun loadEntities(
    reader: JpsFileContentReader,
    errorReporter: ErrorReporter,
    virtualFileManager: VirtualFileUrlManager,
  ): LoadingResult<Map<Class<out WorkspaceEntity>, Collection<WorkspaceEntity.Builder<out WorkspaceEntity>>>> = loadEntitiesTimeMs.addMeasuredTime {

    val projectRootManager = runCatchingXmlIssues { reader.loadComponent(fileUrl.url, TAG_PROJECT_ROOT_MANAGER) }
      .getOrElse { return@addMeasuredTime LoadingResult(emptyMap(), it) }

    if (projectRootManager == null) {
      return@addMeasuredTime LoadingResult(emptyMap())
    }

    val projectSettingsEntityBuilder = ProjectSettingsEntity(internalEntitySource)
    val javaProjectSettingsEntityBuilder = JavaProjectSettingsEntity(internalEntitySource) {
      projectSettings = projectSettingsEntityBuilder
    }

    runCatchingXmlIssues {
      val languageLevelStr = projectRootManager.getAttribute(ATTR_LANGUAGE_LEVEL)?.value
      javaProjectSettingsEntityBuilder.languageLevelId = languageLevelStr
      javaProjectSettingsEntityBuilder.languageLevelDefault = projectRootManager.getAttribute(ATTR_LANGUAGE_LEVEL_DEFAULT)?.value?.toBoolean()
      val sdkNameStr = projectRootManager.getAttribute(ATTR_PROJECT_JDK_NAME)?.value
      val sdkTypeStr = projectRootManager.getAttribute(ATTR_PROJECT_JDK_TYPE)?.value
      if (sdkNameStr != null && sdkTypeStr != null) {
        projectSettingsEntityBuilder.projectSdk = SdkId(sdkNameStr, sdkTypeStr)
      }

      val pathElement: Element? = projectRootManager.getChild(TAG_OUTPUT)
      pathElement?.getAttributeValue(ATTR_URL)?.also { outputPath ->
        val outputUrl = virtualFileManager.getOrCreateFromUrl(outputPath)
        javaProjectSettingsEntityBuilder.compilerOutput = outputUrl
      }
    }

    return@addMeasuredTime LoadingResult(mapOf(
      ProjectSettingsEntity::class.java to listOf(projectSettingsEntityBuilder),
      JavaProjectSettingsEntity::class.java to listOf(javaProjectSettingsEntityBuilder),
    ))
  }

  override fun checkAndAddToBuilder(builder: MutableEntityStorage, orphanage: MutableEntityStorage, newEntities: Map<Class<out WorkspaceEntity>, Collection<WorkspaceEntity.Builder<out WorkspaceEntity>>>) {
    newEntities.forEach { (_, value) ->
      value.forEach { builder.addEntity(it) }
    }
  }

  override fun saveEntities(
    mainEntities: Collection<ProjectSettingsEntity>,
    entities: Map<Class<out WorkspaceEntity>, List<WorkspaceEntity>>,
    storage: EntityStorage,
    writer: JpsFileContentWriter,
  ): Unit = saveEntitiesTimeMs.addMeasuredTime {

    val projectSettingsEntity = mainEntities.firstOrNull()
    if (projectSettingsEntity == null) return@addMeasuredTime

    val javaProjectSettingsEntity = entities[JavaProjectSettingsEntity::class.java]?.firstOrNull() as JavaProjectSettingsEntity?

    val componentTag = JDomSerializationUtil.createComponentElement(TAG_PROJECT_ROOT_MANAGER)
    componentTag.setAttribute(ATTR_VERSION, "2")

    if (javaProjectSettingsEntity?.languageLevelId != null) {
      componentTag.setAttribute(ATTR_LANGUAGE_LEVEL, javaProjectSettingsEntity.languageLevelId)
    }
    javaProjectSettingsEntity?.languageLevelDefault?.also { notNullLanguageLevelDefault ->
      // mimic behavior from commit 6e8bec26: do not write `false` (which is the default in DefaultProject)
      if (notNullLanguageLevelDefault) {
        componentTag.setAttribute(ATTR_LANGUAGE_LEVEL_DEFAULT, notNullLanguageLevelDefault.toString())
      }
    }

    val projectSdk = projectSettingsEntity.projectSdk
    if (projectSdk != null) {
      componentTag.setAttribute(ATTR_PROJECT_JDK_NAME, projectSdk.name)
      componentTag.setAttribute(ATTR_PROJECT_JDK_TYPE, projectSdk.type)
    }
    else {
      componentTag.removeAttribute(ATTR_PROJECT_JDK_NAME)
      componentTag.removeAttribute(ATTR_PROJECT_JDK_TYPE)
    }

    javaProjectSettingsEntity?.compilerOutput?.also { compilerOutput ->
      val compilerOutputTag = Element(TAG_OUTPUT)
      compilerOutputTag.setAttribute(ATTR_URL, compilerOutput.url)
      componentTag.addContent(compilerOutputTag)
    }
    writer.saveComponent(fileUrl.url, TAG_PROJECT_ROOT_MANAGER, componentTag)
  }

  companion object {
    private val loadEntitiesTimeMs = MillisecondsMeasurer()
    private val saveEntitiesTimeMs = MillisecondsMeasurer()
  }
}