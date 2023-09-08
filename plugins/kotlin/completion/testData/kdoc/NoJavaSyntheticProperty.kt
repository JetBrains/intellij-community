// FIR_COMPARISON
// FIR_IDENTICAL
import java.io.File

/**
 * [File.par<caret>]
 */
fun test(f: File) {}

// EXIST: {"lookupString":"getParent","tailText":"()","typeText":"String!","icon":"fileTypes/javaClass.svg","attributes":""}
// ABSENT: parent
// ABSENT: getPath
// IGNORE_K2