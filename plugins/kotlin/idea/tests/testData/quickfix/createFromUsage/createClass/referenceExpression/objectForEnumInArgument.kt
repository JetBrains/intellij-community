// "Create object 'RED'" "false"
// ERROR: Unresolved reference: RED
enum class SampleEnum {}

fun usage() {
    foo(SampleEnum.RED<caret>)
}

fun foo(sample: SampleEnum) {}