@file:Suppress("NO_REFLECTION_IN_CLASS_PATH")

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service

@Service(Service.Level.APP)
class LightApplicationService1

@Service(Service.Level.APP)
class LightApplicationService2 {
  companion object {
    val <warning descr="Provide explicit 'getInstance()' method to access application service instead of a property">instance1</warning>: LightApplicationService2
      get() = service()

    val <warning descr="Provide explicit 'getInstance()' method to access application service instead of a property">instance2</warning> = service<LightApplicationService2>()

    val <warning descr="Provide explicit 'getInstance()' method to access application service instead of a property">instance3</warning> = ApplicationManager.getApplication().getService(LightApplicationService2::class.java)

    //  not this service instance but still stores a service with a backing field
    val <warning descr="Application service must not be assigned to a static immutable property with a backing field">instance4</warning> = service<LightApplicationService1>()

    // not this service instance + does not have a backing field, so safe to use it this way
    val instance5: LightApplicationService1
      get() = service()
  }

  // not inside a companion object
  val instance6: LightApplicationService2
    get() = service()

  val instance7 = service<LightApplicationService2>()

  object Obj {
    // not inside a companion object
    val instance8: LightApplicationService2
      get() = service()

    val <warning descr="Application service must not be assigned to a static immutable property with a backing field">instance9</warning> = service<LightApplicationService2>()
  }
}
