fun executeAction() {}

fun foo() {
    myFor@
    for (i in 1..10) {
        myWhile@
        while (x()) {
            myDo@
            do {
                ea<caret>
            } while (y())
        }
    }
}

// NUMBER: 0