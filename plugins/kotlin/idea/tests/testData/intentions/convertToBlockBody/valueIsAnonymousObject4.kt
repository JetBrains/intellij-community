// WITH_STDLIB

interface I

private fun f() = <caret>listOf(object : I { })
