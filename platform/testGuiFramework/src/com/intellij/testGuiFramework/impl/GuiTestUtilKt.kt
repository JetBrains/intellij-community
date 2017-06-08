/*
 * Copyright 2000-2017 JetBrains s.r.o.
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
package com.intellij.testGuiFramework.impl

import java.util.*

/**
 * @author Sergey Karashevich
 */
object GuiTestUtilKt {

  fun createTree(string: String): ImmutableTree<String> {
    val currentPath = ArrayList<String>()
    var lines = string.split("\n")
    if (lines.last().isEmpty()) lines = lines.subList(0, lines.lastIndex - 1)

    val tree: ImmutableTree<String> = ImmutableTree()
    var lastNode: ImmutableTreeNode<String>? = null

    try {
      for (line in lines) {
        if (currentPath.isEmpty()) {
          currentPath.add(line)
          tree.root = ImmutableTreeNode(line.withoutIndent(), null)
          lastNode = tree.root
        }
        else {
          if (currentPath.last() hasDiffIndentFrom line) {
            if (currentPath.last().getIndent() > line.getIndent()) {
              while (currentPath.last() hasDiffIndentFrom line) {
                currentPath.removeAt(currentPath.lastIndex)
                lastNode = lastNode!!.parent
              }
              currentPath.removeAt(currentPath.lastIndex)
              currentPath.add(line)
              lastNode = lastNode!!.parent!!.createChild(line.withoutIndent())
            }
            else {
              currentPath.add(line)
              lastNode = lastNode!!.createChild(line.withoutIndent())
            }
          }
          else {
            currentPath.removeAt(currentPath.lastIndex)
            currentPath.add(line)
            lastNode = lastNode!!.parent!!.createChild(line.withoutIndent())
          }
        }
      }

      return tree
    }
    catch(e: Exception) {
      throw Exception("Unable to build a tree from given data. Check indents and ")
    }
  }

//  fun ProjectViewFixture.containsTree(tree: ImmutableTree<String>): Boolean {
//
//  }

  private infix fun String.hasDiffIndentFrom(s: String): Boolean {
    return this.getIndent() != s.getIndent()
  }

  private fun String.getIndent() = this.indexOfFirst { it != ' ' }

  private fun String.withoutIndent() = this.substring(this.getIndent())

  private operator fun String.times(n: Int): String {
    val sb = StringBuilder(n)
    for (i in 1..n) {
      sb.append(this)
    }
    return sb.toString()
  }



  class ImmutableTree<Value> {
    var root: ImmutableTreeNode<Value>? = null

    fun print() {
      if (root == null) throw Exception("Unable to print tree without root (or if root is null)")
      printRecursive(root!!, 0)
    }

    fun printRecursive(root: ImmutableTreeNode<Value>, indent: Int) {
      println(" " * indent + root.value)
      if (!root.isLeaf()) root.children.forEach { printRecursive(it, indent + 2) }
    }

//    fun getLeafs(): List<ImmutableTreeNode<Value>> {
//
//    }

    fun deepFirstSearch(consumer: (ImmutableTreeNode<Value>) -> Unit) {
      val stack = ArrayList<ImmutableTreeNode<Value>>()
      assert(root != null)
      stack.add(root!!)
      while (stack.isNotEmpty()) {
        val v = stack[stack.lastIndex]
      }
    }


  }


  data class ImmutableTreeNode<Value>(val value: Value,
                                      val parent: ImmutableTreeNode<Value>?,
                                      val children: LinkedList<ImmutableTreeNode<Value>> = LinkedList()) {

    fun createChild(childValue: Value): ImmutableTreeNode<Value> {
      val child = ImmutableTreeNode<Value>(childValue, this)
      children.add(child)
      return child
    }

    fun countChildren() = children.count()

    fun isLeaf() = (children.count() == 0)

  }

}

fun main(args: Array<String>) {
  val tree = GuiTestUtilKt.createTree("project\n" +
                           "  src\n" +
                           "    com.username\n" +
                           "      Test1.java\n" +
                           "      Test2.java\n" +
                           "  lib\n" +
                           "    someLib1\n" +
                           "   someLib2")
  tree.print()
}
