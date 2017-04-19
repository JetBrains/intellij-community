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
import com.intellij.openapi.module.ModuleGrouper;
import com.intellij.openapi.module.ModuleType;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.OrderEntry;
import com.intellij.usageView.UsageViewBundle;
import com.intellij.usages.Usage;
import com.intellij.usages.UsageGroup;
import com.intellij.usages.UsageTarget;
import com.intellij.usages.UsageView;
import com.intellij.usages.rules.UsageGroupingRule;
import com.intellij.usages.rules.UsageInLibrary;
import com.intellij.usages.rules.UsageInModule;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * @author max
 */
public class ModuleGroupingRule implements UsageGroupingRule, DumbAware {
  private final ModuleGrouper myGrouper;
  private final boolean myFlattenModules;

  public ModuleGroupingRule(Project project, boolean flattenModules) {
    myGrouper = ModuleGrouper.instanceFor(project);
    myFlattenModules = flattenModules;
  }

  @NotNull
  @Override
  public List<UsageGroup> getParentGroupsFor(@NotNull Usage usage, @NotNull UsageTarget[] targets) {
    if (usage instanceof UsageInModule) {
      UsageInModule usageInModule = (UsageInModule)usage;
      Module module = usageInModule.getModule();
      if (module != null) {
        if (myFlattenModules) {
          return Collections.singletonList(new ModuleUsageGroup(module, null));
        }
        else {
          List<String> groupPath = myGrouper.getGroupPath(module);
          List<UsageGroup> parentGroups = new ArrayList<>(groupPath.size() + 1);
          for (int i = 1; i <= groupPath.size(); i++) {
            parentGroups.add(new ModuleGroupUsageGroup(groupPath.subList(0, i)));
          }
          parentGroups.add(new ModuleUsageGroup(module, myGrouper));
          return parentGroups;
        }
      }
    }

    if (usage instanceof UsageInLibrary) {
      UsageInLibrary usageInLibrary = (UsageInLibrary)usage;
      OrderEntry entry = usageInLibrary.getLibraryEntry();
      if (entry != null) return Collections.singletonList(new LibraryUsageGroup(entry));
    }

    return Collections.emptyList();
  }

  private static class LibraryUsageGroup extends UsageGroupBase {

    private final OrderEntry myEntry;

    @Override
    public void update() {
    }

    public LibraryUsageGroup(@NotNull OrderEntry entry) {
      super(2);
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

    public boolean equals(Object o) {
      if (this == o) return true;
      return o instanceof LibraryUsageGroup && myEntry.equals(((LibraryUsageGroup)o).myEntry);
    }

    public int hashCode() {
      return myEntry.hashCode();
    }
  }

  private static class ModuleUsageGroup extends UsageGroupBase implements TypeSafeDataProvider {
    private final Module myModule;
    private final ModuleGrouper myGrouper;

    public ModuleUsageGroup(@NotNull Module module, @Nullable ModuleGrouper grouper) {
      super(1);
      myModule = module;
      myGrouper = grouper;
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
      return myModule.isDisposed() ? "" : myGrouper != null ? myGrouper.getShortenedName(myModule) : myModule.getName();
    }

    @Override
    public boolean isValid() {
      return !myModule.isDisposed();
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

  private static class ModuleGroupUsageGroup extends UsageGroupBase {
    private final List<String> myGroupPath;

    public ModuleGroupUsageGroup(@NotNull List<String> groupPath) {
      super(0);
      myGroupPath = groupPath;
    }

    public boolean equals(Object o) {
      if (this == o) return true;
      return o instanceof ModuleGroupUsageGroup && myGroupPath.equals(((ModuleGroupUsageGroup)o).myGroupPath);
    }

    public int hashCode() {
      return myGroupPath.hashCode();
    }

    @Override
    public Icon getIcon(boolean isOpen) {
      return AllIcons.Nodes.ModuleGroup;
    }

    @Override
    @NotNull
    public String getText(UsageView view) {
      return myGroupPath.get(myGroupPath.size()-1);
    }

    public String toString() {
      return UsageViewBundle.message("node.group.module.group") + getText(null);
    }
  }
}
