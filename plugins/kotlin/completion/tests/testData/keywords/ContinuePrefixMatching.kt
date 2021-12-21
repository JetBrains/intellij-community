fun tiny() {}

fun foo() {
    myFor@
    for (i in 1..10) {
        myWhile@
        while (x()) {
            myDo@
            do {
                tin<caret>
            } while (y())
        }
    }
}

// NUMBER: 0
