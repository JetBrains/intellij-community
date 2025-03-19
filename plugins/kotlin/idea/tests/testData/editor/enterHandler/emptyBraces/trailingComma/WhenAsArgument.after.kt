data class Test(
    val lambda: () -> Unit,
)

fun a() {
    Test(
        when {
            <caret>
        },
    )
}
