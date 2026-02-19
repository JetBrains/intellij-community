// WITH_STDLIB
// FIX: Add non-null asserted (java.lang.String.valueOf(1)!!) call

fun foo<caret>() = java.lang.String.valueOf(1)