// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.devkit.k2.inspections

import org.jetbrains.idea.devkit.inspections.PersistentStatePropertyNotSerializedInspectionTestBase

class KtPersistentStatePropertyNotSerializedInspectionTest : PersistentStatePropertyNotSerializedInspectionTestBase() {

  fun `test read-only property of a constructor is reported`() {
    doTest("MySettings.kt", """
      import com.intellij.openapi.components.SerializablePersistentStateComponent
      import com.intellij.openapi.components.State
      import com.intellij.openapi.components.Storage

      @State(name = "MySettings", storages = [Storage("my.xml")])
      internal class MySettings : SerializablePersistentStateComponent<MySettings.MyState>(MyState()) {
        data class MyState(
          @JvmField val <warning descr="Add a store annotation to the field 'showTimestamp', or make it mutable, so the store saves it">showTimestamp</warning>: Boolean = true,
          val <warning descr="Add a store annotation to the field 'showSource', or make it mutable, so the store saves it">showSource</warning>: Boolean = true,
          @JvmField var showLevel: Boolean = true,
          var showThread: Boolean = true,
        )
      }
      """)
  }

  fun `test read-only property of a class body is reported`() {
    doTest("MySettings.kt", """
      import com.intellij.openapi.components.PersistentStateComponent
      import com.intellij.openapi.components.State
      import com.intellij.openapi.components.Storage

      @State(name = "MySettings", storages = [Storage("my.xml")])
      internal class MySettings : PersistentStateComponent<MyState> {
        private var myState = MyState()

        override fun getState(): MyState = myState

        override fun loadState(state: MyState) {
          myState = state
        }
      }

      internal class MyState {
        @JvmField val <warning descr="Add a store annotation to the field 'showTimestamp', or make it mutable, so the store saves it">showTimestamp</warning>: Boolean = true
        val <warning descr="Add a store annotation to the field 'showSource', or make it mutable, so the store saves it">showSource</warning>: Boolean = true
        @JvmField var showLevel: Boolean = true
        var showThread: Boolean = true
        lateinit var format: String
      }
      """)
  }

  fun `test store annotation keeps a read-only property`() {
    doTest("MyState.kt", """
      import com.intellij.openapi.components.BaseState
      import com.intellij.util.xmlb.annotations.Attribute
      import com.intellij.util.xmlb.annotations.OptionTag

      internal class MyState : BaseState() {
        @JvmField @OptionTag val showTimestamp: Boolean = true
        @JvmField @Attribute val showSource: Boolean = true
        @get:OptionTag val <warning descr="Add a store annotation to the field 'showLevel', or make it mutable, so the store saves it">showLevel</warning>: Boolean = true
        @JvmField @com.intellij.configurationStore.Property val <warning descr="Add a store annotation to the field 'showThread', or make it mutable, so the store saves it">showThread</warning>: Boolean = true
      }
      """)
  }

  fun `test state of the DevKit file template is not reported`() {
    doTest("MySettings.kt", """
      import com.intellij.openapi.components.SerializablePersistentStateComponent
      import com.intellij.openapi.components.State
      import com.intellij.openapi.components.Storage
      import com.intellij.util.xmlb.annotations.Property

      @State(name = "MySettings", storages = [Storage("mysettings.xml")])
      internal class MySettings : SerializablePersistentStateComponent<MySettingsState>(MySettingsState()) {
        var value: String?
          get() = state.storeValue
          set(value) {
            updateState {
              it.copy(storeValue = value)
            }
          }
      }

      internal data class MySettingsState(
        @JvmField @Property val storeValue: String? = null
      )
      """)
  }

  fun `test transient property is not reported`() {
    doTest("MyState.kt", """
      import com.intellij.openapi.components.BaseState
      import com.intellij.util.xmlb.annotations.Transient

      internal class MyState : BaseState() {
        @JvmField @Transient val showTimestamp: Boolean = true
        @get:Transient val showSource: Boolean = true
      }
      """)
  }

  fun `test read-only collection property`() {
    doTest("MyState.kt", """
      import com.intellij.openapi.components.BaseState
      import com.intellij.util.xmlb.annotations.XCollection

      internal class MyState : BaseState() {
        @JvmField val fields: MutableList<String> = ArrayList()
        val <warning descr="Add a store annotation to the field 'levels', or make it mutable, so the store saves it">levels</warning>: MutableList<String> = ArrayList()
        @get:XCollection val names: MutableList<String> = ArrayList()
      }
      """)
  }

