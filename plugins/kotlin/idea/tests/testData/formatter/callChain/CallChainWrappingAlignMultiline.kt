val x = foo.bar().baz().quux()

val x2 = foo().bar().baz().quux()

val x3 = ((foo().bar())).baz().quux()

val x4 = (foo().bar().baz()).quux()

val x5 = (foo()).bar().baz().quux()

val x6 = foo!!.bar().baz()!!.quux()!!

val x7 = foo!!.bar().baz()!!.quux()!!

val x8 = foo!!!!!!!!.bar().baz()!!.quux()!!

val x9 = ((b!!)!!!!)!!.f

val x10 = a()!!.a()

val x11 = a()!!!!.a()

val x12 = a()!!.a()!!.a()

val x13 = a()!!!!.a().a()

val x14 = a().a()

val x15 = (a()).a()

val x16 = (a()).a().a()

val x17 = (a().a()).a()

val x18 = (a().a()).a().a()

val x18 = (a().a().a()).a().a()

val x19 = (a().a().a()).a()

val x20 = foo!!.foo.baz()!!.quux()!!.foo.foo.foo.baz().foo.baz().baz()

val y = xyzzy(foo.bar().baz().quux())

fun foo() {
    foo.bar().baz().quux()

    z = foo.bar().baz().quux()

    z += foo.bar().baz().quux()

    return foo.bar().baz().quux()
}

fun top() = "".plus("").plus("")
class C {
    fun member() = "".plus("").plus("")
}
fun foo() {
    fun local() = "".plus("").plus("")
    val anonymous = fun() = "".plus("").plus("")
}

override fun foo(bar: baz) {
    outter.call {
        val inner = a.a(it)
        val xyz = col.map { b -> b.baz(inner) }.map { c -> inner.foo(c).bar(c) }
        inner.foo(xyz).bar()
    }
}

// SET_INT: METHOD_CALL_CHAIN_WRAP = 2
// SET_FALSE: WRAP_FIRST_METHOD_IN_CALL_CHAIN
// SET_TRUE: ALIGN_MULTILINE_CHAINED_METHODS