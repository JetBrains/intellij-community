package test

import java.util.ArrayList

@Deprecated("Use A instead") open class MyClass {
    val test = this
}

fun test() {
    val a : <warning descr="[DEPRECATION]">MyClass</warning>? = null
    val b = <warning descr="[DEPRECATION]">MyClass</warning>()
    val c = ArrayList<<warning descr="[DEPRECATION]">MyClass</warning>>()

    a == b && a == c
}

class Test(): <warning descr="[DEPRECATION]">MyClass</warning>() {}

class Test2(<warning descr="[UNUSED_PARAMETER]">param</warning>: <warning descr="[DEPRECATION]">MyClass</warning>) {}

// NO_CHECK_INFOS
// NO_CHECK_WEAK_WARNINGS
