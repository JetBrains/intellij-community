@file:Suppress("NO_REFLECTION_IN_CLASS_PATH")

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project

@Service(Service.Level.APP)
class MyAppService {
  companion object {
    @JvmStatic
    fun getInstance(): MyAppService = ApplicationManager.getApplication().getService(MyAppService::class.java)
  }
}

@Service(Service.Level.PROJECT)
class MyProjectService {
  companion object {
    @JvmStatic
    fun getInstance(project: Project): MyProjectService = project.getService(MyProjectService::class.java)
  }
}

fun test(project: Project?) {
  ApplicationManager.getApplication()?.getService(MyAppService::class.java)
  ApplicationManager.getApplication()?.service<MyAppService>()
  project?.getService(MyProjectService::class.java)
  project?.service<MyProjectService>()
}