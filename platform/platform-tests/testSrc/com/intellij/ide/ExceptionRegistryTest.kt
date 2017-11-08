/*
 * Copyright (C) 2017 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.ide

import junit.framework.TestCase
import java.io.IOException
import com.intellij.ide.ExceptionTestUtils.createExceptionFromDesc

class ExceptionRegistryTest : TestCase() {
  /**
   * Generates a report of the registered exceptions, in descending frequency order
   * and optionally with summarizes of the stack traces
   *
   * @param includeSummaries whether to include a stacktrace summary on the right
   * @param maxWidth         the maximum line width to use in the report. Must be at least 60
   * @param threshold        the minimum number of repeats a given stacktrace must have to be included in the report
   * @return the report as a string
   */
  fun getFrequencyReport(
      includeSummaries: Boolean = true,
      maxWidth: Int = 300,
      threshold: Int = 0): String {
    val sb = StringBuilder(1000)
    synchronized(this) {
      val frames = ExceptionRegistry.getStackTraces()
      for (frame in frames) {
        val count = frame.count()
        if (count < threshold) {
          break
        }
        val hash = frame.md5string()
        if (includeSummaries) {
          val summary = frame.summarize(Math.max(maxWidth - 40, 20)) // 40: subtract out the space taken by the frequency and hash strings
          sb.append(String.format("%1$6d %2$32s %3\$s\n", count, hash, summary))
        }
        else {
          sb.append(String.format("%1$6d %2$32s\n", count, hash))
        }
      }
    }
    return sb.toString()
  }

  fun test() {

    // Make sure we can handle the empty scenario
    assertNull(ExceptionRegistry.getMostFrequent())
    assertNull(ExceptionRegistry.getStackTraces().firstOrNull())
    assertNull(ExceptionRegistry.find("not-there"))
    assertEquals("", getFrequencyReport())


    val exceptions = createTestExceptions()

    for (exception in exceptions) {
      ExceptionRegistry.register(exception)
    }

    // let's have a repeated crash
    for (i in 1..10) { // repeated crash
      ExceptionRegistry.register(exceptions[0])
    }

    // Let's repeat another one
    val last = ExceptionRegistry.register(exceptions[5])
    assertEquals("5E389B3192074C7A8260CA82B0ECEE09", last.md5string())

    assertNotNull(ExceptionRegistry.find("5E389B3192074C7A8260CA82B0ECEE09"))

    assertEquals(19, ExceptionRegistry.count)

    assertEquals("""
        11 379B89010804F9BC03263B09AF393F22
         2 F52091828CFC727203AB58D3BBF1A612
         2 5E389B3192074C7A8260CA82B0ECEE09
         1 6F4781E61570E16948ECCE200C16B548
         1 E287C482DD9EA73DD035362F503A7183
         1 ADDCF4E7B9F2324965756AAE9CDD524C
         1 759507990F4C30449A488B4538B92EB5
        """.trimIndent(),
        getFrequencyReport(includeSummaries = false).trimIndent())

    val mostFrequent = ExceptionRegistry.getMostFrequent()
    assertEquals(11, mostFrequent?.count())

    // Only one match for a threshold of 10
    val first = ExceptionRegistry.getStackTraces(10).first()
    assertEquals(11, first.count())


    assertEquals("""
        11 379B89010804F9BC03263B09AF393F22
         2 F52091828CFC727203AB58D3BBF1A612
         2 5E389B3192074C7A8260CA82B0ECEE09
        """.trimIndent(),
        getFrequencyReport(threshold = 2, includeSummaries = false).trimIndent())

    assertEquals("""
        11 379B89010804F9BC03263B09AF393F22 FileNotFoundException: FileInputStream.open0←open:195←<init>:138←CompilerBackwardReferenceIndex.ver…
         2 F52091828CFC727203AB58D3BBF1A612 NullPointerException: ExceptionRegistryTest${'$'}NullPointerException←
         2 5E389B3192074C7A8260CA82B0ECEE09 XmlPullParserException: KXmlParser.exception←error←pushEntity←pushText←nextImpl←next←BridgeXmlBlock…
         1 6F4781E61570E16948ECCE200C16B548 NullPointerException: DateUtils.getDayOfWeekString:248←CalendarView.setUpHeader:1034←<init>:403←333…
         1 E287C482DD9EA73DD035362F503A7183 ArithmeticException: MyCustomView.<init>:13←NativeConstructorAccessorImpl.newInstance0←newInstance:…
         1 ADDCF4E7B9F2324965756AAE9CDD524C FileNotFoundException: ComponentManagerImpl.init:104←ApplicationImpl.load:425←411←IdeaApplication.r…
         1 759507990F4C30449A488B4538B92EB5 InternalException: JDWPException.toJDIException:65←StackFrameImpl.getValues:241←StackFrameProxyImpl…
        """.trimIndent(),
        getFrequencyReport(maxWidth = 140).trimIndent())

    assertEquals("""
java.io.FileNotFoundException:
	at com.intellij.openapi.components.impl.ComponentManagerImpl.init(ComponentManagerImpl.java:init)
	at com.intellij.openapi.application.impl.ApplicationImpl.load(ApplicationImpl.java:load)
	at com.intellij.openapi.application.impl.ApplicationImpl.load(ApplicationImpl.java:load)
	at com.intellij.idea.IdeaApplication.run(IdeaApplication.java:run)
	at org.jetbrains.jps.backwardRefs.CompilerBackwardReferenceIndex.versionDiffers(CompilerBackwardReferenceIndex.java:versionDiffers)
	at com.intellij.compiler.backwardRefs.CompilerReferenceReader.exists(CompilerReferenceReader.java:exists)
	at com.intellij.compiler.backwardRefs.CompilerReferenceReader.create(CompilerReferenceReader.java:create)
	at com.intellij.compiler.backwardRefs.CompilerReferenceServiceImpl.openReaderIfNeed(CompilerReferenceServiceImpl.java:openReaderIfNeed)
	at com.intellij.compiler.backwardRefs.CompilerReferenceServiceImpl.access.600(CompilerReferenceServiceImpl.java:access.600)
	at com.intellij.compiler.backwardRefs.CompilerReferenceServiceImpl.2.lambda.compilationFinished.1(CompilerReferenceServiceImpl.java:lambda.compilationFinished.1)
	at java.util.concurrent.Executors.RunnableAdapter.call(Executors.java:call)
	at java.util.concurrent.FutureTask.run(FutureTask.java:run)
	at com.intellij.util.concurrency.BoundedTaskExecutor.2.run(BoundedTaskExecutor.java:run)
	at java.util.concurrent.ThreadPoolExecutor.runWorker(ThreadPoolExecutor.java:runWorker)
	at java.util.concurrent.ThreadPoolExecutor.Worker.run(ThreadPoolExecutor.java:run)
	at java.lang.Thread.run(Thread.java:run)
        """.trim(),
        ExceptionRegistry.find("ADDCF4E7B9F2324965756AAE9CDD524C")!!.toStackTrace().trim().replace("$","."))

    ExceptionRegistry.clear()
    assertEquals(0, ExceptionRegistry.count)
    assertEquals(0, ExceptionRegistry.getStackTraces().count())
    ExceptionRegistry.register(IOException())
    assertEquals(1, ExceptionRegistry.count)
    assertEquals(1, ExceptionRegistry.getStackTraces().count())
  }

  private fun createTestExceptions(): List<Throwable> {
    val result = mutableListOf<Throwable>()

    result.add(createExceptionFromDesc(
        "java.io.FileNotFoundException: \n" +
            "\tat java.io.FileInputStream.open0(Native Method)\n" +
            "\tat java.io.FileInputStream.open(FileInputStream.java:195)\n" +
            "\tat java.io.FileInputStream.<init>(FileInputStream.java:138)\n" +
            "\tat org.jetbrains.jps.backwardRefs.CompilerBackwardReferenceIndex.versionDiffers(CompilerBackwardReferenceIndex.java:162)\n" +
            "\tat com.intellij.compiler.backwardRefs.CompilerReferenceReader.exists(CompilerReferenceReader.java:114)\n" +
            "\tat com.intellij.compiler.backwardRefs.CompilerReferenceReader.create(CompilerReferenceReader.java:121)\n" +
            "\tat com.intellij.compiler.backwardRefs.CompilerReferenceServiceImpl.openReaderIfNeed(CompilerReferenceServiceImpl.java:363)\n" +
            "\tat com.intellij.compiler.backwardRefs.CompilerReferenceServiceImpl.access\$600(CompilerReferenceServiceImpl.java:72)\n" +
            "\tat com.intellij.compiler.backwardRefs.CompilerReferenceServiceImpl\$2.lambda\$600compilationFinished$1(CompilerReferenceServiceImpl.java:128)\n" +
            "\tat java.util.concurrent.Executors\$RunnableAdapter.call(Executors.java:511)\n" +
            "\tat java.util.concurrent.FutureTask.run(FutureTask.java:266)\n" +
            "\tat com.intellij.util.concurrency.BoundedTaskExecutor$2.run(BoundedTaskExecutor.java:212)\n" +
            "\tat java.util.concurrent.ThreadPoolExecutor.runWorker(ThreadPoolExecutor.java:1142)\n" +
            "\tat java.util.concurrent.ThreadPoolExecutor\$Worker.run(ThreadPoolExecutor.java:617)\n" +
            "\tat java.lang.Thread.run(Thread.java:745)\n"))

    result.add(createExceptionFromDesc(
        // Not a real/realistic stacktrace; just a variation of the top half of throwable1
        "java.io.FileNotFoundException: \n" +
            "\tat com.intellij.openapi.components.impl.ComponentManagerImpl.init(ComponentManagerImpl.java:104)\n" +
            "\tat com.intellij.openapi.application.impl.ApplicationImpl.load(ApplicationImpl.java:425)\n" +
            "\tat com.intellij.openapi.application.impl.ApplicationImpl.load(ApplicationImpl.java:411)\n" +
            "\tat com.intellij.idea.IdeaApplication.run(IdeaApplication.java:199)\n" +
            "\tat org.jetbrains.jps.backwardRefs.CompilerBackwardReferenceIndex.versionDiffers(CompilerBackwardReferenceIndex.java:162)\n" +
            "\tat com.intellij.compiler.backwardRefs.CompilerReferenceReader.exists(CompilerReferenceReader.java:114)\n" +
            "\tat com.intellij.compiler.backwardRefs.CompilerReferenceReader.create(CompilerReferenceReader.java:121)\n" +
            "\tat com.intellij.compiler.backwardRefs.CompilerReferenceServiceImpl.openReaderIfNeed(CompilerReferenceServiceImpl.java:363)\n" +
            "\tat com.intellij.compiler.backwardRefs.CompilerReferenceServiceImpl.access\$600(CompilerReferenceServiceImpl.java:72)\n" +
            "\tat com.intellij.compiler.backwardRefs.CompilerReferenceServiceImpl\$2.lambda\$compilationFinished$1(CompilerReferenceServiceImpl.java:128)\n" +
            "\tat java.util.concurrent.Executors\$RunnableAdapter.call(Executors.java:511)\n" +
            "\tat java.util.concurrent.FutureTask.run(FutureTask.java:266)\n" +
            "\tat com.intellij.util.concurrency.BoundedTaskExecutor$2.run(BoundedTaskExecutor.java:212)\n" +
            "\tat java.util.concurrent.ThreadPoolExecutor.runWorker(ThreadPoolExecutor.java:1142)\n" +
            "\tat java.util.concurrent.ThreadPoolExecutor\$Worker.run(ThreadPoolExecutor.java:617)\n" +
            "\tat java.lang.Thread.run(Thread.java:745)\n"))

    result.add(createExceptionFromDesc(
        "com.sun.jdi.InternalException: Unexpected JDWP Error: 35\n" +
            "\tat com.sun.tools.jdi.JDWPException.toJDIException(JDWPException.java:65)\n" +
            "\tat com.sun.tools.jdi.StackFrameImpl.getValues(StackFrameImpl.java:241)\n" +
            "\tat com.intellij.debugger.jdi.StackFrameProxyImpl.getAllValues(StackFrameProxyImpl.java:365)\n" +
            "\tat com.intellij.debugger.jdi.StackFrameProxyImpl.getValue(StackFrameProxyImpl.java:308)\n" +
            "\tat com.intellij.debugger.ui.impl.watch.LocalVariableDescriptorImpl.calcValue(LocalVariableDescriptorImpl.java:80)\n" +
            "\tat com.intellij.debugger.ui.impl.watch.ValueDescriptorImpl.setContext(ValueDescriptorImpl.java:198)\n" +
            "\tat com.intellij.debugger.engine.JavaValue$1.contextAction(JavaValue.java:149)\n" +
            "\tat com.intellij.debugger.engine.events.SuspendContextCommandImpl.contextAction(SuspendContextCommandImpl.java:48)\n" +
            "\tat com.intellij.debugger.engine.events.SuspendContextCommandImpl.action(SuspendContextCommandImpl.java:73)\n" +
            "\tat com.intellij.debugger.engine.events.DebuggerCommandImpl.run(DebuggerCommandImpl.java:45)\n" +
            "\tat com.intellij.debugger.engine.DebuggerManagerThreadImpl.processEvent(DebuggerManagerThreadImpl.java:146)\n" +
            "\tat com.intellij.debugger.engine.DebuggerManagerThreadImpl.processEvent(DebuggerManagerThreadImpl.java:42)\n" +
            "\tat com.intellij.debugger.impl.InvokeThread.run(InvokeThread.java:151)\n" +
            "\tat com.intellij.debugger.impl.InvokeThread.access$100(InvokeThread.java:31)\n" +
            "\tat com.intellij.debugger.impl.InvokeThread\$WorkerThreadRequest.run(InvokeThread.java:60)\n" +
            "\tat com.intellij.openapi.application.impl.ApplicationImpl$2.run(ApplicationImpl.java:334)\n" +
            "\tat java.util.concurrent.Executors\$RunnableAdapter.call(Executors.java:511)\n" +
            "\tat java.util.concurrent.FutureTask.run(FutureTask.java:266)\n" +
            "\tat java.util.concurrent.ThreadPoolExecutor.runWorker(ThreadPoolExecutor.java:1142)\n" +
            "\tat java.util.concurrent.ThreadPoolExecutor\$Worker.run(ThreadPoolExecutor.java:617)\n" +
            "\tat java.lang.Thread.run(Thread.java:745)\n",
        com.sun.jdi.InternalException("Unexpected JDWP Error: 35")))

    result.add(createExceptionFromDesc(
        "java.lang.NullPointerException\n" +
            "\tat android.text.format.DateUtils.getDayOfWeekString(DateUtils.java:248)\n" +
            "\tat android.widget.CalendarView.setUpHeader(CalendarView.java:1034)\n" +
            "\tat android.widget.CalendarView.<init>(CalendarView.java:403)\n" +
            "\tat android.widget.CalendarView.<init>(CalendarView.java:333)\n" +
            "\tat sun.reflect.NativeConstructorAccessorImpl.newInstance0(Native Method)\n" +
            "\tat sun.reflect.NativeConstructorAccessorImpl.newInstance(NativeConstructorAccessorImpl.java:39)\n" +
            "\tat sun.reflect.DelegatingConstructorAccessorImpl.newInstance(DelegatingConstructorAccessorImpl.java:27)\n" +
            "\tat java.lang.reflect.Constructor.newInstance(Constructor.java:513)\n" +
            "\tat android.view.LayoutInflater.createView(LayoutInflater.java:594)\n" +
            "\tat android.view.BridgeInflater.onCreateView(BridgeInflater.java:86)\n" +
            "\tat android.view.LayoutInflater.onCreateView(LayoutInflater.java:669)\n" +
            "\tat android.view.LayoutInflater.createViewFromTag(LayoutInflater.java:694)\n" +
            "\tat android.view.BridgeInflater.createViewFromTag(BridgeInflater.java:131)\n" +
            "\tat android.view.LayoutInflater.rInflate_Original(LayoutInflater.java:755)\n" +
            "\tat android.view.LayoutInflater_Delegate.rInflate(LayoutInflater_Delegate.java:64)\n" +
            "\tat android.view.LayoutInflater.rInflate(LayoutInflater.java:727)\n" +
            "\tat android.view.LayoutInflater.inflate(LayoutInflater.java:492)\n" +
            "\tat android.view.LayoutInflater.inflate(LayoutInflater.java:397)\n" +
            "\tat android.widget.DatePicker.<init>(DatePicker.java:171)\n" +
            "\tat android.widget.DatePicker.<init>(DatePicker.java:145)\n" +
            "\tat sun.reflect.NativeConstructorAccessorImpl.newInstance0(Native Method)\n" +
            "\tat sun.reflect.NativeConstructorAccessorImpl.newInstance(NativeConstructorAccessorImpl.java:39)\n" +
            "\tat sun.reflect.DelegatingConstructorAccessorImpl.newInstance(DelegatingConstructorAccessorImpl.java:27)\n" +
            "\tat java.lang.reflect.Constructor.newInstance(Constructor.java:513)\n" +
            "\tat android.view.LayoutInflater.createView(LayoutInflater.java:594)\n" +
            "\tat android.view.BridgeInflater.onCreateView(BridgeInflater.java:86)\n" +
            "\tat android.view.LayoutInflater.onCreateView(LayoutInflater.java:669)\n" +
            "\tat android.view.LayoutInflater.createViewFromTag(LayoutInflater.java:694)\n" +
            "\tat android.view.BridgeInflater.createViewFromTag(BridgeInflater.java:131)\n" +
            "\tat android.view.LayoutInflater.rInflate_Original(LayoutInflater.java:755)\n" +
            "\tat android.view.LayoutInflater_Delegate.rInflate(LayoutInflater_Delegate.java:64)\n" +
            "\tat android.view.LayoutInflater.rInflate(LayoutInflater.java:727)\n" +
            "\tat android.view.LayoutInflater.inflate(LayoutInflater.java:492)\n" +
            "\tat android.view.LayoutInflater.inflate(LayoutInflater.java:373)\n" +
            "\tat com.android.layoutlib.bridge.impl.RenderSessionImpl.inflate(RenderSessionImpl.java:385)\n" +
            "\tat com.android.layoutlib.bridge.Bridge.createSession(Bridge.java:332)\n" +
            "\tat com.android.ide.common.rendering.LayoutLibrary.createSession(LayoutLibrary.java:325)\n" +
            "\tat com.android.tools.idea.rendering.RenderService$3.compute(RenderService.java:525)\n" +
            "\tat com.android.tools.idea.rendering.RenderService$3.compute(RenderService.java:518)\n" +
            "\tat com.intellij.openapi.application.impl.ApplicationImpl.runReadAction(ApplicationImpl.java:958)\n" +
            "\tat com.android.tools.idea.rendering.RenderService.createRenderSession(RenderService.java:518)\n" +
            "\tat com.android.tools.idea.rendering.RenderService.render(RenderService.java:555)\n" +
            "\tat com.intellij.android.designer.designSurface.AndroidDesignerEditorPanel$7$2.compute(AndroidDesignerEditorPanel.java:498)\n" +
            "\tat com.intellij.android.designer.designSurface.AndroidDesignerEditorPanel$7$2.compute(AndroidDesignerEditorPanel.java:491)\n" +
            "\tat com.intellij.openapi.application.impl.ApplicationImpl.runReadAction(ApplicationImpl.java:969)\n" +
            "\tat com.intellij.android.designer.designSurface.AndroidDesignerEditorPanel$7.run(AndroidDesignerEditorPanel.java:491)\n" +
            "\tat com.intellij.util.ui.update.MergingUpdateQueue.execute(MergingUpdateQueue.java:320)\n" +
            "\tat com.intellij.util.ui.update.MergingUpdateQueue.execute(MergingUpdateQueue.java:310)\n" +
            "\tat com.intellij.util.ui.update.MergingUpdateQueue$2.run(MergingUpdateQueue.java:254)\n" +
            "\tat com.intellij.util.ui.update.MergingUpdateQueue.flush(MergingUpdateQueue.java:269)\n" +
            "\tat com.intellij.util.ui.update.MergingUpdateQueue.flush(MergingUpdateQueue.java:227)\n" +
            "\tat com.intellij.util.ui.update.MergingUpdateQueue.run(MergingUpdateQueue.java:217)\n" +
            "\tat com.intellij.util.concurrency.QueueProcessor.runSafely(QueueProcessor.java:237)\n" +
            "\tat com.intellij.util.Alarm\$Request$1.run(Alarm.java:297)\n" +
            "\tat java.util.concurrent.Executors\$RunnableAdapter.call(Executors.java:439)\n" +
            "\tat java.util.concurrent.FutureTask\$Sync.innerRun(FutureTask.java:303)\n" +
            "\tat java.util.concurrent.FutureTask.run(FutureTask.java:138)\n" +
            "\tat java.util.concurrent.ThreadPoolExecutor\$Worker.runTask(ThreadPoolExecutor.java:895)\n" +
            "\tat java.util.concurrent.ThreadPoolExecutor\$Worker.run(ThreadPoolExecutor.java:918)\n" +
            "\tat java.lang.Thread.run(Thread.java:680)\n"))

    result.add(createExceptionFromDesc(
        "java.lang.ArithmeticException: / by zero\n" +
            "\tat com.example.myapplication574.MyCustomView.<init>(MyCustomView.java:13)\n" +
            "\tat sun.reflect.NativeConstructorAccessorImpl.newInstance0(Native Method)\n" +
            "\tat sun.reflect.NativeConstructorAccessorImpl.newInstance(NativeConstructorAccessorImpl.java:39)\n" +
            "\tat sun.reflect.DelegatingConstructorAccessorImpl.newInstance(DelegatingConstructorAccessorImpl.java:27)\n" +
            "\tat java.lang.reflect.Constructor.newInstance(Constructor.java:513)\n" +
            "\tat org.jetbrains.android.uipreview.ViewLoader.createNewInstance(ViewLoader.java:365)\n" +
            "\tat org.jetbrains.android.uipreview.ViewLoader.loadView(ViewLoader.java:97)\n" +
            "\tat com.android.tools.idea.rendering.LayoutlibCallback.loadView(LayoutlibCallback.java:121)\n" +
            "\tat android.view.BridgeInflater.loadCustomView(BridgeInflater.java:207)\n" +
            "\tat android.view.BridgeInflater.createViewFromTag(BridgeInflater.java:135)\n" +
            "\tat android.view.LayoutInflater.rInflate_Original(LayoutInflater.java:755)\n" +
            "\tat android.view.LayoutInflater_Delegate.rInflate(LayoutInflater_Delegate.java:64)\n" +
            "\tat android.view.LayoutInflater.rInflate(LayoutInflater.java:727)\n" +
            "\tat android.view.LayoutInflater.inflate(LayoutInflater.java:492)\n" +
            "\tat android.view.LayoutInflater.inflate(LayoutInflater.java:373)\n" +
            "\tat com.android.layoutlib.bridge.impl.RenderSessionImpl.inflate(RenderSessionImpl.java:385)\n" +
            "\tat com.android.layoutlib.bridge.Bridge.createSession(Bridge.java:332)\n" +
            "\tat com.android.ide.common.rendering.LayoutLibrary.createSession(LayoutLibrary.java:325)\n" +
            "\tat com.android.tools.idea.rendering.RenderService$3.compute(RenderService.java:525)\n" +
            "\tat com.android.tools.idea.rendering.RenderService$3.compute(RenderService.java:518)\n" +
            "\tat com.intellij.openapi.application.impl.ApplicationImpl.runReadAction(ApplicationImpl.java:958)\n" +
            "\tat com.android.tools.idea.rendering.RenderService.createRenderSession(RenderService.java:518)\n" +
            "\tat com.android.tools.idea.rendering.RenderService.render(RenderService.java:555)\n" +
            "\tat org.jetbrains.android.uipreview.AndroidLayoutPreviewToolWindowManager$9.compute(AndroidLayoutPreviewToolWindowManager.java:418)\n" +
            "\tat org.jetbrains.android.uipreview.AndroidLayoutPreviewToolWindowManager$9.compute(AndroidLayoutPreviewToolWindowManager.java:411)\n" +
            "\tat com.intellij.openapi.application.impl.ApplicationImpl.runReadAction(ApplicationImpl.java:969)\n" +
            "\tat org.jetbrains.android.uipreview.AndroidLayoutPreviewToolWindowManager.doRender(AndroidLayoutPreviewToolWindowManager.java:411)\n" +
            "\tat org.jetbrains.android.uipreview.AndroidLayoutPreviewToolWindowManager.access$1100(AndroidLayoutPreviewToolWindowManager.java:79)\n" +
            "\tat org.jetbrains.android.uipreview.AndroidLayoutPreviewToolWindowManager$8$1.run(AndroidLayoutPreviewToolWindowManager.java:373)\n" +
            "\tat com.intellij.openapi.progress.impl.ProgressManagerImpl$2.run(ProgressManagerImpl.java:178)\n" +
            "\tat com.intellij.openapi.progress.ProgressManager.executeProcessUnderProgress(ProgressManager.java:207)\n" +
            "\tat com.intellij.openapi.progress.impl.ProgressManagerImpl.executeProcessUnderProgress(ProgressManagerImpl.java:212)\n" +
            "\tat com.intellij.openapi.progress.impl.ProgressManagerImpl.runProcess(ProgressManagerImpl.java:171)\n" +
            "\tat org.jetbrains.android.uipreview.AndroidLayoutPreviewToolWindowManager$8.run(AndroidLayoutPreviewToolWindowManager.java:368)\n" +
            "\tat com.intellij.util.ui.update.MergingUpdateQueue.execute(MergingUpdateQueue.java:320)\n" +
            "\tat com.intellij.util.ui.update.MergingUpdateQueue.execute(MergingUpdateQueue.java:310)\n" +
            "\tat com.intellij.util.ui.update.MergingUpdateQueue$2.run(MergingUpdateQueue.java:254)\n" +
            "\tat com.intellij.util.ui.update.MergingUpdateQueue.flush(MergingUpdateQueue.java:269)\n" +
            "\tat com.intellij.util.ui.update.MergingUpdateQueue.flush(MergingUpdateQueue.java:227)\n" +
            "\tat com.intellij.util.ui.update.MergingUpdateQueue.run(MergingUpdateQueue.java:217)\n" +
            "\tat com.intellij.util.concurrency.QueueProcessor.runSafely(QueueProcessor.java:237)\n" +
            "\tat com.intellij.util.Alarm\$Request$1.run(Alarm.java:297)\n" +
            "\tat java.util.concurrent.Executors\$RunnableAdapter.call(Executors.java:439)\n" +
            "\tat java.util.concurrent.FutureTask\$Sync.innerRun(FutureTask.java:303)\n" +
            "\tat java.util.concurrent.FutureTask.run(FutureTask.java:138)\n" +
            "\tat java.util.concurrent.ThreadPoolExecutor\$Worker.runTask(ThreadPoolExecutor.java:895)\n" +
            "\tat java.util.concurrent.ThreadPoolExecutor\$Worker.run(ThreadPoolExecutor.java:918)\n" +
            "\tat java.lang.Thread.run(Thread.java:680)\n"))

    result.add(createExceptionFromDesc(
        "org.xmlpull.v1.XmlPullParserException: unterminated entity ref (position:TEXT \u0050PNG\u001A\u0000\u0000\u0000" +
            "IHDR\u0000...@8:38 in java.io.InputStreamReader@12caea1b)\n" +
            "\tat org.kxml2.io.KXmlParser.exception(Unknown Source)\n" +
            "\tat org.kxml2.io.KXmlParser.error(Unknown Source)\n" +
            "\tat org.kxml2.io.KXmlParser.pushEntity(Unknown Source)\n" +
            "\tat org.kxml2.io.KXmlParser.pushText(Unknown Source)\n" +
            "\tat org.kxml2.io.KXmlParser.nextImpl(Unknown Source)\n" +
            "\tat org.kxml2.io.KXmlParser.next(Unknown Source)\n" +
            "\tat com.android.layoutlib.bridge.android.BridgeXmlBlockParser.next(BridgeXmlBlockParser.java:301)\n" +
            "\tat android.content.res.ColorStateList.createFromXml(ColorStateList.java:122)\n" +
            "\tat android.content.res.BridgeTypedArray.getColorStateList(BridgeTypedArray.java:373)\n" +
            "\tat android.widget.TextView.<init>(TextView.java:956)\n" +
            "\tat android.widget.Button.<init>(Button.java:107)\n" +
            "\tat android.widget.Button.<init>(Button.java:103)\n" +
            "\tat sun.reflect.GeneratedConstructorAccessor53.newInstance(Unknown Source)\n" +
            "\tat sun.reflect.DelegatingConstructorAccessorImpl.newInstance(DelegatingConstructorAccessorImpl.java:27)\n" +
            "\tat java.lang.reflect.Constructor.newInstance(Constructor.java:513)\n" +
            "\tat android.view.LayoutInflater.createView(LayoutInflater.java:594)\n" +
            "\tat android.view.BridgeInflater.onCreateView(BridgeInflater.java:86)\n" +
            "\tat android.view.LayoutInflater.onCreateView(LayoutInflater.java:669)\n" +
            "\tat android.view.LayoutInflater.createViewFromTag(LayoutInflater.java:694)\n" +
            "\tat android.view.BridgeInflater.createViewFromTag(BridgeInflater.java:131)\n" +
            "\tat android.view.LayoutInflater.rInflate_Original(LayoutInflater.java:755)\n" +
            "\tat android.view.LayoutInflater_Delegate.rInflate(LayoutInflater_Delegate.java:64)\n" +
            "\tat android.view.LayoutInflater.rInflate(LayoutInflater.java:727)\n" +
            "\tat android.view.LayoutInflater.rInflate_Original(LayoutInflater.java:758)\n" +
            "\tat android.view.LayoutInflater_Delegate.rInflate(LayoutInflater_Delegate.java:64)\n" +
            "\tat android.view.LayoutInflater.rInflate(LayoutInflater.java:727)\n" +
            "\tat android.view.LayoutInflater.rInflate_Original(LayoutInflater.java:758)\n" +
            "\tat android.view.LayoutInflater_Delegate.rInflate(LayoutInflater_Delegate.java:64)\n" +
            "\tat android.view.LayoutInflater.rInflate(LayoutInflater.java:727)\n" +
            "\tat android.view.LayoutInflater.inflate(LayoutInflater.java:492)\n" +
            "\tat android.view.LayoutInflater.inflate(LayoutInflater.java:373)\n" +
            "\tat com.android.layoutlib.bridge.impl.RenderSessionImpl.inflate(RenderSessionImpl.java:400)\n" +
            "\tat com.android.layoutlib.bridge.Bridge.createSession(Bridge.java:336)\n" +
            "\tat com.android.ide.common.rendering.LayoutLibrary.createSession(LayoutLibrary.java:332)\n" +
            "\tat com.android.tools.idea.rendering.RenderService$3.compute(RenderService.java:527)\n" +
            "\tat com.android.tools.idea.rendering.RenderService$3.compute(RenderService.java:520)\n" +
            "\tat com.intellij.openapi.application.impl.ApplicationImpl.runReadAction(ApplicationImpl.java:957)\n" +
            "\tat com.android.tools.idea.rendering.RenderService.createRenderSession(RenderService.java:520)\n" +
            "\tat com.android.tools.idea.rendering.RenderService.render(RenderService.java:557)\n" +
            "\tat org.jetbrains.android.uipreview.AndroidLayoutPreviewToolWindowManager$9.compute(AndroidLayoutPreviewToolWindowManager.java:418)\n" +
            "\tat org.jetbrains.android.uipreview.AndroidLayoutPreviewToolWindowManager$9.compute(AndroidLayoutPreviewToolWindowManager.java:411)\n" +
            "\tat com.intellij.openapi.application.impl.ApplicationImpl.runReadAction(ApplicationImpl.java:968)\n" +
            "\tat org.jetbrains.android.uipreview.AndroidLayoutPreviewToolWindowManager.doRender(AndroidLayoutPreviewToolWindowManager.java:411)\n" +
            "\tat org.jetbrains.android.uipreview.AndroidLayoutPreviewToolWindowManager.access$1100(AndroidLayoutPreviewToolWindowManager.java:79)\n" +
            "\tat org.jetbrains.android.uipreview.AndroidLayoutPreviewToolWindowManager$8$1.run(AndroidLayoutPreviewToolWindowManager.java:373)\n" +
            "\tat com.intellij.openapi.progress.impl.ProgressManagerImpl$2.run(ProgressManagerImpl.java:178)\n" +
            "\tat com.intellij.openapi.progress.ProgressManager.executeProcessUnderProgress(ProgressManager.java:209)\n" +
            "\tat com.intellij.openapi.progress.impl.ProgressManagerImpl.executeProcessUnderProgress(ProgressManagerImpl.java:212)\n" +
            "\tat com.intellij.openapi.progress.impl.ProgressManagerImpl.runProcess(ProgressManagerImpl.java:171)\n" +
            "\tat org.jetbrains.android.uipreview.AndroidLayoutPreviewToolWindowManager$8.run(AndroidLayoutPreviewToolWindowManager.java:368)\n" +
            "\tat com.intellij.util.ui.update.MergingUpdateQueue.execute(MergingUpdateQueue.java:320)\n" +
            "\tat com.intellij.util.ui.update.MergingUpdateQueue.execute(MergingUpdateQueue.java:310)\n" +
            "\tat com.intellij.util.ui.update.MergingUpdateQueue$2.run(MergingUpdateQueue.java:254)\n" +
            "\tat com.intellij.util.ui.update.MergingUpdateQueue.flush(MergingUpdateQueue.java:269)\n" +
            "\tat com.intellij.util.ui.update.MergingUpdateQueue.flush(MergingUpdateQueue.java:227)\n" +
            "\tat com.intellij.util.ui.update.MergingUpdateQueue.run(MergingUpdateQueue.java:217)\n" +
            "\tat com.intellij.util.concurrency.QueueProcessor.runSafely(QueueProcessor.java:237)\n" +
            "\tat com.intellij.util.Alarm\$Request$1.run(Alarm.java:297)\n" +
            "\tat java.util.concurrent.Executors\$RunnableAdapter.call(Executors.java:439)\n" +
            "\tat java.util.concurrent.FutureTask\$Sync.innerRun(FutureTask.java:303)\n" +
            "\tat java.util.concurrent.FutureTask.run(FutureTask.java:138)\n" +
            "\tat java.util.concurrent.ThreadPoolExecutor\$Worker.runTask(ThreadPoolExecutor.java:895)\n" +
            "\tat java.util.concurrent.ThreadPoolExecutor\$Worker.run(ThreadPoolExecutor.java:918)\n" +
            "\tat java.lang.Thread.run(Thread.java:680)\n"))

    // With -XX:+OmitStackTraceInFastThrow, the JVM may throw "fast", canned exceptions without a stack trace.
    result.add(NullPointerException())
    result.add(NullPointerException())

    return result
  }

  private class NullPointerException internal constructor() : Exception("", null, false, false) // no stack trace
}