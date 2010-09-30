/*
 * Copyright 2000-2009 JetBrains s.r.o.
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

package com.intellij.testFramework.fixtures.impl;

import com.intellij.testFramework.builders.ModuleFixtureBuilder;
import com.intellij.testFramework.fixtures.IdeaProjectTestFixture;
import com.intellij.testFramework.fixtures.TestFixtureBuilder;

/**
 * @author mike
*/
class LightTestFixtureBuilderImpl<F extends IdeaProjectTestFixture> implements TestFixtureBuilder<F> {

  private final F myFixture;

  public LightTestFixtureBuilderImpl(F fixture) {
    myFixture = fixture;
  }

  @Override
  public F getFixture() {
    return myFixture;
  }

  @Override
  public <M extends ModuleFixtureBuilder> M addModule(final Class<M> builderClass) {
    throw new UnsupportedOperationException("addModule is not allowed in : " + getClass());
  }
}
