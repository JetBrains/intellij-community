@file:Suppress("NO_REFLECTION_IN_CLASS_PATH")

import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.components.serviceIfCreated
import com.intellij.openapi.components.serviceOrNull
import com.intellij.openapi.project.Project

@Service
class ApplicationService

@Service(Service.Level.PROJECT)
class ProjectService

fun fail(project: Project) {
  project.<error descr="The application-level service is retrieved as a project-level service">getService</error>(ApplicationService::class.java)
  project.<error descr="The application-level service is retrieved as a project-level service">service</error><ApplicationService>()
  project.<error descr="The application-level service is retrieved as a project-level service">serviceOrNull</error><ApplicationService>()
  project.<error descr="The application-level service is retrieved as a project-level service">serviceIfCreated</error><ApplicationService>()
  project.<error descr="The application-level service is retrieved as a project-level service">getServiceIfCreated</error>(ApplicationService::class.java)
}

fun success(project: Project) {
  project.getService(ProjectService::class.java)
  project.service<ProjectService>()
  project.serviceOrNull<ProjectService>()
  project.serviceIfCreated<ProjectService>()
  project.getServiceIfCreated(ProjectService::class.java)
}