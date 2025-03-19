@file:Suppress("NO_REFLECTION_IN_CLASS_PATH")

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service

import serviceDeclarations.RegisteredApplicationService


@Service
class ServiceClass

class DoesNotMatter {
  companion object {
    val <warning descr="Application service must not be assigned to a static immutable property with a backing field">instance1</warning> = service<ServiceClass>()

    val <warning descr="Application service must not be assigned to a static immutable property with a backing field">instance2</warning> = ApplicationManager.getApplication().getService(ServiceClass::class.java)

    val <warning descr="Application service must not be assigned to a static immutable property with a backing field">instance3</warning>: ServiceClass = service()
  }

}
