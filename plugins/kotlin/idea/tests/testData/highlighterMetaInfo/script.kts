// FIR_IDENTICAL
// IGNORE_K1
// IGNORE_K2
// ^ KTIJ-31075
class MyScriptClass {
    fun memberFunction(i: Int) {

    }
}

fun foo(m: MyScriptClass) {
    m.memberFunction(42)
}
