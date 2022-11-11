fun test(a: Any) {
    when (a) {
        <caret>is Int, is Long -> println(0)
        is Float, is Double -> println(0)
        is String -> println(1)
    }
}