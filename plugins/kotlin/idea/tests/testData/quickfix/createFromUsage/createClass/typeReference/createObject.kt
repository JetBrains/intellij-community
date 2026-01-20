// "Create object 'ClassCheckerImpl'" "true"
interface FirDeclarationChecker<T>

class FirClass

typealias ClassChecker = FirDeclarationChecker<FirClass>

interface Checkers {
    val classCheckers: Set<ClassChecker>
}

class AdditionalCheckers : Checkers {
    override val classCheckers: Set<ClassChecker> = setOf(ClassCheckerImpl)<caret>

}
// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.createFromUsage.createClass.CreateClassFromUsageFix
// FUS_K2_QUICKFIX_NAME: org.jetbrains.kotlin.idea.k2.codeinsight.quickFixes.createFromUsage.CreateKotlinClassAction