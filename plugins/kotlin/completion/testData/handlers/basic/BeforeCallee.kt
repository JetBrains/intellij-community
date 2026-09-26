fun test(a: Any) {
    a as? AFr<caret> .length
}

// FIR_COMPARISON
// FIR_IDENTICAL
// ELEMENT: AFromDependency