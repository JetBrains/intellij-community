// FIR_IDENTICAL
// FIR_COMPARISON
package org.test

// The test here shows that all kotlinx.datetime classes are prioritized over all java.time classes
val a = Flow<caret>

// WITH_ORDER
// EXIST: { lookupString: "Flow", tailText: " (kotlinx.coroutines.flow)"}
// EXIST: { lookupString: "Flow", tailText: " (java.util.concurrent)"}
