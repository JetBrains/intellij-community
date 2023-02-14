fun List<*>.func(function: List<*>.()-> Unit) {
    this.function()
}

fun test() {
    return listOf(1, 2, 3).func {
        this@func<caret>
    }
}