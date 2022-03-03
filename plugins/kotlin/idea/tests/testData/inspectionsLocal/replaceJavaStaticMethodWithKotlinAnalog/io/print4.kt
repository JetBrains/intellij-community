// WITH_STDLIB
fun x() {
    listOf("")
        .take(10)
        .forEach { System.out.<caret>print(it) }
}