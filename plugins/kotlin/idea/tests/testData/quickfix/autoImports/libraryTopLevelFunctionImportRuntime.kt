// "Import function 'measureNanoTime'" "true"
// WITH_STDLIB
package some

fun testFun() {
  <caret>measureNanoTime({})
}

// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.ImportFix