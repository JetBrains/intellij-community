suspend fun test(lhs: Foo, rhs: Foo) {
    lhs <lineMarker text="Suspend function call 'join()'">join</lineMarker> rhs
}

class Foo {
    suspend infix fun join(other: Foo): Foo {
        return other
    }
}