fun foo(s: String, i: Int){}
fun foo(c: Char){}

fun bar(b: Boolean, s: String){
    foo(if (b) "abc" else <caret>)
}

// ELEMENT: s
