// FIR_COMPARISON
// FIR_IDENTICAL
// WITH_STDLIB
// MIN_JAVA_VERSION: 6

import java.sql.DriverPropertyInfo

val some = Driver<caret>

// INVOCATION_COUNT: 2
// ORDER: Driver
// ORDER: DriverPropertyInfo
// ORDER: DriverManager
