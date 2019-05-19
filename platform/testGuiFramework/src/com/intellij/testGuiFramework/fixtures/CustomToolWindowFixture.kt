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
import com.intellij.openapi.editor.impl.EditorComponentImpl
import com.intellij.testGuiFramework.framework.GuiTestUtil
import com.intellij.testGuiFramework.framework.Timeouts
import com.intellij.testGuiFramework.impl.GuiTestUtilKt
import com.intellij.ui.content.Content
import com.intellij.util.ui.UIUtil.findComponentOfType
import com.intellij.util.ui.UIUtil.findComponentsOfType
import org.fest.swing.core.GenericTypeMatcher
import org.fest.swing.core.Robot
import org.fest.swing.exception.ComponentLookupException
import org.fest.swing.fixture.ContainerFixture
import org.fest.swing.timing.Timeout
import org.fest.swing.util.TextMatcher
import org.junit.Assert.assertNotNull
import javax.swing.JComponent

class CustomToolWindowFixture(val toolWindowId: String, val ideFrame: IdeFrameFixture) :
  ToolWindowFixture(toolWindowId, ideFrame.project, ideFrame.robot()) {

  class ContentFixture(val myParentToolWindow: CustomToolWindowFixture,
                       val myRobot: Robot,
                       private val myContent: Content) : ComponentFixture<ContentFixture, JComponent>(ContentFixture::class.java, myRobot, myContent.component), ContainerFixture<JComponent> {

    private val toolbarButtons: List<ActionButton>
      get() {
        val toolbar = findComponentOfType(myContent.component, ActionToolbarImpl::class.java)!!
        return findComponentsOfType(toolbar, ActionButton::class.java)
      }

    fun getContent(): Content = myContent

    fun editor(): EditorFixture {
      val editor = GuiTestUtil.waitUntilFound(myRobot, myContent.component, object : GenericTypeMatcher<EditorComponentImpl>(EditorComponentImpl::class.java, true){
        override fun isMatching(component: EditorComponentImpl): Boolean {
          return true
        }
      })
      return EditorFixture(myRobot,editor.editor)
    }

    fun findEditorContainingText(text: String): EditorFixture {
      val editor = GuiTestUtil.waitUntilFound(myRobot, myContent.component, object : GenericTypeMatcher<EditorComponentImpl>(EditorComponentImpl::class.java, true){
        override fun isMatching(component: EditorComponentImpl): Boolean {
          return EditorFixture(myRobot, component.editor).getCurrentFileContents(false)?.contains(text) ?: false
        }
      })
      return EditorFixture(myRobot,editor.editor)
    }
  }

  private fun selectedContent() : ContentFixture {
    val content = super.getSelectedContent()
    assertNotNull(content)
    return ContentFixture(this, myRobot, content!!)
  }

  private fun findContent(tabName: String, timeout: Timeout): ContentFixture {
    selectContentWithTimeout(tabName, timeout)
    val content = getContent(tabName) ?: throw ComponentLookupException(
      "Unable to find content with a tab name: \"$tabName\" for a toolwindow with id: \"$toolWindowId\"")
    return ContentFixture(this, myRobot, content)
  }

  private fun selectContentWithTimeout(tabName: String, timeout: Timeout) {
    GuiTestUtilKt.waitUntil("content with a tab name '$tabName' is appeared for a toolwindow with id: \"$toolWindowId\"", timeout) {
      try {
        selectContent(tabName)
        true
      } catch (componentLookupException: ComponentLookupException) {
        false
      }
    }

  }

  fun findContent(tabNameMatcher: TextMatcher): ContentFixture {
    val content = getContent(tabNameMatcher)
    assertNotNull(content)
    return ContentFixture(this, myRobot, content!!)
  }

  /**----------EXTENSION FUNCTIONS FOR GuiTestCase APi----------**/

  fun content(tabName: String, timeout: Timeout = Timeouts.seconds30, func: ContentFixture.() -> Unit) {
    func.invoke(findContent(tabName, timeout))
  }

  fun content(func: ContentFixture.() -> Unit) {
    func.invoke(selectedContent())
  }
}
