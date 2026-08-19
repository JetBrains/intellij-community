fun foo(
    a: String,
    b: String,
) = Unit

fun bar(): String = "bar"

fun usage() {
    foo(
        a = bar(),
        b = b<caret>, // comment
    )
}

// ELEMENT: bar
// TAIL_TEXT: () (<root>)
