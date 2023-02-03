package inspections.lightService

import com.intellij.openapi.components.Service
import com.intellij.openapi.project.Project

@Service(*[])
class <warning descr="If constructor takes Project, Service.Level.PROJECT is required">MyService<caret></warning>(val project: Project)