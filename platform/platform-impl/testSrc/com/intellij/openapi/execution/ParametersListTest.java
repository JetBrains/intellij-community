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
package com.intellij.openapi.execution;

import com.intellij.execution.configurations.ParametersList;
import com.intellij.util.Assertion;
import com.intellij.util.ArrayUtil;
import junit.framework.TestCase;

/**
 * @author dyoma
 */
public class ParametersListTest extends TestCase {
  private final Assertion CHECK = new Assertion();

  public void testAddParametersString() {
    checkTokenizer("a b c", new String[]{"a", "b", "c"});
    checkTokenizer("a \"b\"", new String[]{"a", "b"});
    checkTokenizer("a \"b\\\"", new String[]{"a", "b\\\""});
    checkTokenizer("a \"\"", new String[]{"a", "\"\""}); // Bug #12169
    checkTokenizer("a \"x\"", new String[]{"a", "x"});
    checkTokenizer("a \"\" b", new String[]{"a", "\"\"", "b"});
  }

  private void checkTokenizer(String parmsString, String[] expected) {
    ParametersList params = new ParametersList();
    params.addParametersString(parmsString);
    String[] strings = ArrayUtil.toStringArray(params.getList());
    CHECK.compareAll(expected, strings);
  }
}
