// IS_APPLICABLE: false
// PROBLEM: none
// WITH_STDLIB

data class My(val a: String, val second: Int) {
    fun smth(): String = "$a $second"
}

fun foo(list: List<My>) {
    for (<caret>my in list) {
        my.a + my.second
        my.smth()
    }
}
