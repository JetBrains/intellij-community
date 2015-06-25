/*
 * Copyright 2000-2013 JetBrains s.r.o.
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
package com.intellij.usages.impl.rules;

import com.intellij.icons.AllIcons;
import com.intellij.openapi.actionSystem.DataKey;
import com.intellij.openapi.actionSystem.DataSink;
import com.intellij.openapi.actionSystem.LangDataKeys;
import com.intellij.openapi.actionSystem.TypeSafeDataProvider;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleType;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.roots.OrderEntry;
import com.intellij.openapi.vcs.FileStatus;
import com.intellij.usageView.UsageViewBundle;
import com.intellij.usages.Usage;
import com.intellij.usages.UsageGroup;
import com.intellij.usages.UsageView;
import com.intellij.usages.rules.UsageGroupingRule;
import com.intellij.usages.rules.UsageInLibrary;
import com.intellij.usages.rules.UsageInModule;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;

/**
 * @author max
 */
public class ModuleGroupingRule implements UsageGroupingRule, DumbAware {
  @Override
  public UsageGroup groupUsage(@NotNull Usage usage) {
    if (usage instanceof UsageInModule) {
      UsageInModule usageInModule = (UsageInModule)usage;
      Module module = usageInModule.getModule();
      if (module != null) return new ModuleUsageGroup(module);
    }

    if (usage instanceof UsageInLibrary) {
      UsageInLibrary usageInLibrary = (UsageInLibrary)usage;
      OrderEntry entry = usageInLibrary.getLibraryEntry();
      if (entry != null) return new LibraryUsageGroup(entry);
    }

    return null;
  }

  private static class LibraryUsageGroup implements UsageGroup {

    private final OrderEntry myEntry;

    @Override
    public void update() {
    }

    public LibraryUsageGroup(@NotNull OrderEntry entry) {
      myEntry = entry;
    }

    @Override
    public Icon getIcon(boolean isOpen) {
      return AllIcons.Nodes.PpLibFolder;
    }

    @Override
    @NotNull
    public String getText(UsageView view) {
      return myEntry.getPresentableName();
    }

    @Override
    public FileStatus getFileStatus() {
      return null;
    }

    @Override
    public boolean isValid() {
      return true;
    }

    @Override
    public int compareTo(@NotNull UsageGroup usageGroup) {
      if (usageGroup instanceof ModuleUsageGroup) return 1;
      return getText(null).compareToIgnoreCase(usageGroup.getText(null));
    }

    @Override
    public void navigate(boolean requestFocus) {
    }

    @Override
    public boolean canNavigate() {
      return false;
    }

    @Override
    public boolean canNavigateToSource() {
      return canNavigate();
    }

    public boolean equals(Object o) {
      if (this == o) return true;
      if (!(o instanceof LibraryUsageGroup)) return false;

      return myEntry.equals(((LibraryUsageGroup)o).myEntry);
    }

    public int hashCode() {
      return myEntry.hashCode();
    }
  }

  private static class ModuleUsageGroup implements UsageGroup, TypeSafeDataProvider {
    private final Module myModule;

    public ModuleUsageGroup(@NotNull Module module) {
      myModule = module;
    }

    @Override
    public void update() {
    }

    public boolean equals(Object o) {
      if (this == o) return true;
      if (!(o instanceof ModuleUsageGroup)) return false;

      final ModuleUsageGroup moduleUsageGroup = (ModuleUsageGroup)o;

      return myModule.equals(moduleUsageGroup.myModule);
    }

    public int hashCode() {
      return myModule.hashCode();
    }

    @Override
    public Icon getIcon(boolean isOpen) {
      return myModule.isDisposed() ? null : ModuleType.get(myModule).getIcon();
    }

    @Override
    @NotNull
    public String getText(UsageView view) {
      return myModule.isDisposed() ? "" : myModule.getName();
    }

    @Override
    public FileStatus getFileStatus() {
      return null;
    }

    @Override
    public boolean isValid() {
      return !myModule.isDisposed();
    }

    @Override
    public void navigate(boolean focus) throws UnsupportedOperationException {
    }

    @Override
    public boolean canNavigate() {
      return false;
    }

    @Override
    public boolean canNavigateToSource() {
      return false;
    }

    @Override
    public int compareTo(@NotNull UsageGroup o) {
      if (o instanceof LibraryUsageGroup) return -1;
      return getText(null).compareToIgnoreCase(o.getText(null));
    }

    public String toString() {
      return UsageViewBundle.message("node.group.module") + getText(null);
    }

    @Override
    public void calcData(final DataKey key, final DataSink sink) {
      if (!isValid()) return;
      if (LangDataKeys.MODULE_CONTEXT == key) {
        sink.put(LangDataKeys.MODULE_CONTEXT, myModule);
      }
    }
  }
}
