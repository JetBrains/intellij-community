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
package com.intellij.mock;

import com.intellij.codeInsight.daemon.HighlightDisplayKey;
import com.intellij.codeInspection.ex.InspectionProfileImpl;
import com.intellij.codeInspection.ex.InspectionToolWrapper;
import com.intellij.psi.PsiElement;
import com.intellij.util.containers.ContainerUtil;
import gnu.trove.THashSet;
import org.jetbrains.annotations.NotNull;

import java.util.Set;

/**
 * @author peter
*/
public class MockInspectionProfile extends InspectionProfileImpl {
  private InspectionToolWrapper[] myInspectionTools = new InspectionToolWrapper[0];
  private final Set<InspectionToolWrapper> myDisabledTools = new THashSet<>();

  public MockInspectionProfile() {
    super("a");
  }

  public void setEnabled(@NotNull InspectionToolWrapper tool, boolean enabled) {
    if (enabled) {
      myDisabledTools.remove(tool);
    }
    else {
      myDisabledTools.add(tool);
    }
  }

  @Override
  public boolean isToolEnabled(final HighlightDisplayKey key, PsiElement element) {
    final InspectionToolWrapper entry = ContainerUtil.find(myInspectionTools,
                                                           inspectionProfileEntry -> key.equals(HighlightDisplayKey.find(inspectionProfileEntry.getShortName())));
    assert entry != null;
    return !myDisabledTools.contains(entry);
  }

  public void setInspectionTools(final InspectionToolWrapper... entries) {
    myInspectionTools = entries;
  }

  @Override
  @NotNull
  public InspectionToolWrapper[] getInspectionTools(PsiElement element) {
    return myInspectionTools;
  }
}
