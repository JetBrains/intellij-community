// IGNORE_K1

object `KTIJ-25208` {

    fun `interface`() {}
}

fun bar() {
    i<caret>
}

// EXIST: { itemText: "KTIJ-25208.interface", lookupString: "interface", tailText: "()", typeText: "Unit", icon: "Method" }
// INVOCATION_COUNT: 2