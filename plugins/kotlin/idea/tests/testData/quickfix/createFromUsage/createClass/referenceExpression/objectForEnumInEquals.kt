// "Create object 'RED'" "false"
// ACTION: Create enum constant 'RED'
// ACTION: Create extension property 'SampleEnum.Companion.RED'
// ACTION: Create member property 'SampleEnum.Companion.RED'
// ACTION: Expand boolean expression to 'if else'
// ACTION: Rename reference
// ERROR: Unresolved reference: RED
enum class SampleEnum {}

fun usage(sample: SampleEnum) {
    if (sample == SampleEnum.RED<caret>) {
    }
}