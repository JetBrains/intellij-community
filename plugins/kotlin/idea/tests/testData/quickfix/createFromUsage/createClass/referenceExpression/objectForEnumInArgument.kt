// "Create object 'RED'" "false"
// ERROR: Unresolved reference: RED
// K2_AFTER_ERROR: Unresolved reference 'RED'.
enum class SampleEnum {}

fun usage() {
    foo(SampleEnum.RED<caret>)
}

fun foo(sample: SampleEnum) {}