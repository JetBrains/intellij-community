suspend fun test(lhs: Foo, rhs: Foo) {
    lhs <lineMarker text="Suspend operator call &apos;plus()&apos;">+</lineMarker> rhs
}

class Foo {
    suspend operator fun plus(other: Foo): Foo {
        return other
    }
}