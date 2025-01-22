// "Create expected class in common module testModule_Common" "false"
// DISABLE_ERRORS
// ACTION: Apply all 'Remove modifier' fixes in file
// ACTION: Remove 'actual' modifier
// IGNORE_K2

class Some(val x: Int, val y: Int) {
    fun processIt(z: Int) = x + y - z
}

actual typealias <caret>My = Some