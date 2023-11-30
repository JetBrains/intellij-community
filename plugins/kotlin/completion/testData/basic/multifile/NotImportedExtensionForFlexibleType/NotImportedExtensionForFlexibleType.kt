// FIR_COMPARISON
// FIR_IDENTICAL
package root

import java.util.List

fun foo(a: List<String>) {
    a[0].quoteIf<caret>
}

// EXIST: quoteIfNeeded
