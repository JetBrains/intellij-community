// IGNORE_K2

val <error descr="Destructuring declarations are only allowed for local variables/values">(a, b)</error> by <error descr="[DEBUG]">lazy</error> { <error descr="[DEBUG]">Pair</error>(1, 2) }
val <error descr="Destructuring declarations are only allowed for local variables/values">(c, d)</error> = <error descr="[DEBUG]">run</error> { <error descr="[DEBUG]">Pair</error>(3, 4) }


class Foo {
    val <error descr="Destructuring declarations are only allowed for local variables/values">(a, b)</error> by <error descr="[DEBUG]">lazy</error> { <error descr="[DEBUG]">Pair</error>(1, 2) }
    val <error descr="Destructuring declarations are only allowed for local variables/values">(c, d)</error> = <error descr="[DEBUG]">run</error> { <error descr="[DEBUG]">Pair</error>(3, 4) }
}

// NO_CHECK_INFOS
// WITH_STDLIB
