// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.idea.base.psi

import com.intellij.psi.PsiComment
import com.intellij.psi.PsiWhiteSpace
import org.jetbrains.kotlin.lexer.KtToken
import org.jetbrains.kotlin.lexer.KtTokens
import org.jetbrains.kotlin.psi.KtElement
import org.jetbrains.kotlin.psi.KtPsiFactory
import org.jetbrains.kotlin.psi.psiUtil.siblings

/**
 * Helper for editing comma-separated element lists in the Kotlin PSI tree, such as value parameter lists, value and type
 * argument lists, type parameter lists, and super type lists.
 *
 * The methods keep the separating commas consistent: a comma is inserted when an item is added next to existing ones, and the
 * adjacent comma is removed together with an item that is deleted.
 */
object EditCommaSeparatedListHelper {
    /**
     * Appends [item] to the comma-separated [list], inserting a separating comma when [allItems] is not empty.
     *
     * @param list the PSI element holding the comma-separated list (for example, a value parameter or argument list).
     * @param allItems the items currently present in [list].
     * @param item the item to add.
     * @param prefix the opening token of [list], used to place the very first item right after it; [KtTokens.LPAR] by default.
     * @return [item] as it was added to the tree.
     */
    @JvmOverloads
    fun <TItem : KtElement> addItem(list: KtElement, allItems: List<TItem>, item: TItem, prefix: KtToken = KtTokens.LPAR): TItem {
        return addItemBefore(list, allItems, item, null, prefix)
    }

    /**
     * Inserts [item] into the comma-separated [list] right after [anchor], adding the required comma.
     *
     * When [allItems] is empty, [item] becomes the only element (placed right after [prefix] when [list] starts with it).
     * When [anchor] is `null`, [item] is inserted before the first existing item.
     *
     * @param list the PSI element holding the comma-separated list.
     * @param allItems the items currently present in [list].
     * @param item the item to add.
     * @param anchor the item after which [item] is inserted, or `null` to insert before the first item; must be a child of [list].
     * @param prefix the opening token of [list]; [KtTokens.LPAR] by default.
     * @return [item] as it was added to the tree.
     */
    @Suppress("UNCHECKED_CAST")
    @JvmOverloads
    fun <TItem : KtElement> addItemAfter(
        list: KtElement,
        allItems: List<TItem>,
        item: TItem,
        anchor: TItem?,
        prefix: KtToken = KtTokens.LPAR
    ): TItem {
        assert(anchor == null || anchor.parent == list)
        if (allItems.isEmpty()) {
            return if (list.firstChild?.node?.elementType == prefix) {
                list.addAfter(item, list.firstChild) as TItem
            } else {
                list.add(item) as TItem
            }
        } else {
            var comma = KtPsiFactory(list.project).createComma()
            return if (anchor != null) {
                comma = list.addAfter(comma, anchor)
                list.addAfter(item, comma) as TItem
            } else {
                comma = list.addBefore(comma, allItems.first())
                list.addBefore(item, comma) as TItem
            }
        }
    }

    /**
     * Inserts [item] into the comma-separated [list] right before [anchor], adding the required comma.
     *
     * When [anchor] is `null`, [item] is appended after the last existing item; when [allItems] is empty, [item] becomes the
     * only element.
     *
     * @param list the PSI element holding the comma-separated list.
     * @param allItems the items currently present in [list].
     * @param item the item to add.
     * @param anchor the item before which [item] is inserted, or `null` to append; when not `null`, must be one of [allItems].
     * @param prefix the opening token of [list]; [KtTokens.LPAR] by default.
     * @return [item] as it was added to the tree.
     */
    @JvmOverloads
    fun <TItem : KtElement> addItemBefore(
        list: KtElement,
        allItems: List<TItem>,
        item: TItem,
        anchor: TItem?,
        prefix: KtToken = KtTokens.LPAR
    ): TItem {
        val anchorAfter: TItem? = if (allItems.isEmpty()) {
            assert(anchor == null)
            null
        } else {
            if (anchor != null) {
                val index = allItems.indexOf(anchor)
                assert(index >= 0)
                if (index > 0) allItems[index - 1] else null
            } else {
                allItems[allItems.size - 1]
            }
        }
        return addItemAfter(list, allItems, item, anchorAfter, prefix)
    }

    /**
     * Removes [item] from its comma-separated list together with the adjacent comma: the following one when present, otherwise
     * the preceding one.
     *
     * @param item the item to remove.
     */
    fun <TItem : KtElement> removeItem(item: TItem) {
        var comma = item.siblings(withItself = false).firstOrNull { it !is PsiWhiteSpace && it !is PsiComment }
        if (comma?.node?.elementType != KtTokens.COMMA) {
            comma = item.siblings(forward = false, withItself = false).firstOrNull { it !is PsiWhiteSpace && it !is PsiComment }
            if (comma?.node?.elementType != KtTokens.COMMA) {
                comma = null
            }
        }

        item.delete()
        comma?.delete()
    }
}
