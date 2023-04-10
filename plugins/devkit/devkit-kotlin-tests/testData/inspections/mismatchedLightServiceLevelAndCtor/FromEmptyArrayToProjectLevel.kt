import com.intellij.openapi.components.Service
import com.intellij.openapi.project.Project

<warning descr="If constructor takes Project, Service.Level.PROJECT is required">@Service<caret>(*[])</warning>
class MyService(val project: Project)