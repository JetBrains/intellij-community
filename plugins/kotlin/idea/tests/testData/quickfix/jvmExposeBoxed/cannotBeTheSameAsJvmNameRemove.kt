// "Remove argument" "true"
// WITH_STDLIB
// K2_AFTER_ERROR: JVM_EXPOSE_BOXED_REQUIRES_NAME
// K2_ERROR: JVM_EXPOSE_BOXED_CANNOT_BE_THE_SAME_AS_JVM_NAME
@file:OptIn(ExperimentalStdlibApi::class)

@JvmName("fooJvm")
@JvmExposeBoxed("fo<caret>oJvm")
fun foo(): UInt = 1u

// FUS_K2_QUICKFIX_NAME: org.jetbrains.kotlin.idea.k2.codeinsight.fixes.RemoveJvmExposeBoxedNameFix
