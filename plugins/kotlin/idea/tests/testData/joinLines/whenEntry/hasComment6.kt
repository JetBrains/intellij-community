fun test(number: Int) {
    when {
        number < 0 -> print("Negative")
        <caret> // foo bar
        number == 0 -> print("Zero")
        else -> print("Large")
    }
}