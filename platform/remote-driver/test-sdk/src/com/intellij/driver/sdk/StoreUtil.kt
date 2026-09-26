package com.intellij.driver.sdk

import com.intellij.driver.client.Driver
import com.intellij.driver.client.Remote
import com.intellij.driver.client.utility
import com.intellij.driver.model.OnDispatcher
import com.intellij.driver.model.RdTarget
import com.intellij.tools.ide.util.common.logOutput

@Remote("com.intellij.openapi.fileEditor.FileDocumentManager")
interface FileDocumentManager {
  fun getInstance(): FileDocumentManager
  fun saveAllDocuments()
}

@Remote("com.intellij.configurationStore.StoreUtil")
interface StoreUtil {
  fun saveSettings(componentManager: ComponentManager, forceSavingAllSettings: Boolean)
  fun reloadChangedSettings(componentManager: ComponentManager, changedFileSpecs: List<String>, deletedFileSpecs: List<String>)
}

fun Driver.saveAllDocuments() {
  logOutput("Saving all documents")
  withContext(OnDispatcher.EDT) {
    withWriteAction {
      utility<FileDocumentManager>(RdTarget.FRONTEND).getInstance().saveAllDocuments()
    }
  }
}

fun Driver.saveSettings(
  forceSavingAllSettings: Boolean = false,
  rdTargets: Collection<RdTarget> = listOf(RdTarget.DEFAULT),
) {
  logOutput("Saving settings with forceSavingAllSettings=$forceSavingAllSettings")
  withContext(OnDispatcher.DEFAULT) {
    rdTargets.forEach { rdTarget ->
      val storeUtil = utility<StoreUtil>(rdTarget)
      storeUtil.saveSettings(application(rdTarget), forceSavingAllSettings)
      getOpenProjects(rdTarget).forEach { project ->
        saveProjectSettings(storeUtil, project, forceSavingAllSettings)
      }
    }
  }
}

/**
 * Saves the settings of one project, and skips a project that closes during its own save.
 *
 * The open project list is a snapshot, so a project can close before its turn comes. In Remote Development the
 * frontend closes its project through the RD protocol, after the backend closes it, so the close arrives with no
 * warning. A project that is still open keeps its failure.
 */
private fun saveProjectSettings(storeUtil: StoreUtil, project: Project, forceSavingAllSettings: Boolean) {
  try {
    storeUtil.saveSettings(project, forceSavingAllSettings)
  }
  catch (failure: Throwable) {
    if (runCatching { project.isOpen() }.getOrDefault(false)) {
      throw failure
    }
    logOutput("Skipped the settings save of a project that closed during the save")
  }
}

fun Driver.reloadChangedSettings(
  changedFileSpecs: Collection<String>,
  deletedFileSpecs: Collection<String> = emptySet(),
  rdTargets: Collection<RdTarget> = listOf(RdTarget.DEFAULT),
) {
  if (changedFileSpecs.isEmpty() && deletedFileSpecs.isEmpty()) {
    return
  }

  val changedSpecs = changedFileSpecs.toList()
  val deletedSpecs = deletedFileSpecs.toList()
  val targets = LinkedHashSet(rdTargets)
  withContext(OnDispatcher.DEFAULT) {
    targets.forEach { rdTarget ->
      utility<StoreUtil>(rdTarget).reloadChangedSettings(application(rdTarget), changedSpecs, deletedSpecs)
    }
  }
}
