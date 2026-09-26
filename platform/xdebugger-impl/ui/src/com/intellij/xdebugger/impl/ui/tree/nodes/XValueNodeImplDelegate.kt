// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.xdebugger.impl.ui.tree.nodes

import com.intellij.ui.ColoredTextContainer
import com.intellij.ui.SimpleColoredText
import com.intellij.ui.SimpleTextAttributes
import com.intellij.xdebugger.frame.XDebuggerTreeNodeHyperlink
import com.intellij.xdebugger.frame.XFullValueEvaluator
import com.intellij.xdebugger.frame.XValue
import com.intellij.xdebugger.frame.XValueChildrenList
import com.intellij.xdebugger.frame.presentation.XValuePresentation
import com.intellij.xdebugger.impl.ui.tree.XDebuggerTree
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.Nls
import org.jetbrains.annotations.NonNls
import org.jetbrains.annotations.Unmodifiable
import java.util.Enumeration
import javax.swing.Icon
import javax.swing.event.HyperlinkListener
import javax.swing.tree.TreeNode
import javax.swing.tree.TreePath

@ApiStatus.Internal
@ApiStatus.Obsolete
abstract class XValueNodeImplDelegate(private val delegateNode: XValueNodeImpl, value: XValue) : XValueNodeImpl(delegateNode, value) {

  override fun setPresentation(
    icon: Icon?,
    type: @NonNls String?,
    value: @NonNls String,
    hasChildren: Boolean,
  ) {
    delegateNode.setPresentation(icon, type, value, hasChildren)
  }

  override fun setPresentation(
    icon: Icon?,
    presentation: XValuePresentation,
    hasChildren: Boolean,
  ) {
    delegateNode.setPresentation(icon, presentation, hasChildren)
  }

  override fun applyPresentation(
    icon: Icon?,
    valuePresentation: XValuePresentation,
    hasChildren: Boolean,
  ) {
    delegateNode.applyPresentation(icon, valuePresentation, hasChildren)
  }

  override fun shouldUpdateInlineDebuggerData(): Boolean {
    return delegateNode.shouldUpdateInlineDebuggerData()
  }

  override fun isChanged(): Boolean {
    return delegateNode.isChanged()
  }

  override fun setInlayIcon(icon: Icon?) {
    delegateNode.setInlayIcon(icon)
  }

  override fun getInlayIcon(): Icon? {
    return delegateNode.getInlayIcon()
  }

  override fun setFullValueEvaluator(fullValueEvaluator: XFullValueEvaluator) {
    delegateNode.setFullValueEvaluator(fullValueEvaluator)
  }

  override fun addAdditionalHyperlink(link: XDebuggerTreeNodeHyperlink) {
    delegateNode.addAdditionalHyperlink(link)
  }

  override fun clearAdditionalHyperlinks() {
    delegateNode.clearAdditionalHyperlinks()
  }

  override fun hasLinks(): Boolean {
    return delegateNode.hasLinks()
  }

  override fun clearFullValueEvaluator() {
    delegateNode.clearFullValueEvaluator()
  }

  override fun getXValue(): XValue {
    return delegateNode.getXValue()
  }

  override fun markChanged() {
    delegateNode.markChanged()
  }

  override fun getFullValueEvaluator(): XFullValueEvaluator? {
    return delegateNode.getFullValueEvaluator()
  }

  override fun getLink(): XDebuggerTreeNodeHyperlink? {
    return delegateNode.getLink()
  }

  override fun appendToComponent(component: ColoredTextContainer) {
    delegateNode.appendToComponent(component)
  }

  override fun getName(): String? {
    return delegateNode.getName()
  }

  override fun getValuePresentation(): XValuePresentation? {
    return delegateNode.getValuePresentation()
  }

  override fun getRawValue(): String? {
    return delegateNode.getRawValue()
  }

  override fun isComputed(): Boolean {
    return delegateNode.isComputed()
  }

  override fun setValueModificationStarted() {
    delegateNode.setValueModificationStarted()
  }

  override fun toString(): String {
    return delegateNode.toString()
  }

  override fun getIconTag(): Any? {
    return delegateNode.getIconTag()
  }

  override fun startComputingChildren() {
    delegateNode.startComputingChildren()
  }

  override fun createLoadingMessageNode(): MessageTreeNode? {
    return delegateNode.createLoadingMessageNode()
  }

  override fun setAlreadySorted(alreadySorted: Boolean) {
    delegateNode.setAlreadySorted(alreadySorted)
  }

  override fun addChildren(children: XValueChildrenList, last: Boolean) {
    delegateNode.addChildren(children, last)
  }

  override fun tooManyChildren(remaining: Int) {
    delegateNode.tooManyChildren(remaining)
  }

