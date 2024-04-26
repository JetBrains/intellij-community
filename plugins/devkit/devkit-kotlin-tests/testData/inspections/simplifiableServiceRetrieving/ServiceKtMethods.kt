@file:Suppress("UNUSED_VARIABLE")

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service

@Service(Service.Level.APP)
class MyAppService {
  companion object {
    @JvmStatic
    fun getInstance(): MyAppService = service()
  }
}

fun test() {
  val myAppService1: MyAppService = <weak_warning descr="Can be replaced with 'MyAppService.getInstance()' call">service</weak_warning>()
  val myAppService2 = <weak_warning descr="Can be replaced with 'MyAppService.getInstance()' call">service</weak_warning><MyAppService>()
  val myAppService3 = ApplicationManager.getApplication().<weak_warning descr="Can be replaced with 'MyAppService.getInstance()' call">getService</weak_warning>(MyAppService::class.<warning descr="[NO_REFLECTION_IN_CLASS_PATH] Call uses reflection API which is not found in compilation classpath. Make sure you have kotlin-reflect.jar in the classpath">java</warning>)
}