// "Create local variable 'Unknown'" "false"
// ACTION: Add 'value =' to argument
// ACTION: Create annotation 'Unknown'
// ACTION: Create class 'Unknown'
// ACTION: Create enum 'Unknown'
// ACTION: Create interface 'Unknown'
// ACTION: Create object 'Unknown'
// ACTION: Create parameter 'Unknown'
// ACTION: Make internal
// ACTION: Make private
// ACTION: Make protected
// ACTION: Rename reference
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