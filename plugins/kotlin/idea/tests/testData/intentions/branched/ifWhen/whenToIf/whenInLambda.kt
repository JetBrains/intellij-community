// PRIORITY: LOW

fun foo(xs: List<Int>) {
    xs.forEach {
        when (it.unaryMinus()) {
            1 -> pri<caret>ntln("one")
            2 -> println("two")
        }
    }
}

// IGNORE_K1