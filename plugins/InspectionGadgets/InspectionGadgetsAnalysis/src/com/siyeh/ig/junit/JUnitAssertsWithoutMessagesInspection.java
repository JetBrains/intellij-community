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
package com.siyeh.ig.junit;

import com.siyeh.InspectionGadgetsBundle;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

import java.util.HashMap;
import java.util.Map;

public class JUnitAssertsWithoutMessagesInspection extends AssertsWithoutMessagesInspection {
  @NonNls private static final Map<String, Integer> s_assertMethods = new HashMap<>(8);

  static {
    s_assertMethods.put("assertArrayEquals", 2);
    s_assertMethods.put("assertEquals", 2);
    s_assertMethods.put("assertFalse", 1);
    s_assertMethods.put("assertNotNull", 1);
    s_assertMethods.put("assertNotSame", 2);
    s_assertMethods.put("assertNull", 1);
    s_assertMethods.put("assertSame", 2);
    s_assertMethods.put("assertThat", 2);
    s_assertMethods.put("assertTrue", 1);
    s_assertMethods.put("fail", 0);
  }

  @Override
  protected Map<String, Integer> getAssertMethods() {
    return s_assertMethods;
  }

  @Override
  protected boolean checkTestNG() {
    return false;
  }

  @Override
  @NotNull
  public String getDisplayName() {
    return InspectionGadgetsBundle.message("asserts.without.messages.display.name");
  }

  @Override
  @NotNull
  public String getID() {
    return "MessageMissingOnJUnitAssertion";
  }

  @Override
  @NotNull
  protected String buildErrorString(Object... infos) {
    return InspectionGadgetsBundle.message("asserts.without.messages.problem.descriptor");
  }

}
