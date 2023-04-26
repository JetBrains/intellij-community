import serviceDeclarations.RegisteredApplicationService
import serviceDeclarations.RegisteredProjectService

<warning descr="Application service must not be assigned to a static final field">// -------- top-level declarations ---------
val myAppService1 = RegisteredApplicationService.getInstance()</warning>

<warning descr="Application service must not be assigned to a static final field">val myAppService2 = RegisteredApplicationService.getInstance()</warning>

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
    <warning descr="Application service must not be assigned to a static final field">val myAppService1 = RegisteredApplicationService.getInstance()</warning>

    <warning descr="Application service must not be assigned to a static final field">val myAppService2 = RegisteredApplicationService.getInstance()</warning>

    // non-final
    var myAppService3 = RegisteredApplicationService.getInstance()

    // not an application service
    val myProjectService = RegisteredProjectService.getInstance()
  }

}

// -------- object declarations ---------

object MyObject {
  <warning descr="Application service must not be assigned to a static final field">val myAppService1 = RegisteredApplicationService.getInstance()</warning>

  <warning descr="Application service must not be assigned to a static final field">val myAppService2 = RegisteredApplicationService.getInstance()</warning>

  // non-final
  var myAppService3 = RegisteredApplicationService.getInstance()

  // not an application service
  val myProjectService = RegisteredProjectService.getInstance()
}

