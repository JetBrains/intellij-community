data class Test(
    val lambda: () -> Unit,
)

fun a() {
    Test(
        lambda = if (true) {
            <caret>
        },
    )
}