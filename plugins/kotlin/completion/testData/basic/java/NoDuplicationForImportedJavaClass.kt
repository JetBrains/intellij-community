// FIR_IDENTICAL
// FIR_COMPARISON

import java.io.InputStreamReader

val x = InputStreamReader<caret>

// INVOCATION_COUNT: 2
// EXIST: { lookupString:"InputStreamReader", tailText:" (java.io)", icon: "RowIcon(icons=[Class, null])"}
// NOTHING_ELSE
