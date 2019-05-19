// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.testGuiFramework.fixtures

import com.intellij.testGuiFramework.cellReader.ExtendedJListCellReader
import com.intellij.testGuiFramework.util.FinderPredicate
import com.intellij.testGuiFramework.util.step
import com.intellij.ui.components.JBList
import com.intellij.ui.popup.PopupFactoryImpl
import org.fest.swing.core.Robot
import org.fest.swing.fixture.JListFixture

class JBListPopupFixture(
  jbList: JBList<*>,
  private val searchedItem: String,
  private val predicate: FinderPredicate,
  robot: Robot) : JComponentFixture<JBListPopupFixture, JBList<*>>(
  JBListPopupFixture::class.java, robot, jbList) {

  private val jListFixture = JListFixture(robot, jbList)

  init {
    jListFixture.replaceCellReader(ExtendedJListCellReader())
  }

  fun clickSearchedItem(){
    step("click '$searchedItem' in popup menu") {
      jListFixture.clickItem(searchedItem)
    }
  }

  fun clickItem(item: String) {
    jListFixture.clickItem(item)
  }

  fun clickItem(index: Int) {
    jListFixture.clickItem(index)
  }

  fun isSearchedItemEnable(): Boolean =
    isItemEnable(itemIndex(searchedItem))

  fun isItemEnable(index: Int): Boolean {
    val item = jListFixture.target().model.getElementAt(index)
    return (item as? PopupFactoryImpl.ActionItem)?.isEnabled
           ?: throw ClassCastException("Menu item type '${item.javaClass.canonicalName}' cannot be cast to PopupFactoryImpl.ActionItem")
  }

  fun isSearchedItemPresent() : Boolean = isItemPresent(searchedItem)

  fun isItemPresent(item: String): Boolean = listItems().any { predicate(it, item) }

  fun itemIndex(item: String): Int = listItems().indexOfFirst { predicate(it, item) }

  fun listItems(): List<String> {
    val itemCount = jListFixture.target().model.size
    return (0 until itemCount)
      .mapNotNull { jListFixture.item(it).value() }
  }

}