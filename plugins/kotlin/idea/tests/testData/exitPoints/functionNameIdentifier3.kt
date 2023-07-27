fun test(s: String?): Int =
    if (s != null) {
        1<caret>
    } else 0

//HIGHLIGHTED: 1
//HIGHLIGHTED: test
//HIGHLIGHTED: 0
