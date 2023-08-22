// WITH_STDLIB
// Tests from PR#1176
fun foo(a: Int, b: Int) {
    val flag = a > b
    if (flag) {
        val notflag = <warning descr="Condition '!flag' is always false">!flag</warning>
        if (<warning descr="Condition 'notflag' is always false">notflag</warning>)
            println()
    }
}

fun boo(a: Boolean) {
    val flag = a
    val flag2 = a
    if (flag) {
        if (<warning descr="Condition '!flag2' is always false">!flag2</warning>)
            println()
    }
}

fun boo2(a: Boolean) {
    val flag = a
    val flag2 = a
    if (<warning descr="Condition 'flag && !flag2' is always false">flag && <warning descr="Condition '!flag2' is always false when reached">!flag2</warning></warning>) {
        println()
    }
}

fun boo3(a: Boolean) {
    val flag = a
    val flag2 = a
    if (flag && <warning descr="Condition 'flag2' is always true when reached">flag2</warning>) {
        println()
    }
}

fun foo1(b: Boolean) {
    if (b)
        return
    if (<warning descr="Condition 'b' is always false">b</warning>)
        println()
}

fun foo2(b: Boolean) {
    if (!b)
        return
    if (<warning descr="Condition 'b' is always true">b</warning>)
        println()
}

fun foo3(b: Boolean) {
    if (b)
        print (123)
    else
        return
    if (<warning descr="Condition 'b' is always true">b</warning>)
        println()
}

fun foo4(b: Boolean) {
    if (!b)
        print (123)
    else
        return
    if (<warning descr="Condition 'b' is always false">b</warning>)
        println()
}

fun foo5(a: Boolean, b: Boolean) {
    if (a && b) {
        if (<warning descr="Condition 'b' is always true">b</warning>)
            println()
    } else {
        if (a)
            println()
    }
}

fun foo6(a: Boolean, b: Boolean, c: Boolean) {
    if (a && b && c) {
        if (<warning descr="Condition 'c' is always true">c</warning>)
            println()
    } else {
        if (!b)
            println()
    }
}

fun foo7(a: Boolean, <warning descr="[UNUSED_PARAMETER] Parameter 'b' is never used">b</warning>: Boolean, <warning descr="[UNUSED_PARAMETER] Parameter 'c' is never used">c</warning>: Boolean) {
    if (a || <warning descr="Condition 'a' is always false when reached">a</warning>) {
        println()
    }
}

fun foo1Fp(a: Boolean) {
    val <warning descr="[UNUSED_VARIABLE] Variable 'x' is never used">x</warning> = if (a) 1 else 0
    val <warning descr="[UNUSED_VARIABLE] Variable 'y' is never used">y</warning> = if (a) 2 else 3
}

fun foo2Fp(a: Boolean, o: Any): Int {
    when(o) {
        is Int -> if (a) return 1
        is String -> if (a) return 2
    }
    return 3
}

fun foo3FpLambda(ints: Array<Int>, b : Boolean) {
    ints.forEach {
        if (b) return  // nonlocal return from inside lambda directly to the caller of foo()
        print(it)
    }
    if (b)
        print(123)
}
