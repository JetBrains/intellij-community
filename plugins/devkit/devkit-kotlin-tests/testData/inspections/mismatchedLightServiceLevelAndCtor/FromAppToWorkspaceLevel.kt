import com.intellij.openapi.components.Service
import com.intellij.openapi.workspace.Workspace

<warning descr="Light service with a constructor that takes a parameter of type 'Workspace' must specify '@Service(Service.Level.WORKSPACE)'">@Service<caret>(Service.Level.APP)</warning>
class <warning descr="Application-level service requires a no-arg or single parameter constructor with 'kotlinx.coroutines.CoroutineScope' type">MyService</warning>(val workspace: Workspace)