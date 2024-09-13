// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.devkit.kotlin.inspections

import org.jetbrains.idea.devkit.inspections.PotentialDeadlockInServiceInitializationInspectionTestBase

class KtPotentialDeadlockInServiceInitializationInspectionTest : PotentialDeadlockInServiceInitializationInspectionTestBase() {

  fun `test read and write actions are reported in a light service`() {
    myFixture.configureByText("TestService.kt", getServiceWithReadAndWriteActionsCalledDuringInit(true))
    myFixture.checkHighlighting()
  }

  fun `test read and write actions are reported in an XML-registered service`() {
    myFixture.addFileToProject(
      "META-INF/plugin.xml",
      // language=XML
      """
      <idea-plugin>
        <extensions defaultExtensionNs="com.intellij">
          <applicationService serviceImplementation="TestService"/>
        </extensions>
      </idea-plugin>
    """.trimIndent())
    myFixture.configureByText("TestService.kt", getServiceWithReadAndWriteActionsCalledDuringInit(false))
    myFixture.checkHighlighting()
  }

  private fun getServiceWithReadAndWriteActionsCalledDuringInit(lightService: Boolean): String {
    // language=kotlin
    return """
      import com.intellij.openapi.application.*
      ${if (lightService) "import com.intellij.openapi.components.Service" else ""}
      
      ${if (lightService) "@Service" else ""}
      internal class TestService {
        var v1: String = ReadAction.<error descr="Do not run read actions during service initialization">compute</error><String, RuntimeException> { "any" }
        var v2: String = getV2()
        private fun getV2(): String {
          return ReadAction.<error descr="Do not run read actions during service initialization ('getV2' is called in 'v2' field initializer)">compute</error><String, RuntimeException> { "any" }
        }
      
        init {
          <error descr="Do not run read actions during service initialization">runReadAction</error><Any?> { null }
          <error descr="Do not run write actions during service initialization">runWriteAction</error><Any?> { null }
          readActionMethodUsedInInitBlock()
        }
      
        private fun readActionMethodUsedInInitBlock() {
          ReadAction.nonBlocking<String> { "any" }.<error descr="Do not run read actions during service initialization ('readActionMethodUsedInInitBlock' is called in 'TestService' constructor or init block)">executeSynchronously</error>()
        }
      
        fun notUsedInInit() {
          // should not be reported:
          ApplicationManager.getApplication().runReadAction {
            // do something
          }
        }
      
        companion object {
          val v3: String = ReadAction.<error descr="Do not run read actions during service initialization">computeCancellable</error><String, RuntimeException> { "any" }
          val v4: String = getV4()
          private fun getV4(): String {
            return ReadAction.<error descr="Do not run read actions during service initialization ('getV4' is called in 'v4' field initializer)">computeCancellable</error><String, RuntimeException> { "any" }
          }
      
          init {
            ApplicationManager.getApplication().<error descr="Do not run read actions during service initialization">runReadAction</error> {
              // do something
            }
            ApplicationManager.getApplication().<error descr="Do not run write actions during service initialization">runWriteAction</error> {
              // do something
            }
            ApplicationManager.getApplication().<error descr="Do not run 'invokeAndWait' during service initialization">invokeAndWait</error> {
              // do something
            }
            writeActionMethodUsedInCompanionObjectInitBlock()
          }
      
          private fun writeActionMethodUsedInCompanionObjectInitBlock() {
            WriteAction.<error descr="Do not run write actions during service initialization ('writeActionMethodUsedInCompanionObjectInitBlock' is called in 'Companion' constructor or init block)">run</error><RuntimeException> {
              // do something
            }
          }
      
          fun notUsedInInitStatic() {
            // should not be reported:
            ApplicationManager.getApplication().runWriteAction {
              // do something
            }
          }
        }
      }
      """.trimIndent()
  }

