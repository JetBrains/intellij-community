enum class SomeEnum {
    <caret>UNUSED;

    companion object {
        fun values(a: Int) {
            return
        }
    }
}

fun main() {
    SomeEnum.values(1)
}
// IGNORE_K2