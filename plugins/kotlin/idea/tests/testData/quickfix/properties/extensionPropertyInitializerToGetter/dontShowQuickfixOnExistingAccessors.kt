// "Convert extension property initializer to getter" "false"
// ERROR: Extension property cannot be initialized because it has no backing field
// K2_AFTER_ERROR: EXTENSION_PROPERTY_WITH_BACKING_FIELD
// K2_ERROR: EXTENSION_PROPERTY_WITH_BACKING_FIELD
var String.foo: Int = 0<caret>
    get() = 1
