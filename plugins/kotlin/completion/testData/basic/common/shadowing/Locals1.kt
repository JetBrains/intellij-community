// FIR_IDENTICAL
// FIR_COMPARISON
class C {
    val xxx = 1

    fun foo(xxx: String) {
        val xxx = 'x'

        if (true) {
            val xxx = true
            xx<caret>
        }
    }
}

// EXIST: { lookupString: "xxx", itemText: "xxx", typeText: "Boolean", icon: "org/jetbrains/kotlin/idea/icons/value.svg"}
// NOTHING_ELSE
