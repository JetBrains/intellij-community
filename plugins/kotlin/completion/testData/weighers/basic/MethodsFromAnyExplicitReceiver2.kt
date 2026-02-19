// FIR_COMPARISON
// FIR_IDENTICAL

class A {
    fun xxFun() {}
}

fun test(a: A) {
    a.<caret>
}

// ORDER: xxFun
// ORDER: equals
// ORDER: hashCode
// ORDER: toString
