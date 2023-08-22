@file:Suppress("UNUSED_VARIABLE")

import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service

import serviceDeclarations.RegisteredApplicationService

// static property with backing field
val <warning descr="Application service must not be assigned to a static immutable property with a backing field">service1</warning> = RegisteredApplicationService.getInstance()

// static property without backing field (but not an instance, so no warning)
val service2: RegisteredApplicationService
  get() = RegisteredApplicationService.getInstance()

interface MyInterface {
  fun bar()
}


// non-static property
@Service(Service.Level.APP)
class AppService(val service3: RegisteredApplicationService = RegisteredApplicationService.getInstance()) {

  // non-static property with backing field
  val service4 = RegisteredApplicationService.getInstance()

  // non-static property without backing field
  val service5: RegisteredApplicationService
    get() = RegisteredApplicationService.getInstance()

  companion object {
    // static property with backing field
    val <warning descr="Application service must not be assigned to a static immutable property with a backing field">service6</warning> = RegisteredApplicationService.getInstance()

    // static property without backing field, but being an instance
    val <warning descr="An explicit 'getInstance()' method should be used to retrieve an application service instead of a property">service7</warning>: AppService
      get() = service<AppService>()


    val a = {
      // non-static property inside a companion object
      val service8 = RegisteredApplicationService.getInstance()
    }

    fun foo() {
      // non-static property inside a companion object
      val service9 = RegisteredApplicationService.getInstance()
    }

  }

  object O {
    // static property with backing field
    val <warning descr="Application service must not be assigned to a static immutable property with a backing field">service10</warning> = RegisteredApplicationService.getInstance()

    // static property without backing field (but not an instance)
    val service11: AppService
      get() = service<AppService>()


    val a = {
      // non-static property inside an object
      val service12 = RegisteredApplicationService.getInstance()
    }

    fun foo() {
      // non-static property inside an object
      val service13 = RegisteredApplicationService.getInstance()

      val ojb = object : MyInterface {
        // static property with backing field
        val <warning descr="Application service must not be assigned to a static immutable property with a backing field">service10</warning> = RegisteredApplicationService.getInstance()

        override fun bar() { }
      }
    }

    class A {
      // non-static property inside an object
      val a = RegisteredApplicationService.getInstance()
    }

  }

}