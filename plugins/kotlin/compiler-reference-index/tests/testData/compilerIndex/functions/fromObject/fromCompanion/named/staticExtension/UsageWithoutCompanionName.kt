package one.two

fun usageWithoutCompanionName() {
    with(KotlinClass) { Receiver().staticExtension(24) }
}