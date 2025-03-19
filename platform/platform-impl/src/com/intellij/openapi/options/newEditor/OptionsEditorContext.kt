// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:Suppress("ReplaceGetOrSet")

package com.intellij.openapi.options.newEditor

import com.intellij.openapi.options.Configurable
import com.intellij.openapi.options.Configurable.InnerWithModifiableParent
import com.intellij.openapi.options.ConfigurationException
import com.intellij.openapi.options.ex.ConfigurableWrapper
import com.intellij.util.containers.MultiMap
import org.jetbrains.concurrency.Promise
import org.jetbrains.concurrency.all
import org.jetbrains.concurrency.rejectedPromise
import org.jetbrains.concurrency.resolvedPromise
import java.util.*
import java.util.concurrent.CopyOnWriteArraySet

internal class OptionsEditorContext {
  @JvmField
  var isHoldingFilter: Boolean = false

  private var colleagues = CopyOnWriteArraySet<OptionsEditorColleague>()

  var currentConfigurable: Configurable? = null
  private var modified = CopyOnWriteArraySet<Configurable>()

  var errors: Map<Configurable, ConfigurationException> = emptyMap()
    private set

  private val configurableToParentMap = HashMap<Configurable, Configurable>()
  private val parentToChildrenMap = MultiMap<Configurable, Configurable>()

  fun fireSelected(configurable: Configurable?, requestor: OptionsEditorColleague): Promise<*> {
    if (this.currentConfigurable === configurable) {
      return resolvedPromise<Any?>()
    }

    val old = this.currentConfigurable
    this.currentConfigurable = configurable

    return notify(object : ColleagueAction {
      override fun process(colleague: OptionsEditorColleague): Promise<Any?> {
        return colleague.onSelected(configurable, old)
      }
    }, requestor)
  }

  fun fireModifiedAdded(configurable: Configurable, requestor: OptionsEditorColleague?): Promise<*> {
    if (modified.contains(configurable)) {
      return rejectedPromise<Any?>()
    }

    modified.add(configurable)

    return notify(object : ColleagueAction {
      override fun process(colleague: OptionsEditorColleague): Promise<Any?> {
        return colleague.onModifiedAdded(configurable)
      }
    }, requestor)
  }

  fun fireModifiedRemoved(configurable: Configurable, requestor: OptionsEditorColleague?): Promise<*> {
    if (!modified.contains(configurable)) {
      return rejectedPromise<Any?>()
    }

    modified.remove(configurable)

    return notify(object : ColleagueAction {
      override fun process(colleague: OptionsEditorColleague): Promise<*> {
        return colleague.onModifiedRemoved(configurable)
      }
    }, requestor)
  }

  fun fireErrorsChanged(errors: MutableMap<Configurable, ConfigurationException>?, requestor: OptionsEditorColleague?): Promise<*> {
    if (this.errors == errors) {
      return rejectedPromise<Any?>()
    }

    this.errors = errors ?: HashMap()

    return notify(object : ColleagueAction {
      override fun process(colleague: OptionsEditorColleague): Promise<Any?> {
        return colleague.onErrorsChanged()
      }
    }, requestor)
  }

  fun notify(action: ColleagueAction, requestor: OptionsEditorColleague?): Promise<*> {
    return colleagues.mapNotNull {
      if (it === requestor) null else action.process(it)
    }.all()
  }

  fun fireReset(configurable: Configurable) {
    if (modified.contains(configurable)) {
      fireModifiedRemoved(configurable, null)
    }

    if (errors.containsKey(configurable)) {
      val newErrors = HashMap(this.errors)
      newErrors.remove(configurable)
      fireErrorsChanged(newErrors, null)
    }
  }

  fun isModified(configurable: Configurable?): Boolean {
    if (modified.contains(configurable)) return true

    if (configurable is InnerWithModifiableParent) {
      for (parent in configurable.getModifiableParents()) {
        if (modified.contains(parent)) return true

        for (modified in modified) {
          if (modified is ConfigurableWrapper) {
            val unwrapped = modified.rawConfigurable
            if (unwrapped != null && unwrapped == parent) {
              return true
            }
          }
        }
      }
    }

    return false
  }

  fun getParentConfigurable(configurable: Configurable?): Configurable? = configurableToParentMap.get(configurable)

  fun registerKid(parent: Configurable, kid: Configurable) {
    configurableToParentMap.put(kid, parent)
    parentToChildrenMap.putValue(parent, kid)
  }

  fun getChildren(parent: Configurable): Collection<Configurable> = parentToChildrenMap.get(parent)

  internal interface ColleagueAction {
    fun process(colleague: OptionsEditorColleague): Promise<*>
  }

  fun getModified(): Set<Configurable> = Collections.unmodifiableSet(modified)

  fun addColleague(colleague: OptionsEditorColleague) {
    colleagues.add(colleague)
  }

  fun reload() {
    this.currentConfigurable = null
    errors = emptyMap()
    configurableToParentMap.clear()
    parentToChildrenMap.clear()
  }
}