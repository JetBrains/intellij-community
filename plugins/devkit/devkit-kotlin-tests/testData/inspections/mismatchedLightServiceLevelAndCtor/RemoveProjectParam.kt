import com.intellij.openapi.components.Service
import com.intellij.openapi.project.Project

@Service(Service.Level.APP)
class <warning descr="Application level service requires no-arg constructor or constructor taking Coroutine"><warning descr="If constructor takes Project, Service.Level.PROJECT is required">MyService<caret></warning></warning>(val project: Project)