// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.workspace.jps.bridge.impl.serialization

import com.intellij.openapi.util.io.FileUtil
import com.intellij.platform.workspace.jps.UnloadedModulesNameHolder
import com.intellij.platform.workspace.jps.bridge.impl.JpsModelBridge
import com.intellij.platform.workspace.jps.bridge.impl.JpsProjectAdditionalData
import com.intellij.platform.workspace.jps.bridge.impl.library.sdk.JpsSdkLibraryBridge
import com.intellij.platform.workspace.jps.serialization.impl.ErrorReporter
import com.intellij.platform.workspace.jps.serialization.impl.JpsGlobalEntitiesSerializers
import com.intellij.platform.workspace.jps.serialization.impl.JpsProjectEntitiesLoader
import com.intellij.platform.workspace.jps.serialization.impl.toConfigLocation
import com.intellij.platform.workspace.storage.EntityStorage
import com.intellij.platform.workspace.storage.MutableEntityStorage
import com.intellij.platform.workspace.storage.impl.url.VirtualFileUrlManagerImpl
import com.intellij.platform.workspace.storage.url.VirtualFileUrl
import kotlinx.coroutines.runBlocking
import org.jetbrains.jps.model.JpsModel
import org.jetbrains.jps.model.JpsProject
import org.jetbrains.jps.model.serialization.JpsGlobalLoader
import org.jetbrains.jps.model.serialization.JpsGlobalSettingsLoading
import org.jetbrains.jps.model.serialization.JpsMacroExpander
import org.jetbrains.jps.model.serialization.impl.JpsSerializationViaWorkspaceModel
import java.nio.file.Path
import kotlin.io.path.Path

internal class JpsSerializationViaWorkspaceModelImpl : JpsSerializationViaWorkspaceModel {
  override fun loadModel(projectPath: String, optionsPath: String?, loadUnloadedModules: Boolean): JpsModel {
    val virtualFileUrlManager = VirtualFileUrlManagerImpl()
    val errorReporter = object : ErrorReporter {
      override fun reportError(message: String, file: VirtualFileUrl) {
        error(message)
      }
    }

    val globalStorage = MutableEntityStorage.create()
    val globalMacroExpander = if (optionsPath != null) {
      loadGlobalStorage(optionsPath, virtualFileUrlManager, errorReporter, globalStorage)
    }
    else {
      null
    }
    
    val (projectStorage, additionalData) = loadProjectStorage(Path(projectPath), virtualFileUrlManager, errorReporter)
    val model = JpsModelBridge(projectStorage, globalStorage, additionalData)
    
    if (optionsPath != null) {
      val globalLoader = JpsGlobalLoader(globalMacroExpander!!, model.global, arrayOf(JpsGlobalLoader.FILE_TYPES_SERIALIZER))
      globalLoader.load(Path(FileUtil.toCanonicalPath(optionsPath)))
    }
    return model
  }

  private fun loadProjectStorage(projectPath: Path, virtualFileUrlManager: VirtualFileUrlManagerImpl, errorReporter: ErrorReporter): Pair<EntityStorage, JpsProjectAdditionalData> {
    val configLocation = toConfigLocation(projectPath, virtualFileUrlManager)
    val externalStoragePath = Path("")//todo
    val context = SerializationContextImpl(virtualFileUrlManager)
    val serializers = JpsProjectEntitiesLoader.createProjectSerializers(configLocation, externalStoragePath, context)
    val mainStorage = MutableEntityStorage.create()
    val orphanageStorage = MutableEntityStorage.create()
    val unloadedStorage = MutableEntityStorage.create()
    val unloadedModuleNames = UnloadedModulesNameHolder.DUMMY
    @Suppress("SSBasedInspection")
    runBlocking {
      serializers.loadAll(context.fileContentReader, mainStorage, orphanageStorage, unloadedStorage, unloadedModuleNames, errorReporter)
    }
    
    return mainStorage to JpsProjectAdditionalData(TODO(), TODO())
  }

  private fun loadGlobalStorage(
    optionsPath: String,
    virtualFileUrlManager: VirtualFileUrlManagerImpl,
    errorReporter: ErrorReporter,
    globalStorage: MutableEntityStorage,
  ): JpsMacroExpander {
    val macroExpander = JpsMacroExpander(JpsGlobalSettingsLoading.computeAllPathVariables(optionsPath))
    val reader = DirectJpsFileContentReader(macroExpander)
    val rootsTypes = JpsSdkLibraryBridge.serializers.map { it.typeId }
    val serializers = JpsGlobalEntitiesSerializers.createApplicationSerializers(virtualFileUrlManager, rootsTypes)
    for (serializer in serializers) {
      val loaded = serializer.loadEntities(reader, errorReporter, virtualFileUrlManager)
      serializer.checkAndAddToBuilder(globalStorage, globalStorage, loaded.data)
      loaded.exception?.let { throw it }
    }
    return macroExpander
  }

  override fun loadProject(projectPath: String, pathVariables: Map<String, String>, loadUnloadedModules: Boolean): JpsProject {
    TODO("not implemented")
  }
}
