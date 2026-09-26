// MISSING_ACTUALS: LinuxArm64
// PLATFORM: Linux
// FILE: expect.kt
class B

expect class A {
    typealias C = B
}

// PLATFORM: LinuxX64
// FILE: actual.kt
actual class A