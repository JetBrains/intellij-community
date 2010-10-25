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

import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.module.Module;
import com.intellij.testFramework.fixtures.ModuleFixture;

/**
 * @author mike
 */
public class ModuleFixtureImpl extends BaseFixture implements ModuleFixture {

  private Module myModule;
  protected final ModuleFixtureBuilderImpl myBuilder;

  public ModuleFixtureImpl(final ModuleFixtureBuilderImpl builder) {
    myBuilder = builder;
  }

  @Override
  public Module getModule() {
    if (myModule != null) return myModule;
    new WriteCommandAction.Simple(null) {
      @Override
      protected void run() throws Throwable {
        myModule = myBuilder.buildModule();
      }
    }.execute().throwException();


    //disposeOnTearDown(myModule);
    return myModule;
  }

  @Override
  public void setUp() throws Exception {
    super.setUp();
    getModule();
  }

  @Override
  public void tearDown() throws Exception {
    myModule = null;
    super.tearDown();
  }
}