  fun `test read-only stored property is reported`() {
    doTest("MyState.kt", """
      import com.intellij.openapi.components.BaseState
      import com.intellij.util.xmlb.annotations.XCollection

      internal class MyState : BaseState() {
        val <warning descr="Make the property 'format' mutable, so the store saves it">format</warning>: String? by string()
        val <warning descr="Make the property 'level' mutable, so the store saves it">level</warning>: Int by property(0)
        val <warning descr="Make the property 'fields' mutable, so the store saves it">fields</warning>: MutableList<String> by list()
        private val <warning descr="Make the property 'source' public, so the store saves it">source</warning>: String? by string()
        var thread: String? by string()
        @get:XCollection val names: MutableList<String> by list()
      }
      """)
  }

  fun `test property of a private or protected visibility is not reported`() {
    doTest("MyState.kt", """
      import com.intellij.openapi.components.BaseState

      internal open class MyState : BaseState() {
        private var showTimestamp: Boolean = true
        protected var showSource: Boolean = true
        internal var showLevel: Boolean = true
        private val showThread: Boolean = true
      }
      """)
  }

  fun `test property that the store never binds is not reported`() {
    doTest("MyState.kt", """
      import com.intellij.openapi.components.BaseState

      internal class MyState : BaseState() {
        val format: String by lazy { "text" }
        var isEnabled: Boolean = true
        companion object {
          const val DEFAULT_FORMAT: String = "text"
        }
      }
      """)
  }

  fun `test property of a type with a required constructor parameter is not reported`() {
    doTest("MyState.kt", """
      import com.intellij.openapi.components.BaseState

      internal class MyState : BaseState() {
        val wrapper = Wrapper("text")
      }

      internal class Wrapper(var value: String)
      """)
  }

  fun `test property of a type with a no-arg constructor is reported`() {
    doTest("MyState.kt", """
      import com.intellij.openapi.components.BaseState

      internal class MyState : BaseState() {
        val <warning descr="Add a store annotation to the field 'wrapper', or make it mutable, so the store saves it">wrapper</warning> = Wrapper()
      }

      internal class Wrapper
      """)
  }

  fun `test property of a type whose constructor parameters all have defaults is reported`() {
    // Kotlin generates a real no-arg constructor for a class whose constructor parameters all have a default
    // value, so the store can create such a class. The check must read this constructor from the light class,
    // because it does not show in the source of the primary constructor.
    doTest("MyState.kt", """
      import com.intellij.openapi.components.BaseState

      internal class MyState : BaseState() {
        val <warning descr="Add a store annotation to the field 'wrapper', or make it mutable, so the store saves it">wrapper</warning> = Wrapper()
      }

      internal class Wrapper(var value: String = "text")
      """)
  }

  fun `test property of a class that is not a state is not reported`() {
    doTest("MyService.kt", """
      internal class MyService {
        @JvmField val showTimestamp: Boolean = true
        val showSource: Boolean = true
        private val lock: Any = Any()
      }
      """)
  }

  fun `test stored property of an is-prefixed name is not reported`() {
    doTest("MyState.kt", """
      import com.intellij.openapi.components.BaseState

      internal class MyState : BaseState() {
        var isEnabled: Boolean by property(true)
        var isVisible: Boolean by property(true)
        val <warning descr="Make the property 'isMuted' mutable, so the store saves it">isMuted</warning>: Boolean by property(true)
      }
      """)
  }

  fun `test state class of kotlinx serialization is not reported`() {
    addStub("kotlinx/serialization/Serializable.kt", """
      package kotlinx.serialization

      annotation class Serializable
      """)
    doTest("MySettings.kt", """
      import com.intellij.openapi.components.SerializablePersistentStateComponent
      import com.intellij.openapi.components.State
      import com.intellij.openapi.components.Storage
      import kotlinx.serialization.Serializable

      @State(name = "MySettings", storages = [Storage("my.xml")])
      internal class MySettings : SerializablePersistentStateComponent<MySettings.MyState>(MyState()) {
        @Serializable
        data class MyState(
          val showTimestamp: Boolean = true,
          val showSource: Boolean = true,
        )
      }
      """)
  }

  fun `test dependency of the component is not reported`() {
    addStub("kotlinx/coroutines/CoroutineScope.kt", """
      package kotlinx.coroutines

      interface CoroutineScope
      """)
    addStub("kotlinx/coroutines/flow/Flow.kt", """
      package kotlinx.coroutines.flow

      interface Flow<out T>
      """)
    doTest("MySettings.kt", """
      import com.intellij.openapi.components.PersistentStateComponent
      import com.intellij.openapi.components.State
      import com.intellij.openapi.components.Storage
      import com.intellij.openapi.project.Project
      import kotlinx.coroutines.CoroutineScope
      import kotlinx.coroutines.flow.Flow

      @State(name = "MySettings", storages = [Storage("my.xml")])
      internal class MySettings(val project: Project, val scope: CoroutineScope) : PersistentStateComponent<MySettings> {
        val changes: Flow<Boolean>? = null
        val <warning descr="Add a store annotation to the field 'showTimestamp', or make it mutable, so the store saves it">showTimestamp</warning>: Boolean = true

        override fun getState(): MySettings = this

        override fun loadState(state: MySettings) {
        }
      }
      """)
  }

