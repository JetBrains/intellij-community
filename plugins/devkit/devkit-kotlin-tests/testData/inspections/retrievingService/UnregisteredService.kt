import com.intellij.openapi.components.Service
import com.intellij.openapi.project.Project

class MyService {
  @Suppress("NO_REFLECTION_IN_CLASS_PATH")
  fun foo(project: Project) {
    project.<error descr="The 'MyService' class is not registered as a service">getService</error>(MyService::class.java)
  }
}
