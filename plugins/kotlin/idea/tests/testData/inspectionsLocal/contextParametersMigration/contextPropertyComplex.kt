// "Replace context receivers with context parameters" "true"
// COMPILER_ARGUMENTS: -Xcontext-parameters
// LANGUAGE_VERSION: 2.2
// K2_ERROR: Context parameters must be named. Use '_' to declare an anonymous context parameter.
// K2_ERROR: Context parameters must be named. Use '_' to declare an anonymous context parameter.

class C1 {
    fun C2.f1(): C1 = this@C1
    val c1 = "c1"
}

class C2 {
    fun C1.f2(): C2 = this@C2
    val c2 = "c2"
}

fun C1.ef1(): C1 = this
fun C2.ef2(): C2 = this

context(<caret>C1, C2)
val test: Int
    get() {
        f1()
        f2()
        f1().f2().f1().f2()
        c1
        c2

        with (C1()) {
            f1()
            f2()
            f1().f2().f1().f2()
            c1
            c2
        }

        with (C2()) {
            f1()
            f2()
            f1().f2().f1().f2()
            c1
            c2
        }

        with (C1()) {
            with (C2()) {
                f1()
                f2()
                f1().f2().f1().f2()
                c1
                c2
            }
        }

        return 0
    }
