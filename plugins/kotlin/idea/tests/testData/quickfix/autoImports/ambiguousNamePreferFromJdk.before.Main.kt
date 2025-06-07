// "Import class 'Date'" "true"
// ERROR: Unresolved reference: Date

import java.util.*
import dependency.*

fun foo(d: Date<caret>) {
}

// IGNORE_K2