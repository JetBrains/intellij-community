// "Convert extension property initializer to getter" "false"
// ACTION: Do not show return expression hints
// ERROR: Extension property cannot be initialized because it has no backing field
var String.foo: Int = 0<caret>
    get() = 1
