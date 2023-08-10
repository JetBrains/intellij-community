package one.two

fun read() {
    val c = with(KotlinObject.Nested) {
        42.staticExtension
    }
}