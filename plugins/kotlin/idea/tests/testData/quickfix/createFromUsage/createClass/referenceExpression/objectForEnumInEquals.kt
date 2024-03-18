// "Create object 'RED'" "false"
// ERROR: Unresolved reference: RED
enum class SampleEnum {}

fun usage(sample: SampleEnum) {
    if (sample == SampleEnum.RED<caret>) {
    }
}