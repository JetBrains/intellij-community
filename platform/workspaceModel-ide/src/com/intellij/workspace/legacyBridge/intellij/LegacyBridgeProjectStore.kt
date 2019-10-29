package com.intellij.workspace.legacyBridge.intellij

import com.intellij.configurationStore.*
import com.intellij.openapi.components.StoragePathMacros
import com.intellij.openapi.components.impl.stores.IComponentStore
import com.intellij.openapi.components.impl.stores.IProjectStore
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.impl.ProjectStoreFactory
import com.intellij.openapi.util.io.FileUtil
import com.intellij.workspace.jps.JpsFileContentWriter
import com.intellij.workspace.jps.JpsProjectModelSynchronizer
import com.intellij.workspace.jps.getProjectStateStorage
import com.intellij.util.PathUtil
import org.jdom.Element
import org.jetbrains.jps.util.JpsPathUtil
import java.util.concurrent.ConcurrentHashMap

internal class LegacyBridgeProjectStore(project: Project) : ProjectWithModulesStoreImpl(project) {
  override fun createSaveSessionProducerManager(): ProjectSaveSessionProducerManager {
    return ProjectWithModulesSaveSessionProducerManager(project)
  }

  override suspend fun saveModules(errors: MutableList<Throwable>,
                                   isForceSavingAllSettings: Boolean,
                                   projectSaveSessionManager: SaveSessionProducerManager): List<SaveSession> {
    val writer = JpsStorageContentWriter(projectSaveSessionManager as ProjectWithModulesSaveSessionProducerManager, this)
    project.getComponent(JpsProjectModelSynchronizer::class.java).saveChangedProjectEntities(writer)
    return super.saveModules(errors, isForceSavingAllSettings, projectSaveSessionManager)
  }

  override fun commitModuleComponents(moduleStore: ComponentStoreImpl,
                                      moduleSaveSessionManager: SaveSessionProducerManager,
                                      projectSaveSessionManager: SaveSessionProducerManager,
                                      isForceSavingAllSettings: Boolean,
                                      errors: MutableList<Throwable>) {
    super.commitModuleComponents(moduleStore, moduleSaveSessionManager, projectSaveSessionManager, isForceSavingAllSettings, errors)
    val moduleFilePath = moduleStore.storageManager.expandMacros(StoragePathMacros.MODULE_FILE)
    (projectSaveSessionManager as ProjectWithModulesSaveSessionProducerManager).commitComponents(moduleFilePath, moduleStore, moduleSaveSessionManager)
  }
}

internal class LegacyBridgeProjectStoreFactory : ProjectStoreFactory {
  override fun createStore(project: Project): IComponentStore {
    return if (project.isDefault) DefaultProjectStoreImpl(project) else LegacyBridgeProjectStore(project)
  }
}

private class JpsStorageContentWriter(private val session: ProjectWithModulesSaveSessionProducerManager,
                                      private val store: IProjectStore) : JpsFileContentWriter {
  override fun saveComponent(fileUrl: String, componentName: String, componentTag: Element?) {
    val filePath = JpsPathUtil.urlToPath(fileUrl)
    if (FileUtil.extensionEquals(filePath, "iml")) {
      session.setModuleComponentState(filePath, componentName, componentTag)
    }
    else {
      val stateStorage = getProjectStateStorage(filePath, store)
      val producer = session.getProducer(stateStorage)
      if (producer is DirectoryBasedSaveSessionProducer) {
        producer.setFileState(PathUtil.getFileName(filePath), componentName, componentTag)
      }
      else {
        producer?.setState(null, componentName, componentTag)
      }
    }
  }

}

private val MODULE_FILE_STORAGE_ANNOTATION = FileStorageAnnotation(StoragePathMacros.MODULE_FILE, false)

private class ProjectWithModulesSaveSessionProducerManager(project: Project) : ProjectSaveSessionProducerManager(project) {
  private val moduleComponents = ConcurrentHashMap<String, ConcurrentHashMap<String, Element?>>()

  internal fun setModuleComponentState(imlFilePath: String, componentName: String, componentTag: Element?) {
    val componentToElement = moduleComponents.computeIfAbsent(imlFilePath) { ConcurrentHashMap() }
    componentToElement[componentName] = componentTag
  }

  internal fun commitComponents(moduleFilePath: String, moduleStore: ComponentStoreImpl,
                                moduleSaveSessionManager: SaveSessionProducerManager) {
    val componentToElement = moduleComponents[moduleFilePath] ?: return
    val storage = moduleStore.storageManager.getStateStorage(MODULE_FILE_STORAGE_ANNOTATION)
    val producer = moduleSaveSessionManager.getProducer(storage) ?: return
    componentToElement.forEach { (componentName, componentTag) ->
      producer.setState(null, componentName, componentTag)
    }
  }
}