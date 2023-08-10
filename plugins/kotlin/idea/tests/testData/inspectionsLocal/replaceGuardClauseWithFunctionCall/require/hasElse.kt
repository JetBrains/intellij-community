// WITH_STDLIB
fun test(flag: Boolean) {
    <caret>if (!flag) throw IllegalArgumentException() else println(1)
}