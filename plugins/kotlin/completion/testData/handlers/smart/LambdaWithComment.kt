class Apple()
fun makeJuice(a: Apple) {}

val tree: () -> Apple = {
        <caret> // comment
}

// ELEMENT: Apple
// TAIL_TEXT: () (<root>)
