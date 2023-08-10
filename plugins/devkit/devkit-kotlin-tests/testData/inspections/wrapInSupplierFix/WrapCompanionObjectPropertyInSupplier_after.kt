@file:Suppress("NO_REFLECTION_IN_CLASS_PATH")
package inspections.wrapInSupplierFix

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.CachedSingletonsRegistry
import com.intellij.openapi.components.Service
import inspections.wrapInSupplierFix.MyAnnotation
import java.util.function.Supplier

@Service(Service.Level.APP)
class MyService {

    fun foo() { }

    companion object {

        @MyAnnotation
        val companionObjectAppServiceSupplier1: Supplier<MyService> = CachedSingletonsRegistry.lazy { ApplicationManager.getApplication().getService(MyService::class.java) }


        // to test naming conflicts
        val companionObjectAppServiceSupplier = 0
    }
}
