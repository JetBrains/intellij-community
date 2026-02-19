import serviceDeclarations.RegisteredApplicationService


// -------- top-level declarations ---------
// no backing fields
val myAppService1: RegisteredApplicationService
  get() = RegisteredApplicationService.getInstance()

// with a backing field
val <warning descr="Application service must not be assigned to a static immutable property with a backing field">myAppService2</warning>: RegisteredApplicationService = RegisteredApplicationService.getInstance()

// -------- companion object declarations ---------
class MyClass {

  companion object {
    // no backing fields
    val myAppService1: RegisteredApplicationService
      get() = RegisteredApplicationService.getInstance()

    // with a backing field
    val <warning descr="Application service must not be assigned to a static immutable property with a backing field">myAppService2</warning>: RegisteredApplicationService = RegisteredApplicationService.getInstance()

  }

}

// -------- object declarations ---------

object MyObject {
  // no backing fields
  val myAppService1: RegisteredApplicationService
    get() = RegisteredApplicationService.getInstance()

  // with a backing field
  val <warning descr="Application service must not be assigned to a static immutable property with a backing field">myAppService2</warning>: RegisteredApplicationService = RegisteredApplicationService.getInstance()
}
