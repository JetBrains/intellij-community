// PSI_ELEMENT: org.jetbrains.kotlin.psi.KtSecondaryConstructor
// OPTIONS: usages
// PSI_ELEMENT_AS_TITLE: "constructor A(Int = ..., Double = ..., String = ...)"

// IGNORE_PLATFORM_JS: Java-specific code
// IGNORE_PLATFORM_NATIVE: Java-specific code

public class A {
    @JvmOverloads
    <caret>constructor(x: Int = 0, y: Double = 0.0, z: String = "0")
}

