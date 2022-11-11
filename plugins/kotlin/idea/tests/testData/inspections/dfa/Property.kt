// WITH_STDLIB
abstract class X {
    var a: Int = 0
    @Volatile
    var b: Int = 0
    abstract var c: Int

    fun testProperty() {
        if (<warning descr="Condition 'a > 5 && a < 3' is always false">a > 5 && <warning descr="Condition 'a < 3' is always false when reached">a < 3</warning></warning>) {}
        if (b > 5 && b < 3) {}
        if (c > 5 && c < 3) {}
    }
}
open class Test {
  open var a: String? = ""
  fun printA0(){
    if (a==null){
      println()
    } else if (a!=null){
      println()
    }
  }
}

open class Test2 : Test() {
  override var a: String?
    get() = if (Math.random() > 0.5) "" else null
    set(<warning descr="[UNUSED_PARAMETER] Parameter 'value' is never used">value</warning>) {}
}