// WITH_STDLIB
// COMPILER_ARGUMENTS: -opt-in=test.Marker

package test

@RequiresOptIn
annotation class Marker

@Marker
fun experimentalApi() {}

@OptIn(<caret>Marker::class)
fun useExperimentalApi() {
    experimentalApi()
}
