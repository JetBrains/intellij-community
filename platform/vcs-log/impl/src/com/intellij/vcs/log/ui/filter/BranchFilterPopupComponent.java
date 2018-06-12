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

import com.intellij.icons.AllIcons;
import com.intellij.ide.DataManager;
import com.intellij.openapi.actionSystem.ActionGroup;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.DefaultActionGroup;
import com.intellij.openapi.ui.popup.ListPopup;
import com.intellij.openapi.ui.popup.ListPopupStep;
import com.intellij.openapi.ui.popup.PopupStep;
import com.intellij.openapi.vcs.ui.PopupListElementRendererWithIcon;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.ui.LayeredIcon;
import com.intellij.ui.popup.WizardPopup;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.ui.EmptyIcon;
import com.intellij.vcs.log.VcsLogBranchFilter;
import com.intellij.vcs.log.VcsLogDataPack;
import com.intellij.vcs.log.VcsRef;
import com.intellij.vcs.log.impl.MainVcsLogUiProperties;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.awt.event.InputEvent;
import java.awt.event.MouseEvent;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

public class BranchFilterPopupComponent extends MultipleValueFilterPopupComponent<VcsLogBranchFilter> {
  private final VcsLogClassicFilterUi.BranchFilterModel myBranchFilterModel;

  public BranchFilterPopupComponent(@NotNull MainVcsLogUiProperties uiProperties,
                                    @NotNull VcsLogClassicFilterUi.BranchFilterModel filterModel) {
    super("Branch", uiProperties, filterModel);
    myBranchFilterModel = filterModel;
  }

  @NotNull
  @Override
  protected String getText(@NotNull VcsLogBranchFilter filter) {
    return displayableText(myFilterModel.getFilterValues(filter));
  }

  @Nullable
  @Override
  protected String getToolTip(@NotNull VcsLogBranchFilter filter) {
    return tooltip(myFilterModel.getFilterValues(filter));
  }

  @Override
  protected boolean supportsNegativeValues() {
    return true;
  }

  @NotNull
  @Override
  protected ListPopup createPopupMenu() {
    return new MyBranchLogSpeedSearchPopup();
  }

  @Override
  protected ActionGroup createActionGroup() {
    DefaultActionGroup actionGroup = new DefaultActionGroup();

    actionGroup.add(createAllAction());
    actionGroup.add(createSelectMultipleValuesAction());

    actionGroup.add(new MyBranchPopupBuilder(myFilterModel.getDataPack(), myBranchFilterModel.getVisibleRoots(),
                                             getRecentValuesFromSettings()).build());
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
    return ContainerUtil.map(myFilterModel.getDataPack().getRefs().getBranches(), VcsRef::getName);
  }

  private class MyBranchPopupBuilder extends BranchPopupBuilder {
    protected MyBranchPopupBuilder(@NotNull VcsLogDataPack dataPack,
                                   @Nullable Collection<VirtualFile> visibleRoots,
                                   @Nullable List<List<String>> recentItems) {
      super(dataPack, visibleRoots, recentItems);
    }

    @NotNull
    @Override
    public AnAction createAction(@NotNull String name, @NotNull Collection<VcsRef> refs) {
      return new BranchFilterAction(name, refs);
    }

    @Override
    protected void createRecentAction(@NotNull DefaultActionGroup actionGroup, @NotNull List<String> recentItems) {
      actionGroup.add(new PredefinedValueAction(recentItems));
    }

    @NotNull
    @Override
    protected AnAction createCollapsedAction(@NotNull String actionName, @NotNull Collection<VcsRef> refs) {
      return new BranchFilterAction(actionName, refs);
    }

    @Override
    protected void createFavoritesAction(@NotNull DefaultActionGroup actionGroup, @NotNull List<String> favorites) {
      actionGroup.add(new PredefinedValueAction("Favorites", favorites));
    }

    private class BranchFilterAction extends PredefinedValueAction {
      @NotNull private final LayeredIcon myIcon;
      @NotNull private final LayeredIcon myHoveredIcon;
      @NotNull private final Collection<VcsRef> myReferences;
      private boolean myIsFavorite;

      public BranchFilterAction(@NotNull String value, @NotNull Collection<VcsRef> references) {
        super(value, true);
        myReferences = references;
        myIcon = new LayeredIcon(AllIcons.Vcs.Favorite, EmptyIcon.ICON_16);
        myHoveredIcon = new LayeredIcon(AllIcons.Vcs.FavoriteOnHover, AllIcons.Vcs.NotFavoriteOnHover);
        getTemplatePresentation().setIcon(myIcon);
        getTemplatePresentation().setSelectedIcon(myHoveredIcon);

        myIsFavorite = isFavorite();
        updateIcons();
      }

      private boolean isFavorite() {
        for (VcsRef ref : myReferences) {
          if (myDataPack.getLogProviders().get(ref.getRoot()).getReferenceManager().isFavorite(ref)) {
            return true;
          }
        }
        return false;
      }

      private void updateIcons() {
        myIcon.setLayerEnabled(0, myIsFavorite);
        myHoveredIcon.setLayerEnabled(0, myIsFavorite);

        myIcon.setLayerEnabled(1, !myIsFavorite);
        myHoveredIcon.setLayerEnabled(1, !myIsFavorite);
      }

      public void setFavorite(boolean favorite) {
        for (VcsRef ref : myReferences) {
          myDataPack.getLogProviders().get(ref.getRoot()).getReferenceManager().setFavorite(ref, favorite);
        }
        myIsFavorite = isFavorite();
        updateIcons();
      }

      public void toggle() {
        setFavorite(!myIsFavorite);
      }
    }
  }

  private class MyBranchLogSpeedSearchPopup extends BranchLogSpeedSearchPopup {
    private PopupListElementRendererWithIcon myListElementRenderer;

    public MyBranchLogSpeedSearchPopup() {
      super(BranchFilterPopupComponent.this.createActionGroup(), DataManager.getInstance().getDataContext(BranchFilterPopupComponent.this));
    }

    protected MyBranchLogSpeedSearchPopup(@Nullable WizardPopup parent,
                                          @NotNull ListPopupStep step,
                                          @Nullable Object value) {
      super(parent, step, DataManager.getInstance().getDataContext(BranchFilterPopupComponent.this), value);
    }

    @Override
    public void handleSelect(boolean handleFinalChoices, InputEvent e) {
      MyBranchPopupBuilder.BranchFilterAction branchAction =
        getSpecificAction(getList().getSelectedValue(), MyBranchPopupBuilder.BranchFilterAction.class);
      if (branchAction != null && e instanceof MouseEvent && myListElementRenderer.isIconAt(((MouseEvent)e).getPoint())) {
        branchAction.toggle();
        getList().repaint();
      }
      else {
        super.handleSelect(handleFinalChoices, e);
      }
    }

    @Override
    protected PopupListElementRendererWithIcon getListElementRenderer() {
      if (myListElementRenderer == null) {
        myListElementRenderer = new PopupListElementRendererWithIcon(this);
      }
      return myListElementRenderer;
    }

    @Override
    protected WizardPopup createPopup(WizardPopup parent, PopupStep step, Object parentValue) {
      if (step instanceof ListPopupStep) {
        return new MyBranchLogSpeedSearchPopup(parent, (ListPopupStep)step, parentValue);
      }
      return super.createPopup(parent, step, parentValue);
    }
  }
}
