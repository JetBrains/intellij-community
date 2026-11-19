// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.xdebugger

import com.intellij.configurationStore.deserialize
import com.intellij.configurationStore.serialize
import com.intellij.openapi.application.runReadActionBlocking
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.text.StringUtil
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.VirtualFileManager
import com.intellij.testFramework.VfsTestUtil
import com.intellij.testFramework.junit5.fixture.TestFixture
import com.intellij.testFramework.junit5.fixture.projectFixture
import com.intellij.testFramework.junit5.fixture.tempPathFixture
import com.intellij.testFramework.junit5.fixture.testFixture
import com.intellij.util.xmlb.annotations.Attribute
import com.intellij.xdebugger.breakpoints.XBreakpoint
import com.intellij.xdebugger.breakpoints.XBreakpointProperties
import com.intellij.xdebugger.breakpoints.XBreakpointType
import com.intellij.xdebugger.breakpoints.XLineBreakpoint
import com.intellij.xdebugger.breakpoints.XLineBreakpointType
import com.intellij.xdebugger.impl.BreakpointManagerState
import com.intellij.xdebugger.impl.breakpoints.XBreakpointManagerImpl
import org.jdom.Element
import org.jetbrains.annotations.TestOnly
import java.nio.file.Path
import kotlin.io.path.createDirectories
import kotlin.io.path.createFile

/**
 * Gives each test a new opened project and its [XBreakpointManagerImpl].
 *
 * The fixture registers [XBreakpointsFixture.MY_LINE_BREAKPOINT_TYPE] and [XBreakpointsFixture.MY_SIMPLE_BREAKPOINT_TYPE]
 * before the project opens.
 * The breakpoint manager then creates the default breakpoint of [XBreakpointsFixture.MY_SIMPLE_BREAKPOINT_TYPE] at its start.
 * The fixture removes the types after the project closes.
 */
@TestOnly
fun xBreakpointsFixture(): TestFixture<XBreakpointsFixture> = testFixture {
  val tempDir = tempPathFixture().init()
  val project = projectFixture(openAfterCreation = true).dependsOn(testBreakpointTypesFixture()).init()
  initialized(XBreakpointsFixture(project, tempDir)) {}
}

@TestOnly
private fun testBreakpointTypesFixture(): TestFixture<Unit> = testFixture {
  val disposable = Disposer.newDisposable()
  val point = XBreakpointType.EXTENSION_POINT_NAME.point
  point.registerExtension(XBreakpointsFixture.MY_LINE_BREAKPOINT_TYPE, disposable)
  point.registerExtension(XBreakpointsFixture.MY_SIMPLE_BREAKPOINT_TYPE, disposable)
  initialized(Unit) {
    Disposer.dispose(disposable)
  }
}

@TestOnly
class XBreakpointsFixture internal constructor(val project: Project, private val tempDir: Path) {
  val breakpointManager: XBreakpointManagerImpl =
    XDebuggerManager.getInstance(project).breakpointManager as XBreakpointManagerImpl

  fun addBreakpoint(properties: MyBreakpointProperties?): XBreakpoint<MyBreakpointProperties> =
    breakpointManager.addBreakpoint(MY_SIMPLE_BREAKPOINT_TYPE, properties)

  fun addLineBreakpoint(url: String, line: Int, properties: MyBreakpointProperties?): XLineBreakpoint<MyBreakpointProperties> =
    breakpointManager.addLineBreakpoint(MY_LINE_BREAKPOINT_TYPE, url, line, properties)

  fun removeBreakpoint(breakpoint: XBreakpoint<*>) {
    breakpointManager.removeBreakpoint(breakpoint)
  }

  fun load(element: Element?) {
    breakpointManager.loadState(element?.deserialize(BreakpointManagerState::class.java) ?: BreakpointManagerState())
  }

  fun save(): Element? {
    val state = BreakpointManagerState()
    breakpointManager.saveState(state)
    return serialize(state)
  }

  /**
   * Returns the breakpoints of [MY_SIMPLE_BREAKPOINT_TYPE] and [MY_LINE_BREAKPOINT_TYPE], sorted by [MyBreakpointProperties.myOption].
   */
  fun getAllBreakpoints(): List<XBreakpoint<*>> {
    val breakpoints = runReadActionBlocking { breakpointManager.allBreakpoints }
    return breakpoints
      .filter { it.type is MySimpleBreakpointType || it.type is MyLineBreakpointType }
      .sortedWith { o1, o2 ->
        StringUtil.compare((o1.properties as MyBreakpointProperties).myOption, (o2.properties as MyBreakpointProperties).myOption, true)
      }
  }

  /**
   * Creates an empty file at [relativePath] in the temp directory of the test, and the parent directories of the file.
   */
  fun createFile(relativePath: String): VirtualFile {
    val ioFile = tempDir.resolve(relativePath)
    ioFile.parent.createDirectories()
    ioFile.createFile()
    return checkNotNull(VirtualFileManager.getInstance().refreshAndFindFileByNioPath(ioFile)) { ioFile }
  }

  fun rename(file: VirtualFile, newName: String) {
    WriteCommandAction.writeCommandAction(null).run<RuntimeException> { file.rename(file, newName) }
  }

  fun move(file: VirtualFile, newParent: VirtualFile) {
    WriteCommandAction.writeCommandAction(null).run<RuntimeException> { file.move(file, newParent) }
  }

  fun delete(file: VirtualFile) {
    VfsTestUtil.deleteFile(file)
  }

  class MyLineBreakpointType : XLineBreakpointType<MyBreakpointProperties>("testLine", "239") {
    override fun createBreakpointProperties(file: VirtualFile, line: Int): MyBreakpointProperties? = null

    override fun createProperties(): MyBreakpointProperties = MyBreakpointProperties()

    override fun supportsInterLinePlacement(): Boolean = true
  }

  class MySimpleBreakpointType : XBreakpointType<XBreakpoint<MyBreakpointProperties>, MyBreakpointProperties>("test", "239") {
    override fun getDisplayText(breakpoint: XBreakpoint<MyBreakpointProperties>): String = ""

    override fun createProperties(): MyBreakpointProperties = MyBreakpointProperties()

    override fun createDefaultBreakpoint(creator: XBreakpointCreator<MyBreakpointProperties>): XBreakpoint<MyBreakpointProperties> {
      val breakpoint = creator.createBreakpoint(MyBreakpointProperties("default"))
      breakpoint.isEnabled = true
      return breakpoint
    }
  }

  open class MyBreakpointProperties() : XBreakpointProperties<MyBreakpointProperties>() {
    @JvmField
    @Attribute("option")
    var myOption: String? = null

    constructor(option: String?) : this() {
      myOption = option
    }

    override fun getState(): MyBreakpointProperties = this

    override fun loadState(state: MyBreakpointProperties) {
      myOption = state.myOption
    }
  }

  companion object {
    @JvmField
    val MY_LINE_BREAKPOINT_TYPE: MyLineBreakpointType = MyLineBreakpointType()

    @JvmField
    val MY_SIMPLE_BREAKPOINT_TYPE: MySimpleBreakpointType = MySimpleBreakpointType()
  }
}
