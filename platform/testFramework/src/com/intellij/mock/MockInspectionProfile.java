// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.mock;

import com.intellij.codeInsight.daemon.HighlightDisplayKey;
import com.intellij.codeInspection.ex.InspectionProfileImpl;
import com.intellij.codeInspection.ex.InspectionToolWrapper;
import com.intellij.psi.PsiElement;
import com.intellij.util.containers.ContainerUtil;
import it.unimi.dsi.fastutil.objects.ObjectOpenHashSet;
import org.jetbrains.annotations.NotNull;

import java.util.Collections;
import java.util.List;
import java.util.Set;

public final class MockInspectionProfile extends InspectionProfileImpl {
  private List<InspectionToolWrapper<?, ?>> myInspectionTools = Collections.emptyList();
  private final Set<InspectionToolWrapper<?, ?>> myDisabledTools = new ObjectOpenHashSet<>();

  public MockInspectionProfile() {
    super("a");
  }

  public void setEnabled(@NotNull InspectionToolWrapper<?, ?> tool, boolean enabled) {
    if (enabled) {
      myDisabledTools.remove(tool);
    }
    else {
      myDisabledTools.add(tool);
    }
  }

  @Override
  public boolean isToolEnabled(final HighlightDisplayKey key, PsiElement element) {
    InspectionToolWrapper<?, ?> entry = ContainerUtil.find(myInspectionTools, inspectionProfileEntry -> {
      return key.equals(HighlightDisplayKey.find(inspectionProfileEntry.getShortName()));
    });
    assert entry != null;
    return !myDisabledTools.contains(entry);
  }

  public void setInspectionTools(@NotNull List<InspectionToolWrapper<?, ?>> entries) {
    myInspectionTools = entries;
  }

  @Override
  public @NotNull List<InspectionToolWrapper<?, ?>> getInspectionTools(PsiElement element) {
    return myInspectionTools;
  }
}
