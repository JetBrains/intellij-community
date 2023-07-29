fun f(a: Int): Int {
    return<caret> if (a < 5) {
        val q = 1
        1
    }
    else {
        2
    }
}

//HIGHLIGHTED: return
//HIGHLIGHTED: f
//HIGHLIGHTED: 1
//HIGHLIGHTED: 2