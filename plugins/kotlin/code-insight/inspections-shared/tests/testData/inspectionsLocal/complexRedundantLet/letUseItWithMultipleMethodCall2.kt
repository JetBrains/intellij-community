// WITH_STDLIB
// PROBLEM: none

fun foo() {
    val foo: String? = null
    foo?.let {
        it.to("").to(it).to("")<caret>
    }
}