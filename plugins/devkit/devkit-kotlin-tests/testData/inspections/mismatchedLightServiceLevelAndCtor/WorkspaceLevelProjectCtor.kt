import com.intellij.openapi.components.Service
import com.intellij.openapi.project.Project

<warning descr="Light service with a constructor that takes a parameter of type 'Project' must specify '@Service(Service.Level.PROJECT)'">@Service(Service.Level.WORKSPACE)</warning>
class <warning descr="Workspace-level service requires a no-arg constructor or a constructor with 'Workspace' and 'kotlinx.coroutines.CoroutineScope' parameters">MyService</warning>(val project: Project)