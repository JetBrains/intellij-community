// FIR_COMPARISON
// FIR_IDENTICAL
import java.io.File

/**
 * [File.par<caret>]
 */
fun test(f: File) {}

// EXIST: {"lookupString":"getParent","tailText":"()","typeText":"String!","icon":"Method","attributes":"bold"}
// ABSENT: parent
// ABSENT: getPath