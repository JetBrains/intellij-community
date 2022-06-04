// IS_APPLICABLE: false
// ERROR: Data class primary constructor must only have property (val / var) parameters
data class Foo(<caret>x: Int, val y: Int) {

}