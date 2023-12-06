@file:Suppress("NO_REFLECTION_IN_CLASS_PATH")
package inspections.wrapInSupplierFix

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.Service
import inspections.wrapInSupplierFix.MyAnnotation

@Service(Service.Level.APP)
class MyService {
    fun foo() { }
}

object MyObject {
    @MyAnnotation
    val <warning descr="Application service must not be assigned to a static immutable property with a backing field">objectAppService<caret></warning> = ApplicationManager.getApplication().getService(MyService::class.java)

    // to test naming conflicts
    val objectAppServiceSupplier = 0
}