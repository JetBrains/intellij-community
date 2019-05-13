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

package com.intellij.testFramework.fixtures;

import com.intellij.testFramework.builders.ModuleFixtureBuilder;
import org.jetbrains.annotations.NotNull;

/**
 * @author mike
 */
public interface TestFixtureBuilder<T extends IdeaTestFixture> {
  @NotNull
  T getFixture();

  <M extends ModuleFixtureBuilder> M addModule(Class<M> builderClass);
}
