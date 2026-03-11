// "Create class 'BookKeeper'" "true"
// K2_AFTER_ERROR: Return type mismatch: expected 'Man', actual 'BookKeeper'.
// IGNORE_K1
// K2_ERROR: Unresolved reference 'BookKeeper'.
package pack

import pack.Currrency.EUR

enum class Currrency { EUR }
class Item(val p1: Double, p2: Currrency)
class Transaction(vararg val p: Item)
class Man

fun place(): Man {
    val transactions = listOf(Transaction(Item(10.0, EUR), Item(10.0, EUR)))
    return BookKee<caret>per(transactions)
}
// FUS_K2_QUICKFIX_NAME: org.jetbrains.kotlin.idea.k2.codeinsight.quickFixes.createFromUsage.CreateKotlinClassAction