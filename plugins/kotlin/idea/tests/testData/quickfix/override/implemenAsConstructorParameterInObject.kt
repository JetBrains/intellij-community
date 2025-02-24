// "Implement as constructor parameters" "false"
// ACTION: Create test
// ACTION: Implement members
// ACTION: Make internal
// ACTION: Make private
// ACTION: Extract 'A' from current file
// ERROR: Object 'A' is not abstract and does not implement abstract member public abstract val foo: Int defined in I
// K2_AFTER_ERROR: Object 'A' is not abstract and does not implement abstract member:<br>val foo: Int
interface I {
    val foo: Int
}

<caret>object A : I
