// WITH_STDLIB
// PROBLEM: none
// K2_ERROR: DYNAMIC_RECEIVER_NOT_ALLOWED
// K2_ERROR: UNSUPPORTED

class C {
    operator fun dynamic.plus<caret>(x: Any?) = this
}