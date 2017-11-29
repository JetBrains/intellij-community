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
package com.siyeh.ig.bugs;

import com.intellij.codeInspection.InspectionProfileEntry;
import com.intellij.codeInspection.ui.OptionAccessor;
import com.intellij.openapi.util.text.StringUtil;
import com.siyeh.ig.LightInspectionTestCase;
import org.jetbrains.annotations.Nullable;

public class ObjectToStringInspectionTest extends LightInspectionTestCase {

  public void testObjectToString() {
    doTest();
  }

  public void testObjectToString_IGNORE_TOSTRING() {
    doTest();
  }

  public void testObjectToString_IGNORE_EXCEPTION() {
    doTest();
  }

  public void testObjectToString_IGNORE_ASSERT() {
    doTest();
  }

  public void testObjectToString_IGNORE_NONNLS() {
    doTest();
  }

  @Nullable
  @Override
  protected InspectionProfileEntry getInspection() {
    ObjectToStringInspection inspection = new ObjectToStringInspection();
    String option = StringUtil.substringAfter(getName(), "_");
    if(option != null) {
      new OptionAccessor.Default(inspection).setOption(option, true);
    }
    return inspection;
  }
}