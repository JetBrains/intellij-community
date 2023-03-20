import com.intellij.openapi.components.Service
import com.intellij.openapi.project.Project

<warning descr="If constructor takes Project, Service.Level.PROJECT is required">@Service(Service.Level.APP)</warning>
class <warning descr="Application level service requires no-arg constructor or constructor taking Coroutine">MyService<caret></warning>(val project: Project)