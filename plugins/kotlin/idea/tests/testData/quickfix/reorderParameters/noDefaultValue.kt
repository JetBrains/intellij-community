// "Reorder parameters" "true"
fun foo(
    x: String = y<caret>,
    y: String
) = Unit
