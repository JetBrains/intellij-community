// "Create object 'ClassCheckerImpl'" "true"
// IGNORE_K1
interface FirDeclarationChecker<T>

class FirClass

typealias ClassChecker = FirDeclarationChecker<FirClass>

interface Checkers {
    val classCheckers: Set<ClassChecker>
}

class AdditionalCheckers : Checkers {
    override val classCheckers: Set<ClassChecker> = setOf(ClassCheckerImpl)<caret>

}
// FUS_K2_QUICKFIX_NAME: org.jetbrains.kotlin.idea.k2.codeinsight.quickFixes.createFromUsage.CreateKotlinClassAction