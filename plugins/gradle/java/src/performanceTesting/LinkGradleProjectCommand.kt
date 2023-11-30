package org.jetbrains.plugins.gradle.performanceTesting

import com.intellij.ide.actions.ImportModuleAction
import com.intellij.ide.actions.ImportModuleAction.Companion.doImport
import com.intellij.openapi.application.EDT
import com.intellij.openapi.externalSystem.service.project.wizard.AbstractExternalProjectImportProvider
import com.intellij.openapi.ui.playback.PlaybackContext
import com.intellij.projectImport.ProjectImportProvider
import com.jetbrains.performancePlugin.commands.OpenFileCommand.Companion.findFile
import com.jetbrains.performancePlugin.commands.PerformanceCommandCoroutineAdapter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jetbrains.plugins.gradle.util.GradleConstants


/**
 * The command links a gradle project by project_path
 * Syntax: %linkGradleProject [project_path]
 * Example: %linkGradleProject /opt/user/gradle-projects/gradle-project-with-sources
 */
class LinkGradleProjectCommand(text: String, line: Int) : PerformanceCommandCoroutineAdapter(text, line) {

  companion object {
    const val NAME = "linkGradleProject"
    const val PREFIX = "$CMD_PREFIX$NAME"
  }

  override suspend fun doExecute(context: PlaybackContext) {
    val project = context.project

    val filePath = extractCommandArgument(PREFIX)
    val projectBuildFile = findFile(extractCommandArgument(PREFIX), project)

    if (projectBuildFile == null) {
      throw IllegalArgumentException("File not found: $filePath")
    }

    val projectImportProvider = ProjectImportProvider.PROJECT_IMPORT_PROVIDER
      .findFirstSafe { it: ProjectImportProvider? ->
        it is AbstractExternalProjectImportProvider && GradleConstants.SYSTEM_ID == it.externalSystemId
      }!!

    withContext(Dispatchers.EDT) {
      doImport(project) {
        ImportModuleAction.createImportWizard(project, null, projectBuildFile, projectImportProvider)
      }
    }
  }

  override fun getName(): String {
    return NAME
  }
}