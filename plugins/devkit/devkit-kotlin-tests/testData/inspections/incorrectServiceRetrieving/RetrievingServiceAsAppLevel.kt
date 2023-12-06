@file:Suppress("NO_REFLECTION_IN_CLASS_PATH")

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.components.serviceIfCreated
import com.intellij.openapi.components.serviceOrNull

@Service
class ApplicationService

@Service(Service.Level.PROJECT)
class ProjectService

fun fail() {
  <error descr="The project-level service is retrieved as an application-level service">service</error><ProjectService>()
  <error descr="The project-level service is retrieved as an application-level service">serviceOrNull</error><ProjectService>()
  <error descr="The project-level service is retrieved as an application-level service">serviceIfCreated</error><ProjectService>()

  ApplicationManager.getApplication().<error descr="The project-level service is retrieved as an application-level service">getService</error>(ProjectService::class.java)
}

fun success() {
  service<ApplicationService>()
  serviceOrNull<ApplicationService>()
  serviceIfCreated<ApplicationService>()

  ApplicationManager.getApplication().getService(ApplicationService::class.java)
}
