fun foo(): Int? = null

fun test() : Int? {
    return@test foo() <caret>?: return null
}