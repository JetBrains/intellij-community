// "Replace with 'this.bar()'" "true"
// WITH_STDLIB
import kotlin.reflect.KFunction0

class A {
    @Deprecated("...", ReplaceWith("this.bar()"))
    fun foo() = 1
    fun bar() = 2
}

fun test() {
    with(A()) {
        val f: KFunction0<Int> = ::foo<caret>
    }
}