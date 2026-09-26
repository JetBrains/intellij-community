// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.github.ui.cloneDialog

import org.jetbrains.plugins.github.api.data.GithubAuthenticatedUser
import org.jetbrains.plugins.github.api.data.GithubRepo
import org.jetbrains.plugins.github.authentication.accounts.GithubAccount
import javax.swing.AbstractListModel

internal class GHCloneDialogRepositoryListModel : AbstractListModel<GHRepositoryListItem>() {

  private val itemsByAccount = LinkedHashMap<GithubAccount, MutableList<GHRepositoryListItem>>()
  private val repositoriesByAccount = hashMapOf<GithubAccount, MutableSet<GithubRepo>>()

  override fun getSize(): Int = itemsByAccount.values.sumOf { it.size }

  override fun getElementAt(index: Int): GHRepositoryListItem {
    var offset = 0
    for ((_, items) in itemsByAccount) {
      if (index >= offset + items.size) {
        offset += items.size
        continue
      }
      return items[index - offset]
    }
    throw IndexOutOfBoundsException(index)
  }

  fun getItemAt(index: Int): Pair<GithubAccount, GHRepositoryListItem> {
    var offset = 0
    for ((account, items) in itemsByAccount) {
      if (index >= offset + items.size) {
        offset += items.size
        continue
      }
      return account to items[index - offset]
    }
    throw IndexOutOfBoundsException(index)
  }

  fun indexOf(account: GithubAccount, item: GHRepositoryListItem): Int {
    if (!itemsByAccount.containsKey(account)) return -1
    var startOffset = 0
    for ((_account, items) in itemsByAccount) {
      if (_account == account) {
        val idx = items.indexOf(item)
        if (idx < 0) return -1
        return startOffset + idx
      }
      else {
        startOffset += items.size
      }
    }
    return -1
  }

  fun clear(account: GithubAccount) {
    repositoriesByAccount.remove(account)
    val (startOffset, endOffset) = findAccountOffsets(account) ?: return
    itemsByAccount.remove(account)
    fireIntervalRemoved(this, startOffset, endOffset)
  }

  fun setError(account: GithubAccount, error: Throwable) {
    val accountItems = itemsByAccount.getOrPut(account) { mutableListOf() }
    val (startOffset, endOffset) = findAccountOffsets(account) ?: return
    val errorItem = GHRepositoryListItem.Error(account, error)
    accountItems.add(0, errorItem)
    fireIntervalAdded(this, endOffset, endOffset + 1)
    fireContentsChanged(this, startOffset, endOffset + 1)
  }

  /**
   * Since each repository can be in several states at the same time (shared access for a collaborator and shared access for org member) and
   * repositories for collaborators are loaded in separate request before repositories for org members, we need to update order of re-added
   * repo in order to place it close to other organization repos
   */
  fun addRepositories(account: GithubAccount, details: GithubAuthenticatedUser, repos: List<GithubRepo>) {
    val repoSet = repositoriesByAccount.getOrPut(account) { mutableSetOf() }
    val items = itemsByAccount.getOrPut(account) { mutableListOf() }
    var (startOffset, endOffset) = findAccountOffsets(account) ?: return

    val toAdd = mutableListOf<GHRepositoryListItem.Repo>()
    for (repo in repos) {
      val item = GHRepositoryListItem.Repo(account, details, repo)
      val isNew = repoSet.add(repo)
      if (!isNew) {
        val idx = items.indexOf(item)
        items.removeAt(idx)
        fireIntervalRemoved(this, startOffset + idx, startOffset + idx)
        endOffset--
      }
      toAdd.add(item)
    }
    items.addAll(toAdd)
    fireIntervalAdded(this, endOffset, endOffset + toAdd.size)
  }

  private fun findAccountOffsets(account: GithubAccount): Pair<Int, Int>? {
    if (!itemsByAccount.containsKey(account)) return null
    var startOffset = 0
    var endOffset = 0
    for ((_account, items) in itemsByAccount) {
      endOffset = startOffset + items.size
      if (_account == account) {
        break
      }
      else {
        startOffset += items.size
      }
    }
    return startOffset to endOffset
  }
}