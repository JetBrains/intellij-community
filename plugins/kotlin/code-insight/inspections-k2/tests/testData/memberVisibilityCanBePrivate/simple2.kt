val a = "a"

fun f1() {}

fun f2() {
    println(a)
    f1()
    A().b
}

class A {
    val unused = ""
    val <weak_warning descr="Property 'a' could be private">a</weak_warning> = ""
    internal val b = ""
    protected val c = ""
    private val d = ""

    fun unused() {}
    fun <weak_warning descr="Function 'f1' could be private">f1</weak_warning>() {}
    <weak_warning descr="Function 'f2' could be private">internal</weak_warning> fun f2() {}
    protected fun f3() {}
    private fun f4() {}

    fun bar() {
        println(a)
        println(b)
        println(c)
        println(d)
        f1()
        f2()
        f3()
        f4()
    }
}

interface I {
    val x: String
    fun foo()
}

class B : I {
    override val x: String
        get() = ""

    override fun foo() {
    }

    fun bar() {
        println(x)
        foo()
    }
}

interface I2 {
    val x: String
    fun foo()
    fun bar() {
        println(x)
        foo()
    }
}

open class C {
    open val x = ""
    <weak_warning descr="Property 'y' could be private">protected</weak_warning> val y = ""

    open fun f1() {}
    <weak_warning descr="Function 'f2' could be private">protected</weak_warning> fun f2() {}

    fun bar() {
        println(x)
        println(y)
        f1()
        f2()
    }
}

class D(val <weak_warning descr="Property 'a' could be private">a</weak_warning>: String = "",
        var <weak_warning descr="Property 'b' could be private">b</weak_warning>: String = "",
        <weak_warning descr="Property 'c' could be private">internal</weak_warning> val c: String = "",
        protected val d: String = "",
        private val e: String = "") {
    fun foo() {
        println(a)
        println(b)
        println(c)
        println(d)
        println(e)
    }
}

open class E(override val x: String = "",
             open val a: String = "") : I {
    override fun foo() {}
    fun foo3() {
        println(x)
        println(a)
    }

    fun bar() {
        var v1 = ""
        val v2 = ""
        println(v1)
        println(v2)
    }
}

val x = object {
    val a: String = ""
    internal val b: String = ""
    protected val c: String = ""
    private val d: String = ""

    fun f1() {}
    internal fun f2() {}
    protected fun f3() {}
    private fun f4() {}

    fun foo() {
        println(a)
        println(b)
        println(c)
        println(d)
        f1()
        f2()
        f3()
        f4()
    }
}

class F(val bar: Int) {
    <warning descr="[NOTHING_TO_INLINE] Expected performance impact from inlining is insignificant. Inlining works best for functions with parameters of function types.">inline</warning> fun baz() = bar
}

class G(val <weak_warning descr="Property 'bar' could be private">bar</weak_warning>: Int) {
    private <warning descr="[NOTHING_TO_INLINE] Expected performance impact from inlining is insignificant. Inlining works best for functions with parameters of function types.">inline</warning> fun baz() = bar
}

private class H(val a: String = "",
                var b: String = "",
                internal val c: String = "",
                protected val d: String = "",
                private val e: String = "") {

    var f: String = ""
    fun g(): String = ""

    fun foo() {
        println(a)
        println(b)
        println(c)
        println(d)
        println(e)
        println(f)
        println(g())
    }
}

private class I1 {
    class NestedCls(val a: String) {
        var b: String = ""
        fun c(): String = ""
        fun foo() {
            println(a)
            println(b)
            println(c())
        }
    }
    object NestedObj {
        var b: String = ""
        fun c(): String = ""
        fun foo() {
            println(b)
            println(c())
        }
    }
}

class J {
    class NestedCls(val <weak_warning descr="Property 'a' could be private">a</weak_warning>: String) {
        var <weak_warning descr="Property 'b' could be private">b</weak_warning>: String = ""
        fun <weak_warning descr="Function 'c' could be private">c</weak_warning>(): String = ""
        fun foo() {
            println(a)
            println(b)
            println(c())
        }
    }
    object NestedObj {
        var <weak_warning descr="Property 'b' could be private">b</weak_warning>: String = ""
        fun <weak_warning descr="Function 'c' could be private">c</weak_warning>(): String = ""
        fun foo() {
            println(b)
            println(c())
        }
    }
}

fun withLocal(): Int {
    class Local(val x: Int) {
        val y = x
        fun res() = x + y
    }

    val local = Local(42)
    return local.res()
}
public annotation class EntryPoint
class Math {
    @EntryPoint fun fact(n: Int):Int = if (n < 2) 1 else n * fact(n - 1)
}

<error descr="[NOT_A_MULTIPLATFORM_COMPILATION] 'expect' and 'actual' declarations can be used only in multiplatform projects. Learn more about Kotlin Multiplatform: https://kotl.in/multiplatform-setup">expect</error> class Expect {
    fun foo()
    val bar: Int
}

<error descr="[NOT_A_MULTIPLATFORM_COMPILATION] 'expect' and 'actual' declarations can be used only in multiplatform projects. Learn more about Kotlin Multiplatform: https://kotl.in/multiplatform-setup">actual</error> class Actual {
    <error descr="[NOT_A_MULTIPLATFORM_COMPILATION] 'expect' and 'actual' declarations can be used only in multiplatform projects. Learn more about Kotlin Multiplatform: https://kotl.in/multiplatform-setup">actual</error> fun foo() {}
    <error descr="[NOT_A_MULTIPLATFORM_COMPILATION] 'expect' and 'actual' declarations can be used only in multiplatform projects. Learn more about Kotlin Multiplatform: https://kotl.in/multiplatform-setup">actual</error> val bar = 42
}

annotation class Ann

@Ann
val annotated = 3.14

@Ann
fun annotatedFun() {}
