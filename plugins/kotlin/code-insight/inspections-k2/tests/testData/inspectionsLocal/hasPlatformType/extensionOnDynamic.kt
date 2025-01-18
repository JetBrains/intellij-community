// WITH_STDLIB
// PROBLEM: none
// K2-ERROR: Dynamic receivers are prohibited.
// K2-ERROR: Unsupported [dynamic type].

class C {
    operator fun dynamic.plus<caret>(x: Any?) = this
}