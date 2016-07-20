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
package com.siyeh.ig.migration;

import com.intellij.codeHighlighting.HighlightDisplayLevel;
import com.intellij.codeInsight.daemon.HighlightDisplayKey;
import com.intellij.codeInspection.LocalInspectionTool;
import com.intellij.codeInspection.ex.InspectionProfileImpl;
import com.intellij.profile.codeInspection.InspectionProjectProfileManager;
import com.siyeh.ig.LightInspectionTestCase;
import org.jetbrains.annotations.Nullable;

/**
 * @author Bas Leijdekkers
 */
public class EqualsReplaceableByObjectsCallInspectionTest extends LightInspectionTestCase {
  private EqualsReplaceableByObjectsCallInspection myInspection = new EqualsReplaceableByObjectsCallInspection();

  @Override
  public void setUp() throws Exception {
    super.setUp();
    final InspectionProfileImpl profile = (InspectionProfileImpl)InspectionProjectProfileManager.getInstance(getProject()).getInspectionProfile();
    profile.setErrorLevel(HighlightDisplayKey.find("EqualsReplaceableByObjectsCall"), HighlightDisplayLevel.WARNING, getProject());
  }

  public void testEqualsReplaceableByObjectsCall() {
    doTest();
  }

  public void testEqualsReplaceableByObjectsCallCheckNull() {
    try {
      myInspection.checkNotNull = true;
      doTest();
    } finally {
      myInspection.checkNotNull = false;
    }
  }

  @Nullable
  @Override
  protected LocalInspectionTool getInspection() {
    return myInspection;
  }
}