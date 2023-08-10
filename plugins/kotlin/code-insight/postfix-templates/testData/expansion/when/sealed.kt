fun test(p: Penguin) {
    p<caret>
}

sealed class Penguin {
    sealed class Aptenodytes : Penguin() {
        object King : Aptenodytes()
        object Emperor : Aptenodytes()
    }
    sealed class Pygoscelis : Penguin() {
        object Gentoo : Pygoscelis()
    }
    class Other(val name: String) : Penguin()
}