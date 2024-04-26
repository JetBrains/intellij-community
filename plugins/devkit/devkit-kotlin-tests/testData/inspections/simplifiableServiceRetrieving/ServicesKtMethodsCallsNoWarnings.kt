@file:Suppress("NO_REFLECTION_IN_CLASS_PATH")

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.serviceIfCreated
import com.intellij.openapi.components.serviceOrNull
import com.intellij.openapi.project.Project

@Service(Service.Level.PROJECT)
class MyProjectService {
  companion object {
    @JvmStatic
    fun getInstance(project: Project): MyProjectService {
      return project.getService(MyProjectService::class.java)
    }
  }

  fun test(project: Project) {
    project.serviceIfCreated<MyProjectService>()
    project.serviceOrNull<MyProjectService>()
  }
}

@Service
class MyAppService {
  companion object {
    @JvmStatic
    fun getInstance(): MyAppService {
      return ApplicationManager.getApplication().getService(MyAppService::class.java)
    }
  }

  fun test() {
    ApplicationManager.getApplication().serviceIfCreated<MyAppService>()
    ApplicationManager.getApplication().serviceOrNull<MyAppService>()
  }
}