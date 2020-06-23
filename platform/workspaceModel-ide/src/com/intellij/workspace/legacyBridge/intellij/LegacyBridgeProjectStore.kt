package com.intellij.workspace.legacyBridge.intellij

import com.intellij.configurationStore.*
import com.intellij.openapi.components.Storage
import com.intellij.openapi.components.StoragePathMacros
import com.intellij.openapi.components.impl.stores.IComponentStore
import com.intellij.openapi.components.impl.stores.IProjectStore
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.impl.ProjectStoreFactory
import com.intellij.openapi.util.io.FileUtil
import com.intellij.util.PathUtil
import com.intellij.workspace.jps.JpsFileContentWriter
import com.intellij.workspace.jps.JpsProjectModelSynchronizer
import com.intellij.workspace.jps.getProjectStateStorage
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
    val writer = JpsStorageContentWriter(projectSaveSessionManager as ProjectWithModulesSaveSessionProducerManager, this, project)
    project.getComponent(JpsProjectModelSynchronizer::class.java).saveChangedProjectEntities(writer)
    return super.saveModules(errors, isForceSavingAllSettings, projectSaveSessionManager)
  }

  override fun commitModuleComponents(moduleStore: ComponentStoreImpl,
                                      moduleSaveSessionManager: SaveSessionProducerManager,
                                      projectSaveSessionManager: SaveSessionProducerManager,
                                      isForceSavingAllSettings: Boolean,
                                      errors: MutableList<Throwable>) {
    super.commitModuleComponents(moduleStore, moduleSaveSessionManager, projectSaveSessionManager, isForceSavingAllSettings, errors)
    (projectSaveSessionManager as ProjectWithModulesSaveSessionProducerManager).commitComponents(moduleStore, moduleSaveSessionManager)
  }
}

internal class LegacyBridgeProjectStoreFactory : ProjectStoreFactory {
  override fun createStore(project: Project): IComponentStore {
    return if (project.isDefault) DefaultProjectStoreImpl(project) else LegacyBridgeProjectStore(project)
  }
}

private class JpsStorageContentWriter(private val session: ProjectWithModulesSaveSessionProducerManager,
                                      private val store: IProjectStore,
                                      private val project: Project) : JpsFileContentWriter {
  override fun saveComponent(fileUrl: String, componentName: String, componentTag: Element?) {
    val filePath = JpsPathUtil.urlToPath(fileUrl)
    if (FileUtil.extensionEquals(filePath, "iml")) {
      session.setModuleComponentState(filePath, componentName, componentTag)
    }
    else if (isExternalModuleFile(filePath)) {
      session.setExternalModuleComponentState(FileUtil.getNameWithoutExtension(PathUtil.getFileName(filePath)), componentName, componentTag)
    }
    else {
      val stateStorage = getProjectStateStorage(filePath, store, project) ?: return
      val producer = session.getProducer(stateStorage)
      if (producer is DirectoryBasedSaveSessionProducer) {
        producer.setFileState(PathUtil.getFileName(filePath), componentName, componentTag?.children?.first())
      }
      else {
        producer?.setState(null, componentName, componentTag)
      }
    }
  }

  private fun isExternalModuleFile(filePath: String): Boolean {
    val parentPath = PathUtil.getParentPath(filePath)
    return FileUtil.extensionEquals(filePath, "xml") && PathUtil.getFileName(parentPath) == "modules"
           && PathUtil.getFileName(PathUtil.getParentPath(parentPath)) != ".idea"
  }

}

private val MODULE_FILE_STORAGE_ANNOTATION = FileStorageAnnotation(StoragePathMacros.MODULE_FILE, false)

private class ProjectWithModulesSaveSessionProducerManager(project: Project) : ProjectSaveSessionProducerManager(project) {
  companion object {
    private val NULL_ELEMENT = Element("null")
  }
  private val internalModuleComponents = ConcurrentHashMap<String, ConcurrentHashMap<String, Element?>>()
  private val externalModuleComponents = ConcurrentHashMap<String, ConcurrentHashMap<String, Element?>>()

  fun setModuleComponentState(imlFilePath: String, componentName: String, componentTag: Element?) {
    val componentToElement = internalModuleComponents.computeIfAbsent(imlFilePath) { ConcurrentHashMap() }
    componentToElement[componentName] = componentTag ?: NULL_ELEMENT
  }

  fun setExternalModuleComponentState(moduleFileName: String, componentName: String, componentTag: Element?) {
    val componentToElement = externalModuleComponents.computeIfAbsent(moduleFileName) { ConcurrentHashMap() }
    componentToElement[componentName] = componentTag ?: NULL_ELEMENT
  }

  fun commitComponents(moduleStore: ComponentStoreImpl, moduleSaveSessionManager: SaveSessionProducerManager) {
    fun commitToStorage(storageSpec: Storage, componentToElement: Map<String, Element?>) {
      val storage = moduleStore.storageManager.getStateStorage(storageSpec)
      val producer = moduleSaveSessionManager.getProducer(storage)
      if (producer != null) {
        componentToElement.forEach { (componentName, componentTag) ->
          producer.setState(null, componentName, if (componentTag === NULL_ELEMENT) null else componentTag)
        }
      }
    }

    val moduleFilePath = moduleStore.storageManager.expandMacros(StoragePathMacros.MODULE_FILE)
    val internalComponents = internalModuleComponents[moduleFilePath]
    if (internalComponents != null) {
      commitToStorage(MODULE_FILE_STORAGE_ANNOTATION, internalComponents)
    }
    
    val moduleFileName = FileUtil.getNameWithoutExtension(PathUtil.getFileName(moduleFilePath))
    val externalComponents = externalModuleComponents[moduleFileName]
    if (externalComponents != null) {
      val providerFactory = StreamProviderFactory.EP_NAME.getExtensionList(project).firstOrNull()
      if (providerFactory != null) {
        val storageSpec = providerFactory.getOrCreateStorageSpec(StoragePathMacros.MODULE_FILE)
        commitToStorage(storageSpec, externalComponents)
      }
    }
  }
}