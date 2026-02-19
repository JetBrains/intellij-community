object EmptyList : AbstractList<Any>() {

    override val size: Int get() = 0

    override fun get(index: Int): Nothing = throw IndexOutOfBoundsException()
}

fun foo() {
    EmptyList.let { <caret> }
}

// INVOCATION_COUNT: 0
// EXIST: { itemText: "emptyList", tailText: " -> ", allLookupStrings: "emptyList", typeText: "EmptyList" }
// ABSENT: { lookupString: "any", itemText: "(any, any1, any2, any3, any4)", tailText: " -> ", allLookupStrings: "any, any1, any2, any3, any4", typeText: "(Any, Any, Any, Any, Any)" }