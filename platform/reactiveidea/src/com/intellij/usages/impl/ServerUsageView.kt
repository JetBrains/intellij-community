/*
 * Copyright 2000-2015 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.usages.impl

import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Condition
import com.intellij.openapi.util.Factory
import com.intellij.psi.SmartPointerManager
import com.intellij.usageView.UsageViewManager
import com.intellij.usages.*
import com.intellij.usages.impl.GroupNode
import com.intellij.usages.impl.UsageNodeTreeBuilder
import com.intellij.usages.impl.UsageViewImpl
import com.intellij.usages.impl.UsageViewTreeModelBuilder
import com.intellij.util.Consumer
import com.intellij.util.Processor
import com.intellij.util.containers.TransferToEDTQueue
import com.intellij.util.ui.UIUtil
import gnu.trove.THashSet
import java.util.*
import java.util.concurrent.ConcurrentHashMap
import javax.swing.JComponent
import javax.swing.JPanel
import javax.swing.tree.*

public class ServerUsageView(val project: Project,
                             private val presentation: UsageViewPresentation,
                             val targets: Array<UsageTarget>,
                             val usageSearcherFactory: Factory<UsageSearcher>) : UsageView {

  companion object {
    val NULL_NODE = UsageViewImpl.NULL_NODE
    val USAGE_COMPARATOR = UsageViewImpl.USAGE_COMPARATOR
  }

  private volatile var isDisposed = false
  private volatile var mySearchInProgress = false
  private val rootPanel = JPanel()

  val model: UsageViewTreeModelBuilder = UsageViewTreeModelBuilder(presentation, targets);
  val root = model.getRoot() as GroupNode
  val builder = UsageNodeTreeBuilder(targets, UsageViewImpl.getActiveGroupingRules(project), UsageViewImpl.getActiveFilteringRules(project), root)
  val transferToEDTQueue = TransferToEDTQueue("Insert usages", object : Processor<Runnable> {
    override fun process(runnable: Runnable): Boolean {
      runnable.run()
      return true
    }
  }, object : Condition<Any> {
    override fun value(o: Any?): Boolean {
      return isDisposed || project.isDisposed()
    }
  }, 200)
  private val usageNodes = ConcurrentHashMap<Usage, UsageNode>()

  override fun appendUsage(usage: Usage) {
    if (!usage.isValid()) {
      return
    }
    val node = builder.appendUsage(usage, object : Consumer<Runnable> {
      override fun consume(runnable: Runnable) {
        transferToEDTQueue.offer(runnable)
      }
    })
    node?.update(this)

    usageNodes.put(usage, node ?: NULL_NODE)
  }

  override fun removeUsage(usage: Usage) {
    usageNodes.remove(usage)
  }

  override fun includeUsages(usages: Array<out Usage>) {
    val nodes = ArrayList<TreeNode>(usages.size())
    for (usage in usages) {
      val node = usageNodes.get(usage)
      if (node !== NULL_NODE && node != null) {
        node.setUsageExcluded(false)
        nodes.add(node)
      }
    }
  }

  override fun excludeUsages(usages: Array<out Usage>) {
    val nodes = ArrayList<TreeNode>(usages.size())
    for (usage in usages) {
      val node = usageNodes.get(usage)
      if (node !== NULL_NODE && node != null) {
        node.setUsageExcluded(true)
        nodes.add(node)
      }
    }
  }

  override fun selectUsages(usages: Array<out Usage>) {
    val paths = ArrayList<TreePath>()

    for (usage in usages) {
      val node = usageNodes.get(usage)

      if (node !== NULL_NODE && node != null) {
        paths.add(TreePath(node.getPath()))
      }
    }
  }

  override fun close() {
  }

  override fun isSearchInProgress(): Boolean {
    return mySearchInProgress
  }

  override fun addButtonToLowerPane(runnable: Runnable, text: String, mnemonic: Char) {
    throw UnsupportedOperationException()
  }

  override fun addButtonToLowerPane(runnable: Runnable, text: String) {
    addButtonToLowerPane(runnable, text)
  }

  override fun addPerformOperationAction(processRunnable: Runnable, commandName: String?, cannotMakeString: String?, shortDescription: String) {
    throw UnsupportedOperationException()
  }

  override fun addPerformOperationAction(processRunnable: Runnable, commandName: String?, cannotMakeString: String?, shortDescription: String, checkReadOnlyStatus: Boolean) {
    throw UnsupportedOperationException()
  }

  override fun getPresentation(): UsageViewPresentation {
    return presentation;
  }

  override fun getExcludedUsages(): MutableSet<Usage> {
    val result = THashSet<Usage>()
    for (entry in usageNodes.entrySet()) {
      val node = entry.getValue()
      val usage = entry.getKey()
      if (node === NULL_NODE || node == null) {
        continue
      }
      if (node.isExcluded()) {
        result.add(usage)
      }
    }
    return result
  }

  override fun getSelectedUsages(): Set<Usage>? {
    throw UnsupportedOperationException()
  }

  override fun getUsages(): Set<Usage> {
    return usageNodes.keySet()
  }

  override fun getSortedUsages(): MutableList<Usage> {
    val usages = ArrayList(getUsages())
    Collections.sort(usages, USAGE_COMPARATOR)
    return usages
  }

  override fun getComponent(): JComponent = rootPanel

  override fun getUsagesCount(): Int = usageNodes.size()

  override fun removeUsagesBulk(usages: Collection<Usage>) {
    val nodes = THashSet<UsageNode>(usages.size())
    for (usage in usages) {
      val node = usageNodes.remove(usage)
      if (node != null && node !== NULL_NODE) {
        nodes.add(node)
      }
    }
  }

  override fun dispose() {
    isDisposed = true
    disposeSmartPointers()
  }

  private fun disposeSmartPointers() {
    val pointerManager = SmartPointerManager.getInstance(project)
    for (usage in usageNodes.keySet()) {
      if (usage is UsageInfo2UsageAdapter) {
        val pointer = usage.getUsageInfo().getSmartPointer()
        pointerManager.removePointer(pointer)
      }
    }
  }

}