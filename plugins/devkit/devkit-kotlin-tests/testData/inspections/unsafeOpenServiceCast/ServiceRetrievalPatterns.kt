package com.example

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.project.Project

interface MyService {
  fun doSomething()
}

class MyServiceImpl : MyService {
  override fun doSomething() {}
  fun implOnly() {}
}

class ServiceHolder {
  val myService: MyService get() = MyServiceImpl()
}

@Suppress("USELESS_CAST", "USELESS_IS_CHECK", "NO_REFLECTION_IN_CLASS_PATH")
class Consumer {
  // Cast of getInstance() result - should warn
  fun castGetInstance(service: MyService) {
    val impl = <warning descr="Casting open service 'MyService' to a specific subclass is unsafe: the actual implementation may differ at runtime">service as MyServiceImpl</warning>
  }

  // Cast in method call chain - should warn
  fun castInMethodChain(service: MyService) {
    (<warning descr="Casting open service 'MyService' to a specific subclass is unsafe: the actual implementation may differ at runtime">service as MyServiceImpl</warning>).implOnly()
  }

  // Cast of property access - should warn
  fun castPropertyAccess(holder: ServiceHolder) {
    val impl = <warning descr="Casting open service 'MyService' to a specific subclass is unsafe: the actual implementation may differ at runtime">holder.myService as MyServiceImpl</warning>
  }

  // Cast from getService() - should warn
  fun castFromGetService(project: Project) {
    val impl = <warning descr="Casting open service 'MyService' to a specific subclass is unsafe: the actual implementation may differ at runtime">project.getService(MyService::class.java) as MyServiceImpl</warning>
  }

  // Cast from Application.getService() - should warn
  fun castFromApplicationGetService() {
    val impl = <warning descr="Casting open service 'MyService' to a specific subclass is unsafe: the actual implementation may differ at runtime">ApplicationManager.getApplication().getService(MyService::class.java) as MyServiceImpl</warning>
  }

  // Nullable cast - should warn (still unsafe, just may return null)
  fun nullableCast(service: MyService) {
    val impl = <warning descr="Casting open service 'MyService' to a specific subclass is unsafe: the actual implementation may differ at runtime">service as MyServiceImpl?</warning>
  }

  // Safe cast (as?) - OK, won't crash
  fun safeCast(service: MyService) {
    val impl = service as? MyServiceImpl
  }

  // Cast to same type - OK
  fun castToSameType(service: MyService) {
    val svc = service as MyService
  }

  // Type check with 'is' - OK (doesn't crash, just returns boolean)
  fun typeCheck(service: MyService): Boolean {
    return service is MyServiceImpl
  }
}
