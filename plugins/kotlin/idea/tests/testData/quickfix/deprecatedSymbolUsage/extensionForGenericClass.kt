// "Replace with 'newFun(t)'" "true"

class C<T>

@Deprecated("", ReplaceWith("newFun(t)"))
fun <T> C<T>.oldFun(t: T){}

fun <T> C<T>.newFun(t: T){}

fun foo(x: C<String>) {
    x.<caret>oldFun("a")
}
