val Foo: String = ""

var FOO_BAR: Int = 0

var _FOO: Int = 0

const val THREE = 3

val xyzzy = 1

fun foo() {
    val XYZZY = 1
    val BAR_BAZ = 2
}

object Foo {
    private var _foo: String = "default"

    val Foo: String = ""

    var FOO_BAR: Int = 0

    var _FOO: Int = 0
}

class D {
    private val _foo: String

    private val FOO_BAR: String

    companion object {
        private var _bar: String = "default"

        val Foo: String = ""

        var FOO_BAR: Int = 0
    }
}