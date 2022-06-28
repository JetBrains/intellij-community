// "Delete redundant extension property" "false"
// ACTION: Create test
// ACTION: Do not show return expression hints
// ACTION: Remove explicit type specification

var Thread.<caret>priority: Int
  get() = getPriority() + 1
  set(value) {
      setPriority(value)
  }
