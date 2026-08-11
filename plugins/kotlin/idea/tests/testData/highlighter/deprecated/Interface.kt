@Deprecated("Use A instead") interface MyInterface { }

fun test() {
   val a: <warning descr="[DEPRECATION]">MyInterface</warning>? = null
   val b: List<<warning descr="[DEPRECATION]">MyInterface</warning>>? = null
   a == b
}

class Test(): <warning descr="[DEPRECATION]">MyInterface</warning> { }

class Test2(<warning descr="[UNUSED_PARAMETER]">param</warning>: <warning descr="[DEPRECATION]">MyInterface</warning>) {}

// NO_CHECK_INFOS
// NO_CHECK_WEAK_WARNINGS
