// IS_APPLICABLE: false
// WITH_STDLIB

suspend fun String.bar() {

}

suspend fun x() {
    listOf("Jack", "Tom").forEach <caret>{ it.bar() }
}
