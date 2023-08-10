// IS_APPLICABLE: false

package one.two.three

fun test() {
    fun localFun() = 42

    <caret>localFun()
}