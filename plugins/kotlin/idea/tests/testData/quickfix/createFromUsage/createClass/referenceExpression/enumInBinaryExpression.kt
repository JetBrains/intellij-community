// "Create enum constant 'RED'" "true"
enum class SampleEnum {}

fun usage(sample: SampleEnum) {
    if (sample == SampleEnum.RED<caret>) {
    }
}
// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.createFromUsage.createClass.CreateClassFromUsageFix