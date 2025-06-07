// WITH_STDLIB
fun main() {
    run label@{
        return@label<caret> when {
            true ->
                42
            else -> 42
        }
    }
}