// "Add non-null asserted (!!) call" "true"

open class MyClass {
    open val s: String? = null

    fun foo() {
        if (s == null) {
            val s2: String = s<caret>
        }
    }
}