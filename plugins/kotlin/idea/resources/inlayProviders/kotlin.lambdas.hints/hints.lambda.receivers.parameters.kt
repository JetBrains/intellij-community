fun numbers(): String {
    val numbers = StringBuilder()
    return with(numbers) {
        // the implicit receiver is "numbers" (StringBuilder)
        for (letter in '0'..'9') {
            append(letter)
        }
        toString()
    }
}