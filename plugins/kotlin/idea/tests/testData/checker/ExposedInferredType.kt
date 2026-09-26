private class A

open class B<T>(val x: T)

class C(<error descr="[EXPOSED_PARAMETER_TYPE]">x: A</error>): <error descr="[EXPOSED_SUPER_CLASS]">B<A>(x)</error>

private class D {
    class E
}

fun <error descr="[EXPOSED_FUNCTION_RETURN_TYPE]">create</error>() = A()

fun <error descr="[EXPOSED_FUNCTION_RETURN_TYPE]">create</error>(<error descr="[EXPOSED_PARAMETER_TYPE]">a: A</error>) = B(a)

val <error descr="[EXPOSED_PROPERTY_TYPE]">x</error> = create()

val <error descr="[EXPOSED_PROPERTY_TYPE]">y</error> = create(x)

val <error descr="[EXPOSED_PROPERTY_TYPE]">z</error>: B<D.E>? = null
