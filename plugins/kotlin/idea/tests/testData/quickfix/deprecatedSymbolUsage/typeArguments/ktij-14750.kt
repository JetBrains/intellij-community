// "Replace with 'run { bar<E> { } }'" "true"
// WITH_STDLIB

class Box<T>

@Deprecated("", ReplaceWith("run { bar<E> { } }"))
fun <E : Any, T : Box<E>> Box<T>.foo() = 42

fun <T> bar(f: Box<T>.() -> Unit) = f

fun test(e: Box<Box<Any>>) {
    e.<caret>foo()
}
// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.replaceWith.DeprecatedSymbolUsageFix
// IGNORE_K2