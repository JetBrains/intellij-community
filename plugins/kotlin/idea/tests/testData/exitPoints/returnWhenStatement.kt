fun yy(): Int = 5

fun f(a: Int): Int {
    return<caret> when {
        a < 0 -> {
            val q = 1
            2 * 2
        }
        a > 0 -> {
            2 * (2 + 2)
        }
        else -> yy()
    }
}

//HIGHLIGHTED: return
//HIGHLIGHTED: f
//HIGHLIGHTED: 2 * (2 + 2)
//HIGHLIGHTED: 2 * 2
//HIGHLIGHTED: yy()