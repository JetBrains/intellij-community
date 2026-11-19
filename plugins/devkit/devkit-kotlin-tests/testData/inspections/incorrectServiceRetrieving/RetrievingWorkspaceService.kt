@file:Suppress("NO_REFLECTION_IN_CLASS_PATH")

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.components.serviceIfCreated
import com.intellij.openapi.components.serviceOrNull
import com.intellij.openapi.project.Project
import com.intellij.openapi.workspace.ProjectWorkspace

@Service(Service.Level.WORKSPACE)
class WorkspaceService

fun fail(project: Project) {
  <error descr="The workspace-level service is retrieved as an application-level service">service</error><WorkspaceService>()
  ApplicationManager.getApplication().<error descr="The workspace-level service is retrieved as an application-level service">getService</error>(WorkspaceService::class.java)
  project.<error descr="The workspace-level service is retrieved as a project-level service">service</error><WorkspaceService>()
  project.<error descr="The workspace-level service is retrieved as a project-level service">getService</error>(WorkspaceService::class.java)
}

fun success(workspace: ProjectWorkspace) {
  workspace.getService(WorkspaceService::class.java)
  workspace.service<WorkspaceService>()
  workspace.serviceOrNull<WorkspaceService>()
  workspace.serviceIfCreated<WorkspaceService>()
  workspace.getServiceIfCreated(WorkspaceService::class.java)
}
