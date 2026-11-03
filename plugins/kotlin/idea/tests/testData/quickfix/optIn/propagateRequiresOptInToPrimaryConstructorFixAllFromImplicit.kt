// "Apply all 'Propagate opt-in requirement' fixes in file" "true"
// K2_ERROR: OPT_IN_USAGE_ERROR
// K2_ERROR: OPT_IN_USAGE_ERROR
// K2_ERROR: OPT_IN_USAGE_ERROR
// K2_ERROR: OPT_IN_USAGE_ERROR
// K2_ERROR: OPT_IN_USAGE_ERROR

@RequiresOptIn(level = RequiresOptIn.Level.ERROR)
@Target(AnnotationTarget.FUNCTION, AnnotationTarget.CONSTRUCTOR)
annotation class Ann

abstract class X @Ann constructor()
abstract class XArg @Ann constructor(val n: Int)

class Y : <caret>X()
internal class A2<T> : X()
class A5 : XArg(42)

class A3(val v: Int) : X()
data class F2(val a: Int) : X()

// FUS_QUICKFIX_NAME: com.intellij.codeInsight.daemon.impl.actions.FixAllHighlightingProblems
