data class Test(
    val lambda: () -> Unit,
)

fun a() {
    Test(
        if (true) {
            <caret>
        },
    )
}