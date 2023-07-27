fun test<caret>(s: String?): Int {
    if (s != null) {
        return@test 1
    }
    return 0
}

//HIGHLIGHTED: return@test 1
//HIGHLIGHTED: test
//HIGHLIGHTED: return 0
