// WITH_RUNTIME
// COMPILER_ARGUMENTS: -opt-in=kotlin.RequiresOptIn

import kotlin.OptIn as Consent

@RequiresOptIn
annotation class Marker

@Marker
fun foo() {}

<caret>@Consent(Marker::class)
fun bar() {}
