// "Remove @JvmExposeBoxed annotation" "true"
// WITH_STDLIB
// K2_ERROR: JVM_EXPOSE_BOXED_CANNOT_EXPOSE_PRIVATE
@file:OptIn(ExperimentalStdlibApi::class)

@JvmE<caret>xposeBoxed("fooBoxed") private fun foo(x: UInt) {}

// FUS_K2_QUICKFIX_NAME: org.jetbrains.kotlin.idea.inspections.RemoveAnnotationFix
