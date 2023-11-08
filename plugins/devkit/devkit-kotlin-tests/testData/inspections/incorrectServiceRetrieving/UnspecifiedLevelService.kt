@file:Suppress("NO_REFLECTION_IN_CLASS_PATH")

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.components.serviceIfCreated
import com.intellij.openapi.components.serviceOrNull
import com.intellij.openapi.project.Project

@Service
class MyService

fun test(project: Project) {
  service<MyService>()
  serviceOrNull<MyService>()
  serviceIfCreated<MyService>()

  ApplicationManager.getApplication().getService(MyService::class.java)
  ApplicationManager.getApplication().getService(MyService::class.java, true)

  project.getService(ProjectService::class.java)
  project.service<ProjectService>()
  project.serviceOrNull<ProjectService>()
  project.serviceIfCreated<ProjectService>()
}
