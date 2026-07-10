// "Make bar internal" "false"
// ACTION: Add names to call arguments
// ACTION: Convert to lazy property
// ACTION: Enable option 'Property types' for 'Types' inlay hints
// ACTION: Move to constructor
// ERROR: Cannot access 'bar': it is private in 'First'
// K2_AFTER_ERROR: INVISIBLE_REFERENCE
// K2_ERROR: INVISIBLE_REFERENCE

private data class Data(val x: Int)

class First {
    // Making it internal exposes 'Data'
    private fun bar(x: Int) = Data(x)
}

class Second(f: First) {
    private val y = f.<caret>bar(42)
}
