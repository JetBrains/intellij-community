// "Import function 'measureNanoTime'" "true"
// WITH_STDLIB
package some

fun testFun() {
  <caret>measureNanoTime({})
}

// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.ImportFix
// FUS_K2_QUICKFIX_NAME: org.jetbrains.kotlin.idea.k2.codeinsight.fixes.imprt.ImportQuickFix