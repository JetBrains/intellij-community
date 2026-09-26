// IS_APPLICABLE: false
interface A
fun m() {
    object: A {
        fun <caret>f() {}
    }
}