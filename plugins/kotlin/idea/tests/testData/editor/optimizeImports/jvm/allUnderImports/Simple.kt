// IGNORE_K2
// NAME_COUNT_TO_USE_STAR_IMPORT: 1
// WITH_MESSAGE: "Removed 3 imports, added 1 import"
package ppp

import java.util.HashMap
import java.util.ArrayList
import java.io.File

fun foo(c: C) {
    val v = HashMap<File, ArrayList<Int>>()
}
