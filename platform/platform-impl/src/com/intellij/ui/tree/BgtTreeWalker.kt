// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ui.tree

import com.intellij.openapi.diagnostic.logger
import com.intellij.util.concurrency.Invoker
import com.intellij.util.concurrency.annotations.RequiresEdt
import org.jetbrains.concurrency.AsyncPromise
import org.jetbrains.concurrency.Promise
import javax.swing.tree.TreePath

internal abstract class BgtTreeWalker<N : Any>(
  private val visitor: TreeVisitor,
  private val background: Invoker,
  private val foreground: Invoker,
  private val convert: (N) -> Any,
) : TreeWalkerBase<N>() {

  val promise: AsyncPromise<TreePath> = AsyncPromise()
  private inner class Level(val path: TreePath?, val nodes: ArrayDeque<N>)
  private val stack = ArrayDeque<Level>()
  @set:RequiresEdt
  private var state: State = InitialState
    set(value) {
      checkValidStateTransition(field, value)
      field = value
    }
  private val lookingForNextNode = LookingForNextNode()
  private val visitingNode = VisitingNode()
  private val requestingChildren = RequestingChildren()
  private val success = Success()
  private val failure = Failure()

  override fun promise(): Promise<TreePath> = promise

  @RequiresEdt
  override fun start(node: N?) {
    if (node != null) {
      stack.addLast(Level(null, ArrayDeque(listOf(node))))
    }
    lookingForNextNode.enter()
  }

  @RequiresEdt
  override fun setChildren(children: Collection<N>) {
    requestingChildren.setChildren(children)
  }

  @RequiresEdt
  override fun setError(error: Throwable) {
    failure.enter(error)
  }

  private fun checkValidStateTransition(oldState: State, newState: State) {
    val valid = when (newState) {
      InitialState -> false
      lookingForNextNode -> oldState == InitialState || oldState == visitingNode || oldState == requestingChildren
      visitingNode -> oldState == lookingForNextNode
      requestingChildren -> oldState == visitingNode
      success -> oldState == lookingForNextNode || oldState == visitingNode
      failure -> oldState != success
      else -> false
    }
    if (!valid) {
      throw IllegalStateException("Invalid state transition in BgtTreeWalker: $oldState -> $newState")
    }
  }

  private abstract class State

  private object InitialState : State()

  private inner class LookingForNextNode : State() {

    fun enter() {
      state = this
      debug("Looking for next node")
      while (true) {
        val level = stack.lastOrNull()
        if (level == null) {
          debug("No nodes remaining in the tree")
          success.enter(null)
          return
        }
        val node = level.nodes.removeFirstOrNull()
        if (node == null) {
          debug("No nodes remaining on the level, going up")
          stack.removeLast()
          continue
        }
        val path = TreePathUtil.createTreePath(level.path, convert(node))
        debug("Found node ", node, path)
        visitingNode.enter(node, path)
        return
      }
    }

  }

  private inner class VisitingNode : State() {

    fun enter(node: N, path: TreePath) {
      state = this
      debug("Visiting node ", node, path)
      val edtBgtVisitor = visitor as? EdtBgtTreeVisitor
      val preVisitResult = edtBgtVisitor?.preVisitEDT(path)
      if (preVisitResult != null) {
        processVisitResult(preVisitResult, path, node)
        return
      }
      background.computeLater {
        visitor.visit(path)
      }.onSuccess { action ->
        foreground.invokeLater {
          val visitResult = action!!
          val postVisitResult = edtBgtVisitor?.postVisitEDT(path, visitResult)
          processVisitResult(postVisitResult ?: visitResult, path, node)
        }
      }.onError { error ->
        foreground.invokeLater {
          failure.enter(error)
        }
      }
    }

    private fun processVisitResult(visitResult: TreeVisitor.Action, path: TreePath, node: N) {
      when (visitResult) {
        TreeVisitor.Action.INTERRUPT -> {
          success.enter(path)
        }
        TreeVisitor.Action.CONTINUE -> {
          requestingChildren.enter(node, path)
        }
        TreeVisitor.Action.SKIP_CHILDREN -> {
          lookingForNextNode.enter()
        }
        TreeVisitor.Action.SKIP_SIBLINGS -> {
          stack.removeLastOrNull()
          lookingForNextNode.enter()
        }
      }
    }

  }

  private inner class RequestingChildren : State() {

    private lateinit var path: TreePath

    fun enter(node: N, path: TreePath) {
      state = this
      debug("Requesting children ", node, path)
      this.path = path
      val children = getChildren(node)
      if (children != null) {
        setChildren(children)
      } // otherwise we expect a setChildren() callback
    }

    fun setChildren(children: Collection<N>) {
      foreground.invokeLater {
        stack.addLast(Level(path, ArrayDeque(children)))
        lookingForNextNode.enter()
      }
    }

  }

  private inner class Success : State() {

    fun enter(result: TreePath?) {
      if (promise.isDone) {
        warn("Already complete, can't register success $result")
        return
      }
      debug("Entering success state: ", result)
      state = this
      promise.setResult(result)
    }

  }

  private inner class Failure : State() {

    fun enter(error: Throwable) {
      if (promise.isDone) {
        warn("Already complete, can't register failure $error")
        return
      }
      debug("Entering failure state: ", error)
      state = this
      promise.setError(error)
    }

  }

  private fun warn(message: String) {
    LOG.warn("$this(${Thread.currentThread()}): $message")
  }

  // overloads to avoid unnecessary vararg array creation when debugging is off

  private fun debug(message: String) {
    if (LOG.isDebugEnabled) {
      LOG.debug("$this(${Thread.currentThread()}): $message")
    }
  }

  private fun debug(message: String, details: Any?) {
    if (LOG.isDebugEnabled) {
      LOG.debug("$this(${Thread.currentThread()}): $message", details)
    }
  }

  private fun debug(message: String, details1: Any?, details2: Any?) {
    if (LOG.isDebugEnabled) {
      LOG.debug("$this(${Thread.currentThread()}): $message", details1, details2)
    }
  }

}

private val LOG = logger<BgtTreeWalker<*>>()
