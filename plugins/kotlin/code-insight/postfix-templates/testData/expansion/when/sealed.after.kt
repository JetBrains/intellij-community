fun test(p: Penguin) {
    // For some reason, 'SealedClassInheritorsProviderIdeImpl' works incorrectly here
    when (p) {
        is Penguin -> TODO()
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