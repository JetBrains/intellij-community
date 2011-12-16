/*
 * Copyright (c) 2000-2006 JetBrains s.r.o. All Rights Reserved.
 */
package com.intellij.mock;

import com.intellij.codeInsight.daemon.HighlightDisplayKey;
import com.intellij.codeInspection.InspectionProfileEntry;
import com.intellij.codeInspection.ex.InspectionProfileImpl;
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
  private InspectionProfileEntry[] myInspectionTools = new InspectionProfileEntry[0];
  private final Set<InspectionProfileEntry> myDisabledTools = new THashSet<InspectionProfileEntry>();

  public MockInspectionProfile() {
    super("a");
  }

  public void setEnabled(InspectionProfileEntry tool, boolean enabled) {
    if (enabled) {
      myDisabledTools.remove(tool);
    } else {
      myDisabledTools.add(tool);
    }
  }

  @Override
  public boolean isToolEnabled(final HighlightDisplayKey key, PsiElement element) {
    final InspectionProfileEntry entry = ContainerUtil.find(myInspectionTools, new Condition<InspectionProfileEntry>() {
      @Override
      public boolean value(final InspectionProfileEntry inspectionProfileEntry) {
        return key.equals(HighlightDisplayKey.find(inspectionProfileEntry.getShortName()));
      }
    });
    assert entry != null;
    return !myDisabledTools.contains(entry);
  }

  public void setInspectionTools(final InspectionProfileEntry... entries) {
    myInspectionTools = entries;
  }

  @Override
  @NotNull
  public InspectionProfileEntry[] getInspectionTools(PsiElement element) {
    return myInspectionTools;
  }
}
