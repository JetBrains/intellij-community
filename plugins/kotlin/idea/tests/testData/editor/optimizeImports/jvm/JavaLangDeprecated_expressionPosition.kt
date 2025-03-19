// IGNORE_K1
// IGNORE_K2
// See KTIJ-32231
package test

import java.lang.Deprecated

import kotlin.String
import kotlin.run

fun test(): String {
    Deprecated::class
    return run { "" }
}