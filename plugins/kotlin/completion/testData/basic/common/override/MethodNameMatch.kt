// FIR_COMPARISON
// FIR_IDENTICAL
class Foo {
    eq<caret>
}

// EXIST: { itemText: "override fun equals(other: Any?): Boolean {...}", lookupString: "override", allLookupStrings: "equals, override", tailText: null, typeText: "Any", attributes: "", icon: "Method" }
// NOTHING_ELSE