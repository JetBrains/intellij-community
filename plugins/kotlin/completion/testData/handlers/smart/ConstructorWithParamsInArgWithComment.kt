class Apple(x: Int)
fun makeJuice(a: Apple) {}

fun test() {
    makeJuice(
         <caret> // comment
    )
}

// ELEMENT: Apple
// TAIL_TEXT: (x: Int) (<root>)
