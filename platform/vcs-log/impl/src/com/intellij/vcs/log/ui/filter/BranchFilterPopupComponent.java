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
package com.intellij.vcs.log.ui.filter;

import com.intellij.ide.DataManager;
import com.intellij.openapi.actionSystem.ActionGroup;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.DefaultActionGroup;
import com.intellij.openapi.ui.popup.ListPopup;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.vcs.log.VcsLogBranchFilter;
import com.intellij.vcs.log.VcsLogDataPack;
import com.intellij.vcs.log.data.VcsLogBranchFilterImpl;
import com.intellij.vcs.log.data.VcsLogUiProperties;
import com.intellij.vcs.log.ui.VcsLogUiImpl;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

public class BranchFilterPopupComponent extends MultipleValueFilterPopupComponent<VcsLogBranchFilter> {
  @NotNull private final VcsLogUiImpl myUi;
  private VcsLogClassicFilterUi.BranchFilterModel myBranchFilterModel;

  public BranchFilterPopupComponent(@NotNull VcsLogUiImpl ui,
                                    @NotNull VcsLogUiProperties uiProperties,
                                    @NotNull VcsLogClassicFilterUi.BranchFilterModel filterModel) {
    super("Branch", uiProperties, filterModel);
    myUi = ui;
    myBranchFilterModel = filterModel;
  }

  @NotNull
  @Override
  protected String getText(@NotNull VcsLogBranchFilter filter) {
    return displayableText(getTextValues(filter));
  }

  @Nullable
  @Override
  protected String getToolTip(@NotNull VcsLogBranchFilter filter) {
    return tooltip(getTextValues(filter));
  }

  @NotNull
  @Override
  protected VcsLogBranchFilter createFilter(@NotNull Collection<String> values) {
    return VcsLogBranchFilterImpl
      .fromTextPresentation(values, ContainerUtil.map2Set(myUi.getDataPack().getRefs().getBranches(), vcsRef -> vcsRef.getName()));
  }

  @Override
  @NotNull
  protected Collection<String> getTextValues(@Nullable VcsLogBranchFilter filter) {
    if (filter == null) return Collections.emptySet();
    return filter.getTextPresentation();
  }

  @Override
  protected boolean supportsNegativeValues() {
    return true;
  }

  @NotNull
  @Override
  protected ListPopup createPopupMenu() {
    return new FlatSpeedSearchPopup(createActionGroup(), DataManager.getInstance().getDataContext(this));
  }

  @Override
  protected ActionGroup createActionGroup() {
    DefaultActionGroup actionGroup = new DefaultActionGroup();

    actionGroup.add(createAllAction());
    actionGroup.add(createSelectMultipleValuesAction());

    actionGroup.add(
      new MyBranchPopupBuilder(myFilterModel.getDataPack(), myBranchFilterModel.getVisibleRoots(), getRecentValuesFromSettings()).build());
    return actionGroup;
  }

  @NotNull
  @Override
  protected List<List<String>> getRecentValuesFromSettings() {
    return myUiProperties.getRecentlyFilteredBranchGroups();
  }

  @Override
  protected void rememberValuesInSettings(@NotNull Collection<String> values) {
    myUiProperties.addRecentlyFilteredBranchGroup(new ArrayList<>(values));
  }

  @NotNull
  @Override
  protected List<String> getAllValues() {
    return ContainerUtil.map(myFilterModel.getDataPack().getRefs().getBranches(), ref -> ref.getName());
  }

  private class MyBranchPopupBuilder extends BranchPopupBuilder {
    protected MyBranchPopupBuilder(@NotNull VcsLogDataPack dataPack,
                                   @Nullable Collection<VirtualFile> visibleRoots,
                                   @Nullable List<List<String>> recentItems) {
      super(dataPack, visibleRoots, recentItems);
    }

    @NotNull
    @Override
    public AnAction createAction(@NotNull String name) {
      return new PredefinedValueAction(Collections.singleton(name)) {
        @Override
        public void actionPerformed(@NotNull AnActionEvent e) {
          myFilterModel.setFilter(BranchFilterPopupComponent.this.createFilter(myValues)); // does not add to recent
        }
      };
    }

    @Override
    protected void createRecentAction(@NotNull DefaultActionGroup actionGroup, @NotNull List<String> recentItem) {
      actionGroup.add(new PredefinedValueAction(recentItem));
    }

    @NotNull
    @Override
    protected AnAction createCollapsedAction(String actionName) {
      return createPredefinedValueAction(Collections.singleton(actionName)); // adds to recent
    }
  }
}