  fun `test read and write actions are not reported in a non-service class`() {
    myFixture.configureByText(
      "TestService.kt",
      // language=kotlin
      """
      import com.intellij.openapi.application.*
      
      internal class NotAService {
        var v1: String = ReadAction.compute<String, RuntimeException> { "any" }
        var v2: String = getV2()
        private fun getV2(): String {
          return ReadAction.compute<String, RuntimeException> { "any" }
        }
      
        init {
          runReadAction<Any?> { null }
          runWriteAction<Any?> { null }
          readActionMethodUsedInInitBlock()
        }
      
        private fun readActionMethodUsedInInitBlock() {
          ReadAction.nonBlocking<String> { "any" }.executeSynchronously()
        }
      
        fun notUsedInInit() {
          // should not be reported:
          ApplicationManager.getApplication().runReadAction {
            // do something
          }
        }
      
        companion object {
          val v3: String = ReadAction.computeCancellable<String, RuntimeException> { "any" }
          val v4: String = getV4()
          private fun getV4(): String {
            return ReadAction.computeCancellable<String, RuntimeException> { "any" }
          }
      
          init {
            ApplicationManager.getApplication().runReadAction {
              // do something
            }
            ApplicationManager.getApplication().runWriteAction {
              // do something
            }
            writeActionMethodUsedInCompanionObjectInitBlock()
          }
      
          private fun writeActionMethodUsedInCompanionObjectInitBlock() {
            WriteAction.run<RuntimeException> {
              // do something
            }
          }
      
          fun notUsedInInitStatic() {
            // should not be reported:
            ApplicationManager.getApplication().runWriteAction {
              // do something
            }
          }
      
          fun notUsedInvokeAndWait() {
            // should not be reported:
            ApplicationManager.getApplication().invokeAndWait {
              // do something
            }
          }
        }
      }
      """.trimIndent()
    )
    myFixture.checkHighlighting()
  }

  fun `test read and write actions are reported in an PersistentStateComponent lifecycle methods`() {
    myFixture.configureByText(
      "TestSettings.kt",
      // language=kotlin
      """
      import com.intellij.openapi.application.ApplicationManager
      import com.intellij.openapi.application.ReadAction
      import com.intellij.openapi.application.WriteAction
      import com.intellij.openapi.components.PersistentStateComponent
      import com.intellij.openapi.components.Service
      import com.intellij.openapi.components.State
      
      @Service(Service.Level.PROJECT)
      @State(name = "TestSettings")
      internal class TestSettings : PersistentStateComponent<TestSettings.State?> {
        internal class State {
          var testValue: Boolean = true
        }
      
        private var myState = State()
      
        @Suppress("UNUSED_VARIABLE")
        override fun loadState(state: State) {
          val value = ReadAction.<error descr="Do not run read actions during service initialization">compute</error><String, RuntimeException> { "any" }
          readActionMethodUsedInLoadState()
          myState = state
        }
      
        private fun readActionMethodUsedInLoadState() {
          ReadAction.nonBlocking<String> { "any" }.<error descr="Do not run read actions during service initialization ('readActionMethodUsedInLoadState' is called in 'loadState' method)">executeSynchronously</error>()
        }
      
        override fun initializeComponent() {
          ReadAction.<error descr="Do not run read actions during service initialization">compute</error><String, RuntimeException> { "any" }
        }
      
        override fun noStateLoaded() {
          WriteAction.<error descr="Do not run write actions during service initialization">run</error><RuntimeException> {
            // do something
          }
        }
      
        override fun getState(): State? {
          // read/write actions are allowed in getState:
          ApplicationManager.getApplication().runReadAction {
            // do something
          }
          ApplicationManager.getApplication().runWriteAction {
            // do something
            }
          return myState
        }
      }
      """.trimIndent())
    myFixture.checkHighlighting()
  }

  fun `test read and write actions are not reported in an object literals and lambdas`() {
    myFixture.configureByText(
      "TestService.kt",
      // language=kotlin
      """
      import com.intellij.openapi.application.ReadAction
      import com.intellij.openapi.application.runReadAction
      import com.intellij.openapi.application.runWriteAction
      import com.intellij.openapi.components.Service
      import com.intellij.openapi.project.Project
      import com.intellij.openapi.project.ProjectCloseListener
      import com.intellij.util.Alarm
      
      
      @Service
      internal class TestService {
        private val myListener1: ProjectCloseListener
        private val myListener2: ProjectCloseListener
        private val myAlarm = <warning descr="[DEPRECATION] 'constructor Alarm()' is deprecated. Please use flow or at least pass coroutineScope">Alarm</warning>()
      
        init {
          myListener1 = object : ProjectCloseListener {
            override fun projectClosed(project: Project) {
              readActionMethodUsedInConstructor()
            }
          }
          myListener2 = object : ProjectCloseListener {
            override fun projectClosed(project: Project) {
              runReadAction<Any?> { null }
            }
          }
          myAlarm.addRequest({
            runWriteAction<Any> { "any" }
          }, 100)
        }
      
        companion object {
          private fun readActionMethodUsedInConstructor() {
            ReadAction.nonBlocking<String> { "any" }.executeSynchronously()
          }
        }
      }
      """.trimIndent()
    )
    myFixture.checkHighlighting()
  }

}
