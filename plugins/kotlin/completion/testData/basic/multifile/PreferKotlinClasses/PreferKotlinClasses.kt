// FIR_IDENTICAL
// FIR_COMPARISON

package org.test

val a = FooCla<caret>

// WITH_ORDER
// EXIST: { lookupString: "FooClass", tailText: " (org.kotlin)"}
// EXIST: { lookupString: "FooClass", tailText: " (org.java)"}
// NOTHING_ELSE
