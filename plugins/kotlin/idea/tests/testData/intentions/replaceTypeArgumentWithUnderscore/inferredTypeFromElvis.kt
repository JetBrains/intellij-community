interface I

object Obj : I

inline fun <reified E : I> foo(a: Any?): E? = a as? E

fun box() = foo<<caret>Obj>(null) ?: Obj
