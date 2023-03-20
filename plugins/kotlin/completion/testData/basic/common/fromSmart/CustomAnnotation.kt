package one.two

@Target(AnnotationTarget.TYPE)
annotation class CustomAnnotation

class MyClass

open class A {
    open fun returnString(): @CustomAnnotation MyClass = MyClass()
}

class B : A() {
    override fun returnString() = null as MyClas<caret>
}

// EXIST: {"lookupString":"MyClass","tailText":" (one.two)","module":"light_idea_test_case","attributes":"","allLookupStrings":"MyClass","itemText":"MyClass"}
// NUMBER: 1
// FIR_COMPARISON
// FIR_IDENTICAL