fun test() {
    foo({ 1 }, { 2 }) {<caret> it.length }
}

fun foo(a: (Unit) -> Int, b: (Unit) -> Int, c: (String) -> Int) {}
