/*
 * Copyright 2000-2017 JetBrains s.r.o.
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
import com.siyeh.ig.testFrameworks.AssertHint;
import com.siyeh.ig.testFrameworks.AssertsWithoutMessagesInspection;
import org.jetbrains.annotations.NotNull;

import java.util.Map;

public class JUnitAssertsWithoutMessagesInspection extends AssertsWithoutMessagesInspection {

  @NotNull
  @Override
  public String getShortName() {
    return "AssertsWithoutMessages";
  }

  @Override
  protected Map<String, Integer> getAssertMethods() {
    return AssertHint.JUnitCommonAssertNames.ASSERT_METHOD_2_PARAMETER_COUNT;
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
