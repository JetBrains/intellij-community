/*
 * Copyright (c) 2000-2006 JetBrains s.r.o. All Rights Reserved.
 */
package com.intellij.mock;

import com.intellij.codeInsight.daemon.HighlightDisplayKey;
import com.intellij.codeInspection.ex.InspectionProfileImpl;
import com.intellij.codeInspection.ex.InspectionToolWrapper;
import com.intellij.openapi.util.Condition;
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
  private final Set<InspectionToolWrapper> myDisabledTools = new THashSet<InspectionToolWrapper>();

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
    final InspectionToolWrapper entry = ContainerUtil.find(myInspectionTools, new Condition<InspectionToolWrapper>() {
      @Override
      public boolean value(final InspectionToolWrapper inspectionProfileEntry) {
        return key.equals(HighlightDisplayKey.find(inspectionProfileEntry.getShortName()));
      }
    });
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
