fun foo() {
    list.filter { it !i<caret> }
}

// EXIST: in
// EXIST: is
// NOTHING_ELSE
