@file:Suppress("NO_REFLECTION_IN_CLASS_PATH")

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.Service

@Service(Service.Level.APP)
class MyAppService {
    companion object {
        @JvmStatic
        @JvmName("jvmMethodName")
        fun getInstance(): MyAppService = ApplicationManager.getApplication().getService(MyAppService::class.java)
    }
}

fun test() {
    ApplicationManager.getApplication().<weak_warning descr="Can be replaced with 'MyAppService.getInstance()' call">get<caret>Service</weak_warning>(MyAppService::class.java)
}