// "Add name to @JvmExposeBoxed" "true"
// WITH_STDLIB
// K2_ERROR: JVM_EXPOSE_BOXED_REQUIRES_NAME
@file:OptIn(ExperimentalStdlibApi::class)

@JvmExpo<caret>seBoxed
fun `foo"bar`(): UInt = 1u

// FUS_K2_QUICKFIX_NAME: org.jetbrains.kotlin.idea.k2.codeinsight.fixes.AddJvmExposeBoxedNameFix
