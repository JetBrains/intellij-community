// IS_APPLICABLE: false
// ERROR: Data class primary constructor must only have property (val / var) parameters
// K2_ERROR: DATA_CLASS_NOT_PROPERTY_PARAMETER
data class Foo(<caret>x: Int, val y: Int) {

}