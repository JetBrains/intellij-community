@file:Suppress("NO_REFLECTION_IN_CLASS_PATH")

import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.workspace.ProjectWorkspace

@Service
class ApplicationService

@Service(Service.Level.PROJECT)
class ProjectService

fun fail(workspace: ProjectWorkspace) {
  workspace.<error descr="The application-level service is retrieved as a workspace-level service">service</error><ApplicationService>()
  workspace.<error descr="The application-level service is retrieved as a workspace-level service">getService</error>(ApplicationService::class.java)
  workspace.<error descr="The project-level service is retrieved as a workspace-level service">service</error><ProjectService>()
  workspace.<error descr="The project-level service is retrieved as a workspace-level service">getService</error>(ProjectService::class.java)
}
