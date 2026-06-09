// WITH_STDLIB
// PROBLEM: none
fun foo(): Result<String?> = Result.success("bar")

fun bar() {
    foo()
        .onSuccess {
            if (it == null) {
                <caret>return@onSuccess
            }
            println(it)
        }
}