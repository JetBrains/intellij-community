package p

open class A

interface I {
    interface Nested
}

class B : A(), I.Nested {
    fun foo() {
        super<<caret>
    }
}

// EXIST: A
// EXIST: { lookupString: "Nested", allLookupStrings: "I, Nested", itemText: "I.Nested", tailText: " (p)", icon: "org/jetbrains/kotlin/idea/icons/interfaceKotlin.svg"}
// NOTHING_ELSE