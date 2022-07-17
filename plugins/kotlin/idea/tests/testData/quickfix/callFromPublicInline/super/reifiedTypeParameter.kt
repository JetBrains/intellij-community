// "Remove 'inline' modifier" "false"
// ERROR: Accessing super members from public-API inline function is deprecated
// ACTION: Expand boolean expression to 'if else'
// ACTION: Make 'bar' internal
// ACTION: Make 'bar' private
// ACTION: Remove braces from all 'if' statements
// WITH_STDLIB
open class Base {
    fun baseFun() = true
}

open class Derived : Base() {
    inline fun <reified T> bar(x: T): String {
        return if (<caret>super.baseFun()) {
            T::class.java.toString()
        } else {
            ""
        }
    }
}