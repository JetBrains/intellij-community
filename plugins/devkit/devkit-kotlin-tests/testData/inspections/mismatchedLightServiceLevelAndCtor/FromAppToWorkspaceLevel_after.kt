import com.intellij.openapi.components.Service
import com.intellij.openapi.workspace.ProjectWorkspace

@Service(Service.Level.WORKSPACE)
class MyService(val workspace: ProjectWorkspace)