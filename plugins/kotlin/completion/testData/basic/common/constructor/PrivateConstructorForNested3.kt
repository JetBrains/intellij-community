// FIR_IDENTICAL
// FIR_COMPARISON
class Container {
    class NestedValue private constructor(val value: Int) {
        companion object {
            operator fun invoke(nestedCount: Int) = NestedValue(nestedCount + 4)
        }

        class NestedNested {
            fun test() {
                NestedValue(<caret>)
            }
        }
    }
}

// EXIST: { "lookupString":"value =", "tailText":" Int", "itemText":"value =" }