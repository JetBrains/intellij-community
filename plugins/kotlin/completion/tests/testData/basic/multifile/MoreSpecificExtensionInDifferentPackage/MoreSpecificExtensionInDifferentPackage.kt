package ppp

class C {
    fun foo() {
        xx<caret>
    }
}

// EXIST: { lookupString: "xxx", itemText: "xxx", tailText: "() for Any in dependency1", typeText: "Int", icon: "nodes/function.svg"}
// EXIST: { lookupString: "xxx", itemText: "xxx", tailText: "() for C in dependency2", typeText: "Int", icon: "nodes/function.svg"}
// NOTHING_ELSE
