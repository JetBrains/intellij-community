// "Import class 'Date'" "true"
// ERROR: Unresolved reference: Date

import java.util.*
import dependency.*
import dependency.Date

fun foo(d: Date<caret>) {
}

// IGNORE_K2