fun foo(i: Int){}
fun foo(i: Int, c: Char){}

fun bar(o: Any) {
    foo(o as <caret>)
}

// AUTOCOMPLETE_SETTING: true