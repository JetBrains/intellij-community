val Foo: String = ""

var FOO_BAR: Int = 0

private var FOO: Int = 0

const val THREE = 3

val xyzzy = 1

fun foo() {
    val XYZZY = 1
    val BAR_BAZ = 2
}

object Foo {
    val Foo: String = ""

    var FOO_BAR: Int = 0
}

class D {
    private val _foo: String

    private val FOO_BAR: String

    private val FOOBAR: String

    private val `bad char` = ""

    companion object {
        val Foo: String = ""

        var FOO_BAR: Int = 0

        private val FOO_BAZ = 1
    }
}

interface Parameter {
    val interface_p1: String
    val interface_p2: String
}

class ParameterImpl(
    override val interface_p1: String,
    private val ctor_private: String,
    private val CtorPrivate: String,
    private val `ctor Private`: String,
    val ctor_val: String,
    var ctor_var: String,
    ctor_param: String,
): Parameter {
    override val interface_p2: String = ""
    fun foo(fun_param: String) {
        val local_val = 1
    }
}
