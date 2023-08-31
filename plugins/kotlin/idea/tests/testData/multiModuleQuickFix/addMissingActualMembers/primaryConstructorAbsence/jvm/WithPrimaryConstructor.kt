// "Add missing actual members" "true"
// DISABLE-ERRORS
// IGNORE_K2

actual class <caret>WithPrimaryConstructor {
    fun bar(x: String) {}

    val z: Double = 3.14
}
