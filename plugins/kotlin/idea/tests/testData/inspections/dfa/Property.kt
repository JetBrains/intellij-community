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
interface Foo {
    var b: String?
}

fun testIface(foo : Foo) {
    if (foo.b==null){
        println()
    } else if (foo.b!=null){
        println()
    }
}

open class Test : Foo {
  open var a: String? = ""
  override var b: String? = ""
  fun printA0(){
    if (a==null){
      println()
    } else if (a!=null){
      println()
    }
    if (b==null){
      println()
    } else if (b!=null){
      println()
    }
  }
}

open class Test2 : Test() {
  override var a: String?
    get() = if (Math.random() > 0.5) "" else null
    set(<warning descr="[UNUSED_PARAMETER] Parameter 'value' is never used">value</warning>) {}
  override var b: String?
    get() = if (Math.random() > 0.5) "" else null
    set(<warning descr="[UNUSED_PARAMETER] Parameter 'value' is never used">value</warning>) {}
}
class WithInit(a: Int) {
    init {
        if (a > 0) {
            println(1)
        } else if (<warning descr="Condition 'a <= 0' is always true">a <= 0</warning>) {
            println(2)
        }
    }
}
