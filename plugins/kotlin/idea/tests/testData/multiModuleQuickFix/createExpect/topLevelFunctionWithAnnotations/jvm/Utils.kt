// "Create expected function in common module testModule_Common" "true"
// DISABLE-ERRORS
// IGNORE_K2

actual fun <T : @SimpleA List<@SimpleA List<@SimpleA String>>>@receiver:SimpleA String.<caret>myExtension(@SimpleA a: @SimpleA List<@SimpleA List<@SimpleA String>>) {
    println(this)
}