// IntelliJ API Decompiler stub source generated from a class file
// Implementation of methods is not available

package test

public final class TypeAliases public constructor() {
    public final fun foo(a: () -> kotlin.Unit /* from: dependency.A */, b: (() -> kotlin.Unit /* from: dependency.A */) -> kotlin.Unit /* from: test.TypeAliases.B */, ta: kotlin.collections.Map<kotlin.collections.Map<kotlin.String, kotlin.Double>, kotlin.collections.Map<kotlin.Int, kotlin.Boolean>> /* from: test.Outer.Inner.TA<kotlin.Boolean> */): kotlin.Unit { /* compiled code */ }

    public typealias B = (dependency.A) -> kotlin.Unit

    @test.Ann private typealias Parametrized<E, F> = kotlin.collections.Map<E, F>
}
