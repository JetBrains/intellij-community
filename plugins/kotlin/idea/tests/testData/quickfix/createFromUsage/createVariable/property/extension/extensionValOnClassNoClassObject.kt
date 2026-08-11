// "Create extension property 'A.Companion.foo'" "true"
// K2_AFTER_ERROR: EXTENSION_PROPERTY_MUST_HAVE_ACCESSORS_OR_BE_ABSTRACT
// K2_AFTER_ERROR: UNRESOLVED_REFERENCE
// K2_AFTER_ERROR: UNRESOLVED_REFERENCE
// K2_ERROR: UNRESOLVED_REFERENCE
class A<T>(val n: T)

fun test() {
    val a: Int = A.<caret>foo
}
// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.createFromUsage.createCallable.CreateExtensionCallableFromUsageFix
// FUS_K2_QUICKFIX_NAME: org.jetbrains.kotlin.idea.k2.codeinsight.quickFixes.createFromUsage.K2CreatePropertyFromUsageBuilder$CreatePropertyFromUsageAction