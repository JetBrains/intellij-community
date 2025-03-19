// IGNORE_K1

package test

val xxx: String = ""

class C {
    val xxx = x<caret>
}

// EXIST: { lookupString: "xxx", itemText: "xxx", tailText: " (test)", typeText: "String" }
