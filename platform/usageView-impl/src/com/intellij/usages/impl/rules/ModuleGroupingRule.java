// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.usages.impl.rules;

import com.intellij.icons.AllIcons;
import com.intellij.navigation.ItemPresentation;
import com.intellij.openapi.actionSystem.DataProvider;
import com.intellij.openapi.actionSystem.LangDataKeys;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleGrouper;
import com.intellij.openapi.module.ModuleType;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.OrderEntry;
import com.intellij.openapi.roots.SyntheticLibrary;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.usageView.UsageViewBundle;
import com.intellij.usages.Usage;
import com.intellij.usages.UsageGroup;
import com.intellij.usages.UsageTarget;
import com.intellij.usages.rules.UsageGroupingRuleEx;
import com.intellij.usages.rules.UsageInLibrary;
import com.intellij.usages.rules.UsageInModule;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

class ModuleGroupingRule implements UsageGroupingRuleEx, DumbAware {
  private final ModuleGrouper myGrouper;
  private final boolean myFlattenModules;

  ModuleGroupingRule(@NotNull Project project, boolean flattenModules) {
    myGrouper = ModuleGrouper.instanceFor(project);
    myFlattenModules = flattenModules;
  }

  @NotNull
  @Override
  public List<UsageGroup> getParentGroupsFor(@NotNull Usage usage, UsageTarget @NotNull [] targets) {
    if (usage instanceof UsageInModule usageInModule) {
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

    if (usage instanceof UsageInLibrary usageInLibrary) {
      OrderEntry entry = usageInLibrary.getLibraryEntry();
      if (entry != null) return Collections.singletonList(new LibraryUsageGroup(entry));

      for (SyntheticLibrary syntheticLibrary : usageInLibrary.getSyntheticLibraries()) {
        if (syntheticLibrary instanceof ItemPresentation) {
          return Collections.singletonList(new SyntheticLibraryUsageGroup((ItemPresentation)syntheticLibrary));
        }
      }
    }

    return Collections.emptyList();
  }

  @Override
  public int getRank() {
    return UsageGroupingRulesDefaultRanks.MODULE.getAbsoluteRank();
  }

  @Override
  public @Nullable String getGroupingActionId() {
    return "UsageGrouping.Module";
  }

  private static class LibraryUsageGroup extends UsageGroupBase {

    private final OrderEntry myEntry;

    LibraryUsageGroup(@NotNull OrderEntry entry) {
      super(2);
      myEntry = entry;
    }

    @Override
    public Icon getIcon() {
      return AllIcons.Nodes.PpLibFolder;
    }

    @Override
    @NotNull
    public String getPresentableGroupText() {
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

  private static class SyntheticLibraryUsageGroup extends UsageGroupBase {
    @NotNull private final ItemPresentation myItemPresentation;

    SyntheticLibraryUsageGroup(@NotNull ItemPresentation itemPresentation) {
      super(2);
      myItemPresentation = itemPresentation;
    }

    @Override
    public Icon getIcon() {
      return myItemPresentation.getIcon(false);
    }

    @Override
    @NotNull
    public String getPresentableGroupText() {
      return StringUtil.notNullize(myItemPresentation.getPresentableText(), UsageViewBundle.message("list.item.library"));
    }

    public boolean equals(Object o) {
      if (this == o) return true;
      return o instanceof SyntheticLibraryUsageGroup && myItemPresentation.equals(((SyntheticLibraryUsageGroup)o).myItemPresentation);
    }

    public int hashCode() {
      return myItemPresentation.hashCode();
    }
  }

  private static class ModuleUsageGroup extends UsageGroupBase implements DataProvider {
    private final Module myModule;
    private final ModuleGrouper myGrouper;

    ModuleUsageGroup(@NotNull Module module, @Nullable ModuleGrouper grouper) {
      super(1);
      myModule = module;
      myGrouper = grouper;
    }

    public boolean equals(Object o) {
      if (this == o) return true;
      if (!(o instanceof ModuleUsageGroup moduleUsageGroup)) return false;

      return myModule.equals(moduleUsageGroup.myModule);
    }

    public int hashCode() {
      return myModule.hashCode();
    }

    @Override
    public Icon getIcon() {
      return myModule.isDisposed() ? null : ModuleType.get(myModule).getIcon();
    }

    @Override
    @NotNull
    public String getPresentableGroupText() {
      return myModule.isDisposed() ? "" : myGrouper != null ? myGrouper.getShortenedName(myModule) : myModule.getName();
    }

    @Override
    public boolean isValid() {
      return !myModule.isDisposed();
    }

    public String toString() {
      return UsageViewBundle.message("node.group.module", getPresentableGroupText());
    }

    @Nullable
    @Override
    public Object getData(@NotNull String dataId) {
      if (!isValid()) return null;
      if (LangDataKeys.MODULE_CONTEXT.is(dataId)) {
        return myModule;
      }
      return null;
    }
  }

  private static class ModuleGroupUsageGroup extends UsageGroupBase {
    private final List<String> myGroupPath;

    ModuleGroupUsageGroup(@NotNull List<String> groupPath) {
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
    public Icon getIcon() {
      return AllIcons.Nodes.ModuleGroup;
    }

    @Override
    @NotNull
    public String getPresentableGroupText() {
      return myGroupPath.get(myGroupPath.size()-1);
    }

    public String toString() {
      return UsageViewBundle.message("node.group.module.group", getPresentableGroupText());
    }
  }
}