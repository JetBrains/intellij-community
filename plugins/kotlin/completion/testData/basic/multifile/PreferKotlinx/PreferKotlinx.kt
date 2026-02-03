// FIR_IDENTICAL
// FIR_COMPARISON
package org.test

// The test here shows that all kotlinx.datetime classes are prioritized over all java.time classes
val a = LocalDat<caret>

// WITH_ORDER
// EXIST: { lookupString: "LocalDate", tailText: " (kotlinx.datetime)"}
// EXIST: { lookupString: "LocalDateTime", tailText: " (kotlinx.datetime)"}
// EXIST: { lookupString: "LocalDate", tailText: " (java.time)"}
// EXIST: { lookupString: "LocalDateTime", tailText: " (java.time)"}
