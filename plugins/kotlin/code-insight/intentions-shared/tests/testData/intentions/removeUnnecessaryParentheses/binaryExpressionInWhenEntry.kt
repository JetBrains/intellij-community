// IS_APPLICABLE: false
fun main() {
    when (true) {
        true -> <caret>(1
                < 2)
        else -> {}
    }
}
