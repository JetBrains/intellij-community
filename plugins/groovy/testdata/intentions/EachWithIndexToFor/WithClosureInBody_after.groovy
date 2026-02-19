for (int idx, int <caret>val in []) {
    if (val == 2) {
        println 2
    }
    if (val == 3) {
        println { String s ->
            println s
        }
    }
}