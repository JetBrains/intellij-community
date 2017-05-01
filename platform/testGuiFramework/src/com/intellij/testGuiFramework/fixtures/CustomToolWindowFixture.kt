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
package com.intellij.testGuiFramework.fixtures

import com.intellij.openapi.actionSystem.impl.ActionButton
import com.intellij.openapi.actionSystem.impl.ActionToolbarImpl
import com.intellij.ui.content.Content
import com.intellij.util.ui.UIUtil.findComponentOfType
import com.intellij.util.ui.UIUtil.findComponentsOfType
import junit.framework.Assert.assertNotNull
import org.fest.swing.core.Robot
import org.fest.swing.util.TextMatcher
import javax.swing.JComponent

class CustomToolWindowFixture(val toolWindowId: String, val ideFrame: IdeFrameFixture) :
  ToolWindowFixture(toolWindowId, ideFrame.project, ideFrame.robot()) {

  class ContentFixture(val myParentToolWindow: CustomToolWindowFixture,
                       val myRobot: Robot,
                       val myContent: Content) : ComponentFixture<ContentFixture, JComponent>(ContentFixture::class.java, myRobot, myContent.component) {

    private val toolbarButtons: List<ActionButton>
      get() {
        val toolbar = findComponentOfType(myContent.component, ActionToolbarImpl::class.java)!!
        return findComponentsOfType(toolbar, ActionButton::class.java)
      }

    fun getContent() = myContent
  }

  fun selectedContent() : ContentFixture {
    val content = super.getSelectedContent()
    assertNotNull(content)
    return ContentFixture(this, myRobot, content!!)
  }

  fun findContent(tabName: String): ContentFixture {
    selectContent(tabName)
    val content = getContent(tabName)
    assertNotNull(content)
    return ContentFixture(this, myRobot, content!!)
  }

  fun findContent(tabNameMatcher: TextMatcher): ContentFixture {
    val content = getContent(tabNameMatcher)
    assertNotNull(content)
    return ContentFixture(this, myRobot, content!!)
  }

  /**----------EXTENSION FUNCTIONS FOR GuiTestCase APi----------**/

  fun content(tabName: String, func: ContentFixture.() -> Unit) {
    func.invoke(findContent(tabName))
  }

  fun content(func: ContentFixture.() -> Unit) {
    func.invoke(selectedContent())
  }
}
