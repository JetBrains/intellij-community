// "Create extension property 'Unit.foo'" "true"
// WITH_STDLIB
// K2_AFTER_ERROR: EXTENSION_PROPERTY_MUST_HAVE_ACCESSORS_OR_BE_ABSTRACT
// K2_ERROR: UNRESOLVED_REFERENCE

fun test() {
    val a: Int = Unit.<caret>foo
}

// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.createFromUsage.createCallable.CreateExtensionCallableFromUsageFix
// FUS_K2_QUICKFIX_NAME: org.jetbrains.kotlin.idea.k2.codeinsight.quickFixes.createFromUsage.K2CreatePropertyFromUsageBuilder$CreatePropertyFromUsageAction