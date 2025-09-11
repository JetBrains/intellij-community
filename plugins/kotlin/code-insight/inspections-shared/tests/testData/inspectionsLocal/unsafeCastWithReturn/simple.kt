fun test(x: Any): Any? {
    val y = <caret>x as String ?: return null
    return y
}

test(3)