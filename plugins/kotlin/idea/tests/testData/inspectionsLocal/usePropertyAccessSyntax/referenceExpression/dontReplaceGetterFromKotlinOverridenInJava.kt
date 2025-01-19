// PROBLEM: none
// WITH_STDLIB
// ERROR: Unresolved reference: getX
// K2-ERROR:

fun test() {
    val j = JavaClassOverridingKotlinClass()
    with(j) {
        <caret>getX()
    }
}

open class OpenKotlinClass {
    private var x: Int = 0

    open fun getX(): Int {
        return x
    }

    open fun setX(x: Int) {
        this.x = x
    }
}