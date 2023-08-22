interface I

object Obj : I

inline fun <reified E : I> foo(a: Any?): E? = a as? E

fun box() = <caret>foo(null) ?: Obj
