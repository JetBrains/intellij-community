// "Replace with 'Bar'" "true"
class Test {
    @get:<caret>Foo
    val s: String = ""
}

@Deprecated("Replace with Bar", ReplaceWith("Bar"))
@Target(AnnotationTarget.PROPERTY_GETTER)
annotation class Foo

@Target(AnnotationTarget.PROPERTY_GETTER)
annotation class Bar
// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.replaceWith.DeprecatedSymbolUsageFix
// FUS_K2_QUICKFIX_NAME: org.jetbrains.kotlin.idea.k2.codeinsight.fixes.replaceWith.DeprecatedSymbolUsageFix