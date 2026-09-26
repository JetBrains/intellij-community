class A {

    val name = "name"

    fun testMeAny(): Any? {
        return n<caret>
    }
}

// ORDER: null
// ORDER: name
