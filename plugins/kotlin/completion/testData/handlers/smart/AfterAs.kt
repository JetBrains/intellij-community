fun foo(p1: String, p2: Int){ }

fun bar(o: Any){
    foo(o as <caret>)
}