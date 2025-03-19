@file:Suppress("NO_REFLECTION_IN_CLASS_PATH")

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.components.serviceIfCreated
import com.intellij.openapi.components.serviceOrNull
import com.intellij.openapi.project.Project

class UnregisteredService

@Service
class ApplicationService

@Service(Service.Level.PROJECT)
class ProjectService

fun main() {
  fun unregisteredService(project: Project) {
    project.<error descr="The 'UnregisteredService' class is not registered as a service">getService</error>(UnregisteredService::class.java)
    project.<error descr="The 'UnregisteredService' class is not registered as a service">service</error><UnregisteredService>()
    project.<error descr="The 'UnregisteredService' class is not registered as a service">serviceOrNull</error><UnregisteredService>()
    project.<error descr="The 'UnregisteredService' class is not registered as a service">serviceIfCreated</error><UnregisteredService>()

    <error descr="The 'UnregisteredService' class is not registered as a service">service</error><UnregisteredService>()
    <error descr="The 'UnregisteredService' class is not registered as a service">serviceOrNull</error><UnregisteredService>()
    <error descr="The 'UnregisteredService' class is not registered as a service">serviceIfCreated</error><UnregisteredService>()

    ApplicationManager.getApplication().<error descr="The 'UnregisteredService' class is not registered as a service">getService</error>(UnregisteredService::class.java)
  }

  fun lightServices(project: Project) {
    project.getService(ProjectService::class.java)
    project.service<ProjectService>()
    project.serviceOrNull<ProjectService>()
    project.serviceIfCreated<ProjectService>()

    service<ApplicationService>()
    serviceOrNull<ApplicationService>()
    serviceIfCreated<ApplicationService>()

    ApplicationManager.getApplication().getService(ApplicationService::class.java)
  }
}
