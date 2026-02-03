typealias Handler = (name: String) -> String

fun x(handler: Handler): String {
    return handler(<caret>)
}

