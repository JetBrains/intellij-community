fun foo(): Int? = null

fun test() : Int? {
    return foo() <caret>?: return@test null
}