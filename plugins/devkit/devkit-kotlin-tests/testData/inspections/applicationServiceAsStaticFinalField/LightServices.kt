import serviceDeclarations.*

// -------- top-level declarations ---------

<warning descr="Application service must not be assigned to a static final field">val myAppService1 = LightServiceAppAndProjectLevelAnnotation.getInstance()</warning>

<warning descr="Application service must not be assigned to a static final field">val myAppService2 = LightServiceAppLevelAnnotation.getInstance()</warning>

<warning descr="Application service must not be assigned to a static final field">val myAppService3 = LightServiceDefaultAnnotation.getInstance()</warning>

<warning descr="Application service must not be assigned to a static final field">val myAppService4 = LightServiceEmptyAnnotation.getInstance()</warning>

// non final
var myAppService5 = LightServiceAppAndProjectLevelAnnotation.getInstance()

// not an application service
val myProjectService = LightServiceProjecLevelAnnotation.getInstance()


// -------- companion object declarations ---------

// parameters are considered as UField but not static
class MyClass(val appService: LightServiceAppLevelAnnotation) {
  // not static
  val myAppService5 = LightServiceAppAndProjectLevelAnnotation.getInstance()

  companion object {
    <warning descr="Application service must not be assigned to a static final field">val myAppService1 = LightServiceAppAndProjectLevelAnnotation.getInstance()</warning>

    <warning descr="Application service must not be assigned to a static final field">val myAppService2 = LightServiceAppLevelAnnotation.getInstance()</warning>

    <warning descr="Application service must not be assigned to a static final field">val myAppService3 = LightServiceDefaultAnnotation.getInstance()</warning>

    <warning descr="Application service must not be assigned to a static final field">val myAppService4 = LightServiceEmptyAnnotation.getInstance()</warning>

    // non final
    var myAppService5 = LightServiceAppAndProjectLevelAnnotation.getInstance()

    // not an application service
    val myProjectService = LightServiceProjecLevelAnnotation.getInstance()
  }

}

// -------- object declarations ---------

object MyObject {
  <warning descr="Application service must not be assigned to a static final field">val myAppService1 = LightServiceAppAndProjectLevelAnnotation.getInstance()</warning>

  <warning descr="Application service must not be assigned to a static final field">val myAppService2 = LightServiceAppLevelAnnotation.getInstance()</warning>

  <warning descr="Application service must not be assigned to a static final field">val myAppService3 = LightServiceDefaultAnnotation.getInstance()</warning>

  <warning descr="Application service must not be assigned to a static final field">val myAppService4 = LightServiceEmptyAnnotation.getInstance()</warning>

  // non final
  var myAppService5 = LightServiceAppAndProjectLevelAnnotation.getInstance()

  // not an application service
  val myProjectService = LightServiceProjecLevelAnnotation.getInstance()
}


