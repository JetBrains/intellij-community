// FIR_COMPARISON
// FIR_IDENTICAL
package Package

/**
 * [Package.<caret>]
 */
class Package {
    fun memberInClass() {}
}

fun topLevelInPackage() {}

// EXIST: {"lookupString":"memberInClass","tailText":"()","typeText":"Unit","icon":"Method","attributes":""}
// EXIST: {"lookupString":"topLevelInPackage","tailText":"() (Package)","typeText":"Unit","icon":"Function","attributes":""}
// IGNORE_K2