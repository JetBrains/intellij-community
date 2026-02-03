class Obj(val field: Int)

fun f(obj: Any): Int =
    when (obj) {
        is Obj -> i<caret>f (obj.field == 42) 1 else 2
        else -> 0
    }