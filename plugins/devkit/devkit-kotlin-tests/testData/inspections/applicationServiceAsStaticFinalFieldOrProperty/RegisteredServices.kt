import serviceDeclarations.RegisteredApplicationService
import serviceDeclarations.RegisteredProjectService

// -------- top-level declarations ---------
val <warning descr="Application service must not be assigned to a static immutable property with a backing field">myAppService1</warning> = RegisteredApplicationService.getInstance()

val <warning descr="Application service must not be assigned to a static immutable property with a backing field">myAppService2</warning> = RegisteredApplicationService.getInstance()

// non-final
var myAppService3 = RegisteredApplicationService.getInstance()

// not an application service
val myProjectService = RegisteredProjectService.getInstance()


// -------- companion object declarations ---------
// parameters are considered as UField but not static
class MyClass(val appService: RegisteredApplicationService) {
  // not static
  val myAppService = RegisteredApplicationService.getInstance()

  companion object {
    val <warning descr="Application service must not be assigned to a static immutable property with a backing field">myAppService1</warning> = RegisteredApplicationService.getInstance()

    val <warning descr="Application service must not be assigned to a static immutable property with a backing field">myAppService2</warning> = RegisteredApplicationService.getInstance()

    // non-final
    var myAppService3 = RegisteredApplicationService.getInstance()

    // not an application service
    val myProjectService = RegisteredProjectService.getInstance()
  }

}

// -------- object declarations ---------

object MyObject {
  val <warning descr="Application service must not be assigned to a static immutable property with a backing field">myAppService1</warning> = RegisteredApplicationService.getInstance()

  val <warning descr="Application service must not be assigned to a static immutable property with a backing field">myAppService2</warning> = RegisteredApplicationService.getInstance()

  // non-final
  var myAppService3 = RegisteredApplicationService.getInstance()

  // not an application service
  val myProjectService = RegisteredProjectService.getInstance()
}

