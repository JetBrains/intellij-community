// WITH_STDLIB

class Test1 {
    fun test() {
        val s = String::class.java.getSimpleName()
        if (<warning descr="Condition 's == String::class.java.getSimpleName()' is always true">s == String::class.java.getSimpleName()</warning>) {}
    }
}