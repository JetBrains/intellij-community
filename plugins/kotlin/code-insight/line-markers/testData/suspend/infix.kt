suspend fun test(lhs: Foo, rhs: Foo) {
    lhs <lineMarker text="Suspend operator call &apos;join()&apos;">join</lineMarker> rhs
}

class Foo {
    suspend infix fun join(other: Foo): Foo {
        return other
    }
}