// PROBLEM: none
// ERROR: 'operator' modifier is inapplicable on this function: must have at least 2 value parameters
// K2_ERROR: INAPPLICABLE_OPERATOR_MODIFIER
class C {
    operator fun set(){}
}

class D(val c: C) {
    fun foo() {
        this.c.<caret>set()
    }
}
