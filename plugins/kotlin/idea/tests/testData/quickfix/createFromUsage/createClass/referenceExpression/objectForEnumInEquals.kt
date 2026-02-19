// "Create object 'RED'" "false"
// ERROR: Unresolved reference: RED
// IGNORE_K2
enum class SampleEnum {}

fun usage(sample: SampleEnum) {
    if (sample == SampleEnum.RED<caret>) {
    }
}