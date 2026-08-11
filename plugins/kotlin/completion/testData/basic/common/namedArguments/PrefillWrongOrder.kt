class Apple(val size: Int, var color: String)


fun testAppleAgain(size: Int, color: String) {
    Apple(color = color, <caret>)
}

// EXIST: { itemText: "size = size" }
// ABSENT: { itemText: "color = color" }
