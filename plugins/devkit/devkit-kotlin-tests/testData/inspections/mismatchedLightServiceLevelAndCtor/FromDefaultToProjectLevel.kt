import com.intellij.openapi.components.Service
import com.intellij.openapi.project.Project

<warning descr="Light service with a constructor that takes a single parameter of type 'Project' must specify '@Service(Service.Level.PROJECT)'">@Service<caret></warning>
class MyService(val project: Project)