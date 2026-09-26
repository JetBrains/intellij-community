class Apple()
fun makeJuice(a: Apple) {}

fun test() {
    makeJuice(
         <caret> // comment
    )
}

// ELEMENT: Apple
// TAIL_TEXT: () (<root>)
