fun numbers(): String {
    val numbers = StringBuilder()
    return with(numbers) {
        // "with" structure starts here
        for (letter in '0'..'9') {
            append(letter)
        }
        toString() // used as the return value
    }
}