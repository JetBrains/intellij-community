fun foo() {
    for (i in 1..10) {
        if (i > 3) {
            <caret>if (i > 5) {
                bar()
            }
            break
        }
        bar()
    }
}

fun bar(){}
