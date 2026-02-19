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
// EXIST: { itemText: "override context(Foo, String) fun bar() {...}", lookupString: "override", allLookupStrings: "bar, override", typeText: "Bar", attributes: "bold", icon: "nodes/abstractMethod.svg" }