package foo

private class A : CommonAbstract() {
    override fun foo(cause: Throwable?) {}
}

@OptIn(ExperimentalMultiplatform::class)
@AllowDifferentMembersInActual // New 'AbstractMutableCollection` supertype is added compared to the expect declaration
internal actual abstract class ExpectBase actual constructor() : I {
    actual abstract override fun foo(cause: Throwable?)
}
