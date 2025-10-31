package p1

open class QualifiedTopLevelClassDiffPackage {

    companion object {
        @Deprecated("Use qualifiedCompanionPropDiffPackage instead", ReplaceWith("qualifiedCompanionPropDiffPackageNew"))
        var qualifiedCompanionPropDiffPackage = 0
        var qualifiedCompanionPropDiffPackageNew = 0
    }
}