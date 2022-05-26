// WITH_STDLIB
fun simpleRange(x: Int) = when {
    x > 10 -> 10
    <warning descr="Condition 'x > 15' is always false">x > 15</warning> -> 15
    else -> 0
}
fun inRange(obj : Int) {
    when (obj) {
        in 0 until 10 -> {}
        10 -> {}
        <warning descr="'when' branch is never reachable">9</warning> -> {}
    }
    when (obj) {
        in 0 until 10 -> {}
        <warning descr="'when' branch is always reachable">!in 0..9</warning> -> {}
        20 -> {}
        else -> {}
    }
    if (obj > 0) {
        when (obj) {
            3 -> {}
            2 -> {}
            1 -> {}
            <warning descr="'when' branch is never reachable">0</warning> -> {}
        }
    }
}
fun whenIs(obj : Any?) {
    when(obj) {
        is X -> {}
        is Y -> {}
        else -> {
            if (<warning descr="Condition 'obj is X' is always false">obj is X</warning>) {}
        }
    }
    if (obj is X) {
        when(obj) {
            <warning descr="[USELESS_IS_CHECK] Check for instance is always 'false'">is Y</warning> -> {}
        }
    }
}
fun lastBranchTrue(obj : Boolean, obj2 : Any) {
    when(obj) {
        true -> {}
        false -> {}
    }
    when(obj2) {
        is String -> {}
        is Int -> {}
        else -> return
    }
    when(obj2) {
        is String -> {}
        is Int -> {}
        else -> return
    }
}
fun lastBranchTrue2(obj2 : Any) {
    when(obj2) {
        is String -> {}
        is Int -> {}
        else -> return
    }
    when(obj2) {
        is String -> {}
        is Int -> {}
        else -> return
    }
}
fun suppressSimilarTests2(a: Boolean, b: Boolean) {
    when {
        a && b -> {}
        a && !b -> {}
        !a && b -> {}
        else -> {}
    }
}
fun suppressSimilarTests3(a: Boolean, b: Boolean, c: Boolean) {
    when {
        a && b && c -> {}
        a && b && !c -> {}
        a && !b && c -> {}
        a && !b && !c -> {}
    }
}
fun unboxBoolean(obj : Boolean?) {
    when(obj) {
        true -> {}
        false -> {}
        null -> {}
    }
}
fun unboxAny(obj : Any) {
    when(obj) {
        0 -> {}
        true -> {}
    }
}
fun returnFromWhen(x: Int): Unit {
    when {
        x > 10 -> return
        x < 0 -> return
    }
    if (<warning descr="Condition 'x == 11' is always false">x == 11</warning>) {}
}
fun throwBranch(x: Int) {
    when(x) {
        0 -> {}
        1 -> {}
        2 -> return
        3 -> {}
    }
    when(x) {
        0 -> {}
        1 -> {}
        <warning descr="'when' branch is never reachable">2</warning> -> throw Exception()
        3 -> {}
    }
    when {
        x == 0 -> {}
        x == 1 -> {}
        <warning descr="Condition 'x == 2' is always false">x == 2</warning> -> throw Exception()
        x == 3 -> {}
    }
}
class X {}
class Y {}