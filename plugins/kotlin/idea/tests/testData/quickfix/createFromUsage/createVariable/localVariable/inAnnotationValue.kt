// "Create local variable 'Unknown'" "false"
// ERROR: An annotation argument must be a compile-time constant
// ERROR: Unresolved reference: Unknown
// WITH_STDLIB
// K2_AFTER_ERROR: ANNOTATION_ARGUMENT_MUST_BE_CONST
// K2_AFTER_ERROR: UNRESOLVED_REFERENCE
// K2_ERROR: ANNOTATION_ARGUMENT_MUST_BE_CONST
// K2_ERROR: UNRESOLVED_REFERENCE

import kotlin.reflect.KClass

class Test {
    @Anno(Un<caret>known::class)
    fun test() {

    }

    annotation class Anno(val value: KClass<Test>) {

    }
}