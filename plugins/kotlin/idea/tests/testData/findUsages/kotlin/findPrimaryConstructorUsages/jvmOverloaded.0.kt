// PSI_ELEMENT: org.jetbrains.kotlin.psi.KtPrimaryConstructor
// OPTIONS: usages
// PSI_ELEMENT_AS_TITLE: "constructor A(Int = ..., Double = ..., String = ...)"
public class A @JvmOverloads <caret>constructor(
    public val x: Int = 0,
    public val y: Double = 0.0,
    public val z: String = "0"
)
