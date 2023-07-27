fun test<caret>(s: String?): Int =
    if (s != null) {
        1
    } else 0

//HIGHLIGHTED: 1
//HIGHLIGHTED: test
//HIGHLIGHTED: 0
