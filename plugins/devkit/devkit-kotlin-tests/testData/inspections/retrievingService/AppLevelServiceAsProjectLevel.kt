import com.intellij.openapi.components.Service
import com.intellij.openapi.project.Project

@Service(Service.Level.APP)
class MyService {
  @Suppress("NO_REFLECTION_IN_CLASS_PATH")
  fun foo(project: Project) {
    <error descr="The application-level service is retrieved as a project-level service">project.getService(MyService::class.java)</error>
  }
}
