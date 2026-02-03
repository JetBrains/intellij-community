@file:Suppress("NO_REFLECTION_IN_CLASS_PATH")

import com.intellij.openapi.components.Service
import com.intellij.openapi.project.Project

internal interface MyService

@Service(Service.Level.PROJECT)
internal class MyServiceImpl : MyService {
  companion object {
    @JvmStatic
    fun getInstance(project: Project): MyService {
      return project.getService(MyServiceImpl::class.java)
    }
  }


  fun foo() {}

  fun foo(project: Project) {
    project.getService(MyServiceImpl::class.java).foo()
  }
}