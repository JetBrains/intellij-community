// "Implement members" "true"
// WITH_STDLIB
interface I {
    fun foo()
}

data <caret>class C(val i: Int) : I {}