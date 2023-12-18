package first

import second.<caret>

// EXIST: { itemText: "topLevelFun1", tailText: "(p: (String, Int) -> Unit) (second)", attributes: "" }
// EXIST: { itemText: "topLevelFun2", tailText: "(p: () -> Unit) (second)", attributes: "" }
// NOTHING_ELSE