  override fun tooManyChildren(remaining: Int, childrenSupplier: Runnable) {
    delegateNode.tooManyChildren(remaining, childrenSupplier)
  }

  override fun isObsolete(): Boolean {
    return delegateNode.isObsolete()
  }

  override fun clearChildren() {
    delegateNode.clearChildren()
  }

  override fun setErrorMessage(errorMessage: String) {
    delegateNode.setErrorMessage(errorMessage)
  }

  override fun setErrorMessage(errorMessage: String, link: XDebuggerTreeNodeHyperlink?) {
    delegateNode.setErrorMessage(errorMessage, link)
  }

  override fun setMessage(
    message: String,
    icon: Icon?,
    attributes: SimpleTextAttributes,
    link: XDebuggerTreeNodeHyperlink?,
  ) {
    delegateNode.setMessage(message, icon, attributes, link)
  }

  override fun setInfoMessage(
    message: @Nls String,
    hyperlinkListener: HyperlinkListener?,
  ) {
    delegateNode.setInfoMessage(message, hyperlinkListener)
  }

  override fun setTemporaryMessage(message: MessageTreeNode) {
    delegateNode.setTemporaryMessage(message)
  }

  override fun addTemporaryEditorNode(
    icon: Icon?,
    text: @Nls String?,
  ): XDebuggerTreeNode {
    return delegateNode.addTemporaryEditorNode(icon, text)
  }

  override fun removeTemporaryEditorNode(node: XDebuggerTreeNode?) {
    delegateNode.removeTemporaryEditorNode(node)
  }

  override fun removeChildNode(
    children: List<*>?,
    node: XDebuggerTreeNode?,
  ): Int {
    return delegateNode.removeChildNode(children, node)
  }

  override fun getChildren(): @Unmodifiable List<TreeNode?> {
    return delegateNode.getChildren()
  }

  override fun getCachedChildren(): List<TreeNode?>? {
    return delegateNode.getCachedChildren()
  }

  override fun getValueContainer(): XValue {
    return delegateNode.getValueContainer()
  }

  override fun getLoadedChildren(): @Unmodifiable List<XValueContainerNode<*>?> {
    return delegateNode.getLoadedChildren()
  }

  override fun setObsolete() {
    delegateNode.setObsolete()
  }

  override fun getChildAt(childIndex: Int): TreeNode? {
    return delegateNode.getChildAt(childIndex)
  }

  override fun getChildCount(): Int {
    return delegateNode.getChildCount()
  }

  override fun getParent(): TreeNode? {
    return delegateNode.getParent()
  }

  override fun getIndex(node: TreeNode): Int {
    return delegateNode.getIndex(node)
  }

  override fun getAllowsChildren(): Boolean {
    return delegateNode.getAllowsChildren()
  }

  override fun isLeaf(): Boolean {
    return delegateNode.isLeaf()
  }

  override fun setIcon(icon: Icon?) {
    delegateNode.setIcon(icon)
  }

  override fun setLeaf(leaf: Boolean) {
    delegateNode.setLeaf(leaf)
  }

  override fun getText(): SimpleColoredText {
    return delegateNode.getText()
  }

  override fun getIcon(): Icon? {
    return delegateNode.getIcon()
  }

  override fun fireNodeChanged() {
    delegateNode.fireNodeChanged()
  }

  override fun fireNodesRemoved(indices: IntArray?, nodes: Array<out TreeNode?>?) {
    delegateNode.fireNodesRemoved(indices, nodes)
  }

  override fun fireNodesInserted(added: Collection<TreeNode?>?) {
    delegateNode.fireNodesInserted(added)
  }

  override fun getChildNodes(indices: IntArray?): Array<out TreeNode?>? {
    return delegateNode.getChildNodes(indices)
  }

  override fun getNodesIndices(children: Collection<TreeNode?>?): IntArray? {
    return delegateNode.getNodesIndices(children)
  }

  override fun fireNodeStructureChanged() {
    delegateNode.fireNodeStructureChanged()
  }

  override fun fireNodeStructureChanged(node: TreeNode?) {
    delegateNode.fireNodeStructureChanged(node)
  }

  override fun getTree(): XDebuggerTree? {
    return delegateNode.getTree()
  }

  override fun getPath(): TreePath? {
    return delegateNode.getPath()
  }

  override fun invokeNodeUpdate(runnable: Runnable?) {
    delegateNode.invokeNodeUpdate(runnable)
  }

  override fun children(): Enumeration<out TreeNode?>? {
    return delegateNode.children()
  }

  override fun equals(other: Any?): Boolean {
    return delegateNode.equals(other)
  }

  override fun hashCode(): Int {
    return delegateNode.hashCode()
  }
}