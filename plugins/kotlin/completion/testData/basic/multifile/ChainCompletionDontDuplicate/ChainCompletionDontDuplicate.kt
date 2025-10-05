
fun test() {
    Foo.val<caret>
}

// EXIST: .val
// EXIST: value
// NUMBER: 2
// NOTHING_ELSE