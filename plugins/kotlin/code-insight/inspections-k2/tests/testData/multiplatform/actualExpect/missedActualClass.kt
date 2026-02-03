// MISSING_ACTUALS: LinuxArm64
// PLATFORM: Linux
// FILE: expect.kt
expect fun test(): Unit

// PLATFORM: LinuxX64
// FILE: expect.kt
actual fun test(): Unit {}

// PLATFORM: LinuxArm64
// FILE: expect.kt
fun test(): Unit {}