// "Remove @JvmExposeBoxed annotation" "true"
// WITH_STDLIB
// K2_ERROR: JVM_EXPOSE_BOXED_CANNOT_EXPOSE_OPEN_ABSTRACT
@file:OptIn(ExperimentalStdlibApi::class)

open class C {
    @JvmE<caret>xposeBoxed("fooBoxed") open fun foo(x: UInt) {}
}

// FUS_K2_QUICKFIX_NAME: org.jetbrains.kotlin.idea.inspections.RemoveAnnotationFix
