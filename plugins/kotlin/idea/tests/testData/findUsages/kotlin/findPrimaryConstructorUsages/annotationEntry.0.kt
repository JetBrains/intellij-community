// PSI_ELEMENT: org.jetbrains.kotlin.psi.KtClass
// OPTIONS: usages, constructorUsages
// PSI_ELEMENT_AS_TITLE: "annotation class MyAnnotation"
annotation class MyAnnotation()

@<caret>MyAnnotation
fun test() {
    MyAnnotation::class.java
}


