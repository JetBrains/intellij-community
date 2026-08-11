// IS_APPLICABLE: false
// ERROR: Extension property cannot be initialized because it has no backing field
// K2_ERROR: EXTENSION_PROPERTY_WITH_BACKING_FIELD

class A

val A.a: Int = 0<caret>
