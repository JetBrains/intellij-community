// PLATFORM: Common
// FILE: common.kt

import kotlin.jvm.JvmOverloads

@JvmOverloads
expect fun withDefaultsInExpect(flag: Boolean = false): Boolean

expect fun withoutDefaults(flag: Boolean): Boolean

// PLATFORM: Jvm
// FILE: jvm.kt

@JvmOverloads
actual fun withDefaultsInExpect(flag: Boolean): Boolean = flag

<warning descr="[OVERLOADS_WITHOUT_DEFAULT_ARGUMENTS]">@JvmOverloads</warning>
actual fun withoutDefaults(flag: Boolean): Boolean = flag

fun useDefaultsFromExpect(): Boolean = withDefaultsInExpect()
