fun foo(
    a: String,
    b: String,
    c: String,
) = Unit

fun bar(): String = "bar"

fun usage() {
    foo(
        a = bar(),
        b = b<caret>,
        /**
        * doc
        */
        bar()
    )
}

// ELEMENT: bar
// TAIL_TEXT: () (<root>)
