/*
 * Copyright 2000-2015 JetBrains s.r.o.
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
package com.intellij.testFramework

import com.intellij.openapi.application.invokeAndWaitIfNeed
import com.intellij.openapi.diagnostic.Logger
import com.intellij.testFramework.fixtures.IdeaProjectTestFixture
import com.intellij.testFramework.fixtures.IdeaTestFixtureFactory
import com.intellij.testFramework.fixtures.TestFixtureBuilder
import com.intellij.util.SmartList
import com.intellij.util.lang.CompoundRuntimeException
import org.junit.rules.ExternalResource
import org.junit.rules.TestRule
import org.junit.runner.Description
import org.junit.runners.model.Statement
import kotlin.properties.Delegates

public open class FixtureRule() : ExternalResource() {
  companion object {
    init {
      Logger.setFactory(javaClass<TestLoggerFactory>())
    }
  }

  protected var _projectFixture: IdeaProjectTestFixture? = null

  public val projectFixture: IdeaProjectTestFixture
    get() = _projectFixture!!

  open fun createBuilder() = IdeaTestFixtureFactory.getFixtureFactory().createLightFixtureBuilder()

  override final fun before() {
    val builder = createBuilder()
    if (_projectFixture == null) {
      _projectFixture = builder.getFixture()
    }

    UsefulTestCase.replaceIdeEventQueueSafely()
    invokeAndWaitIfNeed { projectFixture.setUp() }
  }

  override final fun after() {
    invokeAndWaitIfNeed { projectFixture.tearDown() }
  }
}

public fun FixtureRule(tuner: TestFixtureBuilder<IdeaProjectTestFixture>.() -> Unit): FixtureRule = HeavyFixtureRule(tuner)

private class HeavyFixtureRule(private val tune: TestFixtureBuilder<IdeaProjectTestFixture>.() -> Unit) : FixtureRule() {
  private var name: String by Delegates.notNull()

  override final fun apply(base: Statement, description: Description): Statement {
    name = description.getMethodName()
    return super.apply(base, description)
  }

  override final fun createBuilder(): TestFixtureBuilder<IdeaProjectTestFixture> {
    val builder = IdeaTestFixtureFactory.getFixtureFactory().createFixtureBuilder(name)
    _projectFixture = builder.getFixture()
    builder.tune()
    return builder
  }
}

public class RuleChain(vararg val rules: TestRule) : TestRule {
  override fun apply(base: Statement, description: Description): Statement {
    var statement = base
    var errors: MutableList<Throwable>? = null
    for (i in (rules.size() - 1) downTo 0) {
      try {
        statement = rules[i].apply(statement, description)
      }
      catch(e: Throwable) {
        if (errors == null) {
          errors = SmartList<Throwable>()
        }
        errors.add(e)
      }
    }

    CompoundRuntimeException.doThrow(errors)
    return statement
  }
}