/*
 * Copyright 2000-2016 JetBrains s.r.o.
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

import com.intellij.openapi.fileChooser.ex.FileChooserDialogImpl
import com.intellij.openapi.fileChooser.ex.FileSystemTreeImpl
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.testGuiFramework.framework.GuiTestUtil
import com.intellij.testGuiFramework.framework.GuiTestUtil.SHORT_TIMEOUT
import com.intellij.testGuiFramework.framework.GuiTestUtil.findAndClickOkButton
import org.fest.reflect.core.Reflection.field
import org.fest.swing.core.GenericTypeMatcher
import org.fest.swing.core.Robot
import org.fest.swing.edt.GuiActionRunner.execute
import org.fest.swing.edt.GuiTask
import org.fest.swing.fixture.JTextComponentFixture
import org.fest.swing.timing.Condition
import org.fest.swing.timing.Pause.pause
import org.fest.util.Strings.quote
import org.junit.Assert.assertNotNull
import java.util.concurrent.atomic.AtomicBoolean
import javax.annotation.Nonnull
import javax.swing.JDialog
import javax.swing.JTextField
import javax.swing.tree.TreePath

class FileChooserDialogFixture private constructor(robot: Robot,
                                                   dialogAndWrapper: IdeaDialogFixture.DialogAndWrapper<FileChooserDialogImpl>) : IdeaDialogFixture<FileChooserDialogImpl>(
  robot, dialogAndWrapper) {

  internal var myTargetPath: TreePath? = null
  private var myJTextFieldFixture: JTextComponentFixture? = null

  fun select(file: VirtualFile): FileChooserDialogFixture {
    val fileSystemTree = field("myFileSystemTree").ofType(FileSystemTreeImpl::class.java)
      .`in`(dialogWrapper)
      .get()
    assertNotNull(fileSystemTree)
    val fileSelected = AtomicBoolean()
    execute(object : GuiTask() {
      @Throws(Throwable::class)
      override fun executeInEDT() {
        fileSystemTree!!.select(file, Runnable { fileSelected.set(true) })
      }
    })

    pause(object : Condition("File " + quote(file.path) + " is selected") {
      override fun test(): Boolean {
        return fileSelected.get()
      }
    }, SHORT_TIMEOUT)

    return this
  }

  private fun sleepWithTimeBomb() {
    //TODO: why this bombed?
    assert(System.currentTimeMillis() < 1452600000000L)  // 2016-01-12 12:00
    try {
      Thread.sleep(5000)
    }
    catch (e: InterruptedException) {
    }

  }

  val textFieldFixture: JTextComponentFixture
    get() {
      if (myJTextFieldFixture == null) {
        val textField = robot().finder().find(this.target(), object : GenericTypeMatcher<JTextField>(JTextField::class.java, true) {
          override fun isMatching(@Nonnull field: JTextField): Boolean {
            return true
          }
        })
        myJTextFieldFixture = JTextComponentFixture(robot(), textField)
      }
      return myJTextFieldFixture!!

    }

  fun waitFilledTextField(): FileChooserDialogFixture {
    pause(object : Condition("Wait until JTextField component will be filled by default path") {
      override fun test(): Boolean {
        val text = textFieldFixture.text()
        return text != null && text.isNotEmpty()
      }
    }, GuiTestUtil.THIRTY_SEC_TIMEOUT)
    return this
  }

  fun clickOk(): FileChooserDialogFixture {
    findAndClickOkButton(this)
    return this
  }

  companion object {


    fun findOpenProjectDialog(robot: Robot): FileChooserDialogFixture {
      return findDialog(robot, object : GenericTypeMatcher<JDialog>(JDialog::class.java) {
        override fun isMatching(dialog: JDialog): Boolean {
          return dialog.isShowing && "Open File or Project" == dialog.title
        }
      })
    }

    fun findImportProjectDialog(robot: Robot): FileChooserDialogFixture {
      return findDialog(robot, object : GenericTypeMatcher<JDialog>(JDialog::class.java) {
        override fun isMatching(dialog: JDialog): Boolean {
          val title = dialog.title
          return dialog.isShowing && title != null && title.startsWith("Select") && title.endsWith("Project to Import")
        }
      })
    }

    fun findDialog(robot: Robot, matcher: GenericTypeMatcher<JDialog>): FileChooserDialogFixture {
      return FileChooserDialogFixture(robot, IdeaDialogFixture.find(robot, FileChooserDialogImpl::class.java, matcher))
    }
  }
}
