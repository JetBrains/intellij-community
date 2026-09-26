@file:Suppress("NO_REFLECTION_IN_CLASS_PATH")

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.Service

@Service(Service.Level.APP)
class MyAppService<T: Any> {
    companion object {
        @JvmStatic
        fun getInstance(): MyAppService<*> = ApplicationManager.getApplication().getService(MyAppService::class.java)
    }
}

fun test() {
    MyAppService.getInstance()
}