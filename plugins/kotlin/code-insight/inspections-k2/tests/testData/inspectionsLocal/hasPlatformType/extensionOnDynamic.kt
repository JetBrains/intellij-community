// WITH_STDLIB
// PROBLEM: none
// K2_ERROR: Dynamic receivers are prohibited.
// K2_ERROR: Unsupported [dynamic type].

class C {
    operator fun dynamic.plus<caret>(x: Any?) = this
}