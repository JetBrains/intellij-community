package first

import second.<caret>

// IGNORE_K2
// when the test passes in K2 `InImportHighOrderTopLevelFunNoTailText` may be removed - it is a duplicate without checking tailText
// EXIST: { itemText: "topLevelFun1", tailText: "(p: (String, Int) -> Unit) (second)", attributes: "" }
// EXIST: { itemText: "topLevelFun2", tailText: "(p: () -> Unit) (second)", attributes: "" }
// NOTHING_ELSE