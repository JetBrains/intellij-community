class Apple
fun makeApple(): Apple { return Apple() }

fun test() {

    val tree: () -> Apple = {
         <caret>// comment
    }
}

// ELEMENT: makeApple
// TAIL_TEXT: () (<root>)