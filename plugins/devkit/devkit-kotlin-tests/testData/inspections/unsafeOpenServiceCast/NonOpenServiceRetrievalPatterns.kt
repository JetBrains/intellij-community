package com.example

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.project.Project

interface MyClosedService {
  fun doSomething()
}

class MyClosedServiceImpl : MyClosedService {
  override fun doSomething() {}
  fun implOnly() {}
}

class ServiceHolder {
  val myClosedService: MyClosedService get() = MyClosedServiceImpl()
}

@Suppress("USELESS_CAST", "USELESS_IS_CHECK", "NO_REFLECTION_IN_CLASS_PATH")
class Consumer {
  // Cast from parameter - OK for non-open service
  fun castGetInstance(service: MyClosedService) {
    val impl = service as MyClosedServiceImpl
  }

  // Cast in method call chain - OK for non-open service
  fun castInMethodChain(service: MyClosedService) {
    (service as MyClosedServiceImpl).implOnly()
  }

  // Cast of property access - OK for non-open service
  fun castPropertyAccess(holder: ServiceHolder) {
    val impl = holder.myClosedService as MyClosedServiceImpl
  }

  // Cast from getService() - OK for non-open service
  fun castFromGetService(project: Project) {
    val impl = project.getService(MyClosedService::class.java) as MyClosedServiceImpl
  }

  // Cast from Application.getService() - OK for non-open service
  fun castFromApplicationGetService() {
    val impl = ApplicationManager.getApplication().getService(MyClosedService::class.java) as MyClosedServiceImpl
  }

  // Nullable cast - OK for non-open service
  fun nullableCast(service: MyClosedService) {
    val impl = service as MyClosedServiceImpl?
  }

  // Safe cast (as?) - OK
  fun safeCast(service: MyClosedService) {
    val impl = service as? MyClosedServiceImpl
  }

  // Cast from parameter - OK for non-open service
  fun castFromParameter(service: MyClosedService) {
    val impl = service as MyClosedServiceImpl
  }

  // Cast to same type - OK
  fun castToSameType(service: MyClosedService) {
    val svc = service as MyClosedService
  }

  // Type check with 'is' - OK
  fun typeCheck(service: MyClosedService): Boolean {
    return service is MyClosedServiceImpl
  }
}
