// "Create local variable 'Unknown'" "false"
// ERROR: An annotation argument must be a compile-time constant
// ERROR: Unresolved reference: Unknown
// WITH_STDLIB

import kotlin.reflect.KClass

class Test {
    @Anno(Un<caret>known::class)
    fun test() {

    }

    annotation class Anno(val value: KClass<Test>) {

    }
}