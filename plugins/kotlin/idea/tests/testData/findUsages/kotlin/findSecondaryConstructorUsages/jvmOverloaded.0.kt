// PSI_ELEMENT: org.jetbrains.kotlin.psi.KtSecondaryConstructor
// OPTIONS: usages
// PSI_ELEMENT_AS_TITLE: "constructor A(Int = ..., Double = ..., String = ...)"
public class A {
    @JvmOverloads
    <caret>constructor(x: Int = 0, y: Double = 0.0, z: String = "0")
}

