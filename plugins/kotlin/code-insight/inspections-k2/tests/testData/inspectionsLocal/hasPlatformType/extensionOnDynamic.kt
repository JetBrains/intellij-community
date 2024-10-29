// WITH_STDLIB
// PROBLEM: none

class C {
    operator fun dynamic.plus<caret>(x: Any?) = this
}