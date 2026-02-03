// FIR_COMPARISON
interface Foo

interface Bar {
    context(Foo, String)
    fun bar()
}

class Baz : Bar {
    o<caret>
}

// EXIST: { lookupString: "override", itemText: "override"}
// EXIST: { itemText: "override fun bar() {...}", lookupString: "override", allLookupStrings: "bar, override", tailText: " for Foo, String", typeText: "Bar", attributes: "bold", icon: "nodes/abstractMethod.svg" }