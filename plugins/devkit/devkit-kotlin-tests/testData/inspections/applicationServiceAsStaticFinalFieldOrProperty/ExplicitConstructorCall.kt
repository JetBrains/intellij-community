import serviceDeclarations.RegisteredApplicationService

// -------- top-level declarations ---------
// explicit constructor call
val myAppService1 = RegisteredApplicationService()

val <warning descr="Application service must not be assigned to a static immutable property with a backing field">myAppService2</warning> = RegisteredApplicationService.getInstance()


// -------- companion object declarations ---------
class MyClass {

  companion object {
    // explicit constructor call
    val myAppService1 = RegisteredApplicationService()

    val <warning descr="Application service must not be assigned to a static immutable property with a backing field">myAppService2</warning> = RegisteredApplicationService.getInstance()
  }

}

// -------- object declarations ---------

object MyObject {
  // explicit constructor call
  val myAppService1 = RegisteredApplicationService()

  val <warning descr="Application service must not be assigned to a static immutable property with a backing field">myAppService2</warning> = RegisteredApplicationService.getInstance()

}