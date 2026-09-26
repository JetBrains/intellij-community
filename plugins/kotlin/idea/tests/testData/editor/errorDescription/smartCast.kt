// DESCRIPTION: Smart cast to kotlin.String
fun foo(v: Any) {
    if (v is String) {
        v<caret>.length
    }
}