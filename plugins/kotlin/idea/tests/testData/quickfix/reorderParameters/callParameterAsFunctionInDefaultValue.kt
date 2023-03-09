// "Reorder parameters" "true"
fun foo(
    x: String = <caret>y(),
    y: () -> String = { "OK" }
) = Unit
