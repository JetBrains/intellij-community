// WITH_RUNTIME
class JavaMethods {
    var f : Int = 0

    fun testAbs(x : Int) {
        if (f == 0) return
        val y = Math.abs(x)
        if (y < 0) {
            if (<warning descr="Condition is always true">y == Integer.MIN_VALUE</warning>) {
                if (<warning descr="Condition is always false">f == 0</warning>) {}
            }
        }
    }
    
    fun testRandom() {
        val x = Math.random()
        if (<warning descr="Condition is always false">x == 1.0</warning>) {}
    }

    fun testCollections() {
        val c = java.util.Collections.singleton(123)
        if (<warning descr="Condition is always true">c.size == 1</warning>) {}
        println(c)
        if (<warning descr="Condition is always true">c.size == 1</warning>) {} // immutable collection
    }

    fun suppressZeroConstant() {
        val x = Character.MIN_VALUE
        println(x)
    }
}
