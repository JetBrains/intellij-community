// "Replace with 'this.bar'" "true"
// WITH_STDLIB
import kotlin.reflect.KProperty0

class A {
    @Deprecated("...", ReplaceWith("this.bar"))
    val foo = 1
    val bar = 2
}

fun test() {
    with(A()) {
        val c: KProperty0<Int> = ::foo<caret>
    }
}
// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.replaceWith.DeprecatedSymbolUsageFix