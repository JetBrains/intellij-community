fun foo(p: Int): Int = p
fun bar(): Int = p

fun f(): Int {
    return <caret>foo(1)
}

//ELEMENT: bar
//CHAR: \t
