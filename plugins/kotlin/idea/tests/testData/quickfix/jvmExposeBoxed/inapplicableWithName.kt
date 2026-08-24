// "Remove argument" "true"
// WITH_STDLIB
// K2_ERROR: INAPPLICABLE_JVM_EXPOSE_BOXED_WITH_NAME
@file:OptIn(ExperimentalStdlibApi::class)

@JvmExposeBoxed("Bo<caret>xed")
@JvmInline
value class C(val x: Int)

// FUS_K2_QUICKFIX_NAME: org.jetbrains.kotlin.idea.k2.codeinsight.fixes.RemoveJvmExposeBoxedNameFix
