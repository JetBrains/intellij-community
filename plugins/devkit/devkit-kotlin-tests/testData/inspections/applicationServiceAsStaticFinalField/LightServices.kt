import serviceDeclarations.*

// -------- top-level declarations ---------

val <warning descr="Application service must not be assigned to a static final field">myAppService1</warning> = LightServiceAppAndProjectLevelAnnotation.getInstance()

val <warning descr="Application service must not be assigned to a static final field">myAppService2</warning> = LightServiceAppLevelAnnotation.getInstance()

val <warning descr="Application service must not be assigned to a static final field">myAppService4</warning> = LightServiceEmptyAnnotation.getInstance()

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
    val <warning descr="Application service must not be assigned to a static final field">myAppService1</warning> = LightServiceAppAndProjectLevelAnnotation.getInstance()

    val <warning descr="Application service must not be assigned to a static final field">myAppService2</warning> = LightServiceAppLevelAnnotation.getInstance()

    val <warning descr="Application service must not be assigned to a static final field">myAppService4</warning> = LightServiceEmptyAnnotation.getInstance()

    // non final
    var myAppService5 = LightServiceAppAndProjectLevelAnnotation.getInstance()

    // not an application service
    val myProjectService = LightServiceProjecLevelAnnotation.getInstance()
  }

}

// -------- object declarations ---------

object MyObject {
  val <warning descr="Application service must not be assigned to a static final field">myAppService1</warning> = LightServiceAppAndProjectLevelAnnotation.getInstance()

  val <warning descr="Application service must not be assigned to a static final field">myAppService2</warning> = LightServiceAppLevelAnnotation.getInstance()

  val <warning descr="Application service must not be assigned to a static final field">myAppService4</warning> = LightServiceEmptyAnnotation.getInstance()

  // non final
  var myAppService5 = LightServiceAppAndProjectLevelAnnotation.getInstance()

  // not an application service
  val myProjectService = LightServiceProjecLevelAnnotation.getInstance()
}


