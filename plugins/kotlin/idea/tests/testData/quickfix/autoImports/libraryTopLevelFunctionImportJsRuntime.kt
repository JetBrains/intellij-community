// "Import function 'cos'" "true"
// JS
package some

fun testFun() {
  <caret>cos(0.0)
}

// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.ImportFix
/* IGNORE_K2 */