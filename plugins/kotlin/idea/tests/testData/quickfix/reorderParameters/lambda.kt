// "Reorder parameters" "true"
fun foo(
    x: () -> String = { y<caret> },
    y: String = "OK"
) = Unit
