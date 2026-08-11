// "Create object 'RED'" "false"
// ERROR: Unresolved reference: RED
// K2_AFTER_ERROR: UNRESOLVED_REFERENCE
// K2_ERROR: UNRESOLVED_REFERENCE
enum class SampleEnum {}

fun usage() {
    foo(SampleEnum.RED<caret>)
}

fun foo(sample: SampleEnum) {}