import com.intellij.openapi.components.Service
import com.intellij.openapi.project.Project

@Service(Service.Level.APP)
class MyService {
  fun foo(project: Project, clazz: Class<MyService>) {
    <warning descr="The application-level service is retrieved as a project-level service">project.getService(clazz)</warning>
  }
}
