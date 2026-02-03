fun main() {
    test1.test2.test3()
    test1.test<caret>
}

// EXIST: {"lookupString":"test3","tailText":"()"}
// EXIST: {"lookupString":"test2"}
// NOTHING_ELSE