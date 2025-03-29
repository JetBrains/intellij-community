// IS_APPLICABLE: false
// ERROR: Cannot find a parameter with this name: c
// ERROR: 'operator' modifier is inapplicable on this function: must have a single value parameter
// K2_ERROR: 'operator' modifier is not applicable to function: must have a single value parameter.
// K2_ERROR: No parameter with name 'c' found.
fun test() {
    class Test{
        operator fun contains(a: Int=1, b: Int=2): Boolean = true
    }
    val test = Test()
    test.c<caret>ontains(c=3)
}
