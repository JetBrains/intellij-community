package com.intellij.workspaceModel.ide

import com.intellij.openapi.components.BaseState
import com.intellij.openapi.components.PersistentStateComponentWithModificationTracker
import com.intellij.openapi.roots.ModuleExtension
import com.intellij.util.xmlb.annotations.Attribute
import org.jetbrains.jps.model.java.LanguageLevel
import java.util.concurrent.atomic.AtomicInteger

class TestModuleExtension : ModuleExtension, PersistentStateComponentWithModificationTracker<TestModuleExtension.State> {
  private val myWritable: Boolean

  private val mySource: TestModuleExtension?

  private var myState: State? = State()

  override fun getStateModificationCount(): Long {
    return myState!!.modificationCount
  }

  constructor() {
    mySource = null
    myWritable = false
  }

  constructor(source: TestModuleExtension, writable: Boolean) {
    myWritable = writable
    mySource = source
    // setter must be used instead of creating new state with constructor param because in any case default language level for module is null (i.e. project language level)
    myState!!.languageLevel = source.languageLevel
  }

  override fun getState(): State? {
    return myState
  }

  override fun loadState(state: State) {
    myState = state
  }

  override fun getModifiableModel(writable: Boolean): ModuleExtension {
    return TestModuleExtension(this, writable)
  }

  override fun commit() {
    if (isChanged) {
      mySource!!.myState!!.languageLevel = myState!!.languageLevel
      commitCalled.incrementAndGet()
    }
  }

  override fun isChanged(): Boolean {
    return mySource != null && mySource.myState != myState
  }

  override fun dispose() {
    myState = null
  }

  var languageLevel: LanguageLevel?
    set(value) {
      if (myState!!.languageLevel == value) return

      if (!myWritable) error("Writable model can be retrieved from writable ModifiableRootModel")

      myState!!.languageLevel = value
    }
    get() {
      return myState!!.languageLevel
    }

  class State : BaseState() {
    @get:Attribute("LANGUAGE_LEVEL_TEST_EXTENSION")
    var languageLevel by enum<LanguageLevel>()
  }

  companion object {
    val commitCalled = AtomicInteger(0)
  }
}