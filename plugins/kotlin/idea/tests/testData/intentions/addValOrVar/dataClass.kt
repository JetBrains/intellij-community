// IS_APPLICABLE: false
// K2_ERROR: Primary constructor of data class must only have property ('val' / 'var') parameters.
// ERROR: Data class primary constructor must only have property (val / var) parameters
data class Foo(<caret>x: Int, val y: Int) {

}