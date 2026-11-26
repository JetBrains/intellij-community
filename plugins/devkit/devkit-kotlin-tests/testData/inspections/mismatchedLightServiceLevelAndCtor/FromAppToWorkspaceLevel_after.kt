import com.intellij.openapi.components.Service
import com.intellij.openapi.workspace.Workspace

@Service(Service.Level.WORKSPACE)
class MyService(val workspace: Workspace)