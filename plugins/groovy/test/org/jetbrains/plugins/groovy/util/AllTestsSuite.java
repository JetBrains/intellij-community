/*
 * Copyright 2000-2016 JetBrains s.r.o.
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
package org.jetbrains.plugins.groovy.util;

import com.intellij.util.ArrayUtil;
import org.junit.runners.Suite;
import org.junit.runners.model.InitializationError;
import org.junit.runners.model.RunnerBuilder;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.List;

/**
 * Runner that allows both JUnit 3 and JUnit 4 tests in one suite, this is the main difference from the {@link org.junit.runners.AllTests}.
 */
public class AllTestsSuite extends Suite {

  public AllTestsSuite(Class<?> klass, RunnerBuilder builder) throws InitializationError {
    super(builder, klass, getSuiteClasses(klass));
  }

  private static Class<?>[] getSuiteClasses(Class<?> klass) throws InitializationError {
    try {
      Method method = klass.getMethod("suite");
      //noinspection unchecked
      return ((List<Class<?>>)method.invoke(null)).toArray(ArrayUtil.EMPTY_CLASS_ARRAY);
    }
    catch (NoSuchMethodException | InvocationTargetException | IllegalAccessException e) {
      throw new InitializationError(e);
    }
  }
}
