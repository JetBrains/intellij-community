fun test(p: Penguin) {
    when (p) {
        Penguin.Aptenodytes.Emperor -> TODO()
        Penguin.Aptenodytes.King -> TODO()
        is Penguin.Other -> TODO()
        Penguin.Pygoscelis.Gentoo -> TODO()
    }
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