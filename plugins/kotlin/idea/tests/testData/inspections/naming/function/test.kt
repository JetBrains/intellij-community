fun Foo() {}

fun FOO_BAR() {}

fun xyzzy() {}

fun `a b`() {}

interface I {
    fun a_b()
}

class C : I {
    override fun a_b() {} // Shouldn't be reported
}

fun Vector3d(): Int = 42

interface D
fun D(): D = object : D {}

interface E
fun E() = object : E {}

typealias F = () -> String
fun F(): F = { "" }

class Generic<ID>(val id: ID)
fun Generic(): Generic<Int> = Generic(0)

class Generic2<ID>(val id: ID)
fun Generic2() = Generic2(0)
