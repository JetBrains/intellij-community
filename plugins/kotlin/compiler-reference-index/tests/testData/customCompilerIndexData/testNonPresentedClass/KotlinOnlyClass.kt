package one

open class KotlinOnlyClass {
  val property: Int = 4

  @JvmField
  val fieldProperty: Int = 4

  var variable: Int? = 4

  @JvmField
  var fieldVariable: Int = 4

  lateinit var lateinitVariable: Custom

  fun simpleFunction(i: Int) {}

  fun String.extension() {}
}

class Custom

open class Proxy : KotlinOnlyClass()