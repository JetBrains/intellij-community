// "Create local variable 'Unknown'" "false"
// ERROR: An annotation argument must be a compile-time constant
// ERROR: Unresolved reference: Unknown
// WITH_STDLIB
// K2_ERROR: Annotation argument must be a compile-time constant.
// K2_ERROR: Unresolved reference 'Unknown'.
// K2_AFTER_ERROR: Annotation argument must be a compile-time constant.
// K2_AFTER_ERROR: Unresolved reference 'Unknown'.

import kotlin.reflect.KClass

class Test {
    @Anno(Un<caret>known::class)
    fun test() {

    }

    annotation class Anno(val value: KClass<Test>) {

    }
}