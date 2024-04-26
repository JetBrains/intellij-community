// "Create method 'foo' in 'J'" "true"
// ERROR: Unresolved reference: foo

class K {
    var b = false
    fun test(j: J) {
        b = j.<selection><caret></selection>foo(1, "2")
    }
}

// IGNORE_K1
