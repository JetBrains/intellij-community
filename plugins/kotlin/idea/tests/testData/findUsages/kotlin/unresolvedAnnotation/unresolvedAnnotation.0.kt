// PSI_ELEMENT: org.jetbrains.kotlin.psi.KtClass
// OPTIONS: usages, constructorUsages
// PSI_ELEMENT_AS_TITLE: "class Foo"

// IGNORE_PLATFORM_NATIVE: Java-specific test
// IGNORE_PLATFORM_JS: Java-specific test

open class <caret>Foo {
    open val foo = 1
}

