fun test(number: Int) {
    when {
        number < 0 -> print("Negative")
        // foo<caret>
        // bar
        number == 0 -> print("Zero")
        else -> print("Large")
    }
}