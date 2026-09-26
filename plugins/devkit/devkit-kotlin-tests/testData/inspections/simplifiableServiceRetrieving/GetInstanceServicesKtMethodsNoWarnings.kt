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
    fun getInstanceIfCreated(project: Project): MyProjectService? {
      return project.serviceIfCreated<MyProjectService>()
    }

    @JvmStatic
    fun getInstanceOrNull(project: Project): MyProjectService? {
      return project.serviceOrNull()
    }
  }

  fun test(project: Project) {
    project.getService(MyProjectService::class.java)
  }
}

@Service
class MyAppService {
  companion object {
    @JvmStatic
    fun getInstanceIfCreated(): MyAppService? {
      return ApplicationManager.getApplication().serviceIfCreated()
    }

    @JvmStatic
    fun getInstanceOrNull(): MyAppService? {
      return ApplicationManager.getApplication().serviceOrNull<MyAppService>()
    }
  }

  fun test() {
    ApplicationManager.getApplication().getService(MyAppService::class.java)
  }
}