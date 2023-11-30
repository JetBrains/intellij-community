@file:Suppress("UNUSED_VARIABLE")
import serviceDeclarations.*

// -------- top-level declarations ---------

val <warning descr="Application service must not be assigned to a static immutable property with a backing field">myAppService1</warning> = LightServiceAppAndProjectLevelAnnotation.getInstance()

val <warning descr="Application service must not be assigned to a static immutable property with a backing field">myAppService2</warning> = LightServiceAppLevelAnnotation.getInstance()

val <warning descr="Application service must not be assigned to a static immutable property with a backing field">myAppService4</warning> = LightServiceEmptyAnnotation.getInstance()

// non final
var myAppService5 = LightServiceAppAndProjectLevelAnnotation.getInstance()

// not an application service
val myProjectService = LightServiceProjecLevelAnnotation.getInstance()


// -------- companion object declarations ---------

// parameters are considered as KtProperties but not static
class MyClass(val appService: LightServiceAppLevelAnnotation) {

  companion object {
    val <warning descr="Application service must not be assigned to a static immutable property with a backing field">myAppService1</warning> = LightServiceAppAndProjectLevelAnnotation.getInstance()

    val <warning descr="Application service must not be assigned to a static immutable property with a backing field">myAppService2</warning> = LightServiceAppLevelAnnotation.getInstance()

    val <warning descr="Application service must not be assigned to a static immutable property with a backing field">myAppService4</warning> = LightServiceEmptyAnnotation.getInstance()

    // non final
    var myAppService5 = LightServiceAppAndProjectLevelAnnotation.getInstance()

    // not an application service
    val myProjectService = LightServiceProjecLevelAnnotation.getInstance()
  }

}

// -------- object declarations ---------

object MyObject {
  val <warning descr="Application service must not be assigned to a static immutable property with a backing field">myAppService1</warning> = LightServiceAppAndProjectLevelAnnotation.getInstance()

  val <warning descr="Application service must not be assigned to a static immutable property with a backing field">myAppService2</warning> = LightServiceAppLevelAnnotation.getInstance()

  val <warning descr="Application service must not be assigned to a static immutable property with a backing field">myAppService4</warning> = LightServiceEmptyAnnotation.getInstance()

  // non final
  var myAppService5 = LightServiceAppAndProjectLevelAnnotation.getInstance()

  // not an application service
  val myProjectService = LightServiceProjecLevelAnnotation.getInstance()
}


// -------- local object declarations ---------
interface MyInterface {
  fun foo()
}

fun main() {
  val obj = object : MyInterface {

    // inside an anonymous obect
    val myAppService1 = LightServiceAppAndProjectLevelAnnotation.getInstance()

    // inside an anonymous obect
    val myAppService2 = LightServiceAppLevelAnnotation.getInstance()

    // inside an anonymous obect
    val myAppService4 = LightServiceEmptyAnnotation.getInstance()

    // non final
    var myAppService5 = LightServiceAppAndProjectLevelAnnotation.getInstance()

    // not an application service
    val myProjectService = LightServiceProjecLevelAnnotation.getInstance()

    override fun foo() { }
  }
}
