fun<T> foo(p1: Any, p2: Any?, p3: String?): String {
    if (p2 is String) return p<caret>
}

// IGNORE_K2
// ORDER: p2
// ORDER: p3
// ORDER: p1
