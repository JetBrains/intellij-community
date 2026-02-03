// MISSING_ACTUALS:
// PLATFORM: Linux
// FILE: expect.kt
expect class Expect

// PLATFORM: LinuxX64
// FILE: expect.kt
class Other
actual typealias Expect = Other

// PLATFORM: LinuxArm64
// FILE: expect.kt
class Other
actual typealias Expect = Other