// FIR_COMPARISON
// FIR_IDENTICAL
enum class Foo {
    FOO {
        to<caret>
    };
}

// EXIST: { lookupString: "override", allLookupStrings: "override, toString" }
// NOTHING_ELSE