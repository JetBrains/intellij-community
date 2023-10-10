package first

import second.<caret>

// IGNORE_K2
// EXIST: { itemText: "topLevelFun1", tailText: "(p: (String, Int) -> Unit) (second)", attributes: "" }
// EXIST: { itemText: "topLevelFun2", tailText: "(p: () -> Unit) (second)", attributes: "" }
// NOTHING_ELSE