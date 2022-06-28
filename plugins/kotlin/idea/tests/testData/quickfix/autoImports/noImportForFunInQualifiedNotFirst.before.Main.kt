// "Import" "false"
// ACTION: Do not show return expression hints
// ACTION: Rename reference
// ERROR: Unresolved reference: externalFun

package testing

fun some() {
  testing.<caret>externalFun()
}