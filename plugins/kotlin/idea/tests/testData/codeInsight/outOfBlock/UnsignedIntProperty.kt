// WITH_STDLIB
// TYPE: 'u'

// TODO: It has to be FALSE, actually fix is a workaround for the issue in FE (KTIJ-20240)
// OUT_OF_CODE_BLOCK: TRUE
// SKIP_ANALYZE_CHECK
fun fooFun() {
    val z: UInt = 0u
}

class FooClass {
    val x: UInt = 1<caret>
}