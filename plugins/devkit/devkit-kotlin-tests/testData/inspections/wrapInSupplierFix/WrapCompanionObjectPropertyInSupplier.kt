@file:Suppress("NO_REFLECTION_IN_CLASS_PATH")
package inspections.wrapInSupplierFix

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.Service
import inspections.wrapInSupplierFix.MyAnnotation

@Service(Service.Level.APP)
class MyService {

    fun foo() { }

    companion object {

        @MyAnnotation
        val <warning descr="Provide explicit 'getInstance()' method to access application service instead of a property">companionObjectAppService<caret></warning> = ApplicationManager.getApplication().getService(MyService::class.java)


        // to test naming conflicts
        val companionObjectAppServiceSupplier = 0
    }
}