  fun `test no store annotation fix for a stored property delegate`() {
    // `@Property` does not apply to a delegated Kotlin property. It gives a `WRONG_ANNOTATION_TARGET` error,
    // and it does not bind the property, so the report must offer no annotation fix here.
    myFixture.configureByText("MyState.kt", """
      import com.intellij.openapi.components.BaseState

      internal class MyState : BaseState() {
        val <warning descr="Make the property 'format' mutable, so the store saves it">for<caret>mat</warning>: String? by string()
      }
      """.trimIndent().trim())
    myFixture.checkHighlighting()
    assertEmpty(myFixture.filterAvailableIntentions("Annotate as @Property"))
  }

  fun `test fix adds a store annotation to a read-only property of a class body`() {
    doFixTest("MyState.kt", """
      import com.intellij.openapi.components.BaseState

      internal class MyState : BaseState() {
        val <warning descr="Add a store annotation to the field 'showTimestamp', or make it mutable, so the store saves it">show<caret>Timestamp</warning>: Boolean = true
      }
      """, "Annotate as @Property", """
      import com.intellij.openapi.components.BaseState
      import com.intellij.util.xmlb.annotations.Property

      internal class MyState : BaseState() {
        @Property
        val showTimestamp: Boolean = true
      }
      """)
  }

  fun `test fix adds a store annotation to a read-only property of a constructor`() {
    doFixTest("MySettings.kt", """
      import com.intellij.openapi.components.SerializablePersistentStateComponent
      import com.intellij.openapi.components.State
      import com.intellij.openapi.components.Storage

      @State(name = "MySettings", storages = [Storage("mysettings.xml")])
      internal class MySettings : SerializablePersistentStateComponent<MySettingsState>(MySettingsState())

      internal data class MySettingsState(
        val <warning descr="Add a store annotation to the field 'storeValue', or make it mutable, so the store saves it">store<caret>Value</warning>: String? = null,
      )
      """, "Annotate as @Property", """
      import com.intellij.openapi.components.SerializablePersistentStateComponent
      import com.intellij.openapi.components.State
      import com.intellij.openapi.components.Storage
      import com.intellij.util.xmlb.annotations.Property

      @State(name = "MySettings", storages = [Storage("mysettings.xml")])
      internal class MySettings : SerializablePersistentStateComponent<MySettingsState>(MySettingsState())

      internal data class MySettingsState(
          @Property val storeValue: String? = null,
      )
      """)
  }

  fun `test fix adds XCollection to a read-only collection property`() {
    doFixTest("MyState.kt", """
      import com.intellij.openapi.components.BaseState

      internal class MyState : BaseState() {
        val <warning descr="Add a store annotation to the field 'levels', or make it mutable, so the store saves it">le<caret>vels</warning>: MutableSet<String> = mutableSetOf()
      }
      """, "Annotate as @XCollection", """
      import com.intellij.openapi.components.BaseState
      import com.intellij.util.xmlb.annotations.XCollection

      internal class MyState : BaseState() {
        @XCollection
        val levels: MutableSet<String> = mutableSetOf()
      }
      """)
  }

  fun `test fix adds XMap to a read-only map property`() {
    doFixTest("MyState.kt", """
      import com.intellij.openapi.components.BaseState

      internal class MyState : BaseState() {
        val <warning descr="Add a store annotation to the field 'levels', or make it mutable, so the store saves it">le<caret>vels</warning>: MutableMap<String, String> = mutableMapOf()
      }
      """, "Annotate as @XMap", """
      import com.intellij.openapi.components.BaseState
      import com.intellij.util.xmlb.annotations.XMap

      internal class MyState : BaseState() {
        @XMap
        val levels: MutableMap<String, String> = mutableMapOf()
      }
      """)
  }

  private fun addStub(path: String, text: String) {
    myFixture.addFileToProject(path, text.trimIndent().trim())
  }

  private fun doTest(fileName: String, text: String) {
    myFixture.configureByText(fileName, text.trimIndent().trim())
    myFixture.checkHighlighting()
  }

  private fun doFixTest(fileName: String, before: String, fixName: String, after: String) {
    myFixture.configureByText(fileName, before.trimIndent().trim())
    myFixture.checkHighlighting()
    myFixture.checkPreviewAndLaunchAction(myFixture.findSingleIntention(fixName))
    myFixture.checkResult(after.trimIndent().trim(), true)
    // The inspection repeats the rules of the store, so no warning shows that the annotation reached the backing field.
    myFixture.checkHighlighting()
  }
}
