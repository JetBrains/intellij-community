// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.vcs.log.ui.filter;

import com.intellij.icons.AllIcons;
import com.intellij.ide.DataManager;
import com.intellij.openapi.actionSystem.ActionGroup;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.DefaultActionGroup;
import com.intellij.openapi.ui.popup.ListPopup;
import com.intellij.openapi.ui.popup.ListPopupStep;
import com.intellij.openapi.ui.popup.PopupStep;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vcs.ui.PopupListElementRendererWithIcon;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.ui.LayeredIcon;
import com.intellij.ui.popup.WizardPopup;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.ui.EmptyIcon;
import com.intellij.vcs.log.VcsLogBundle;
import com.intellij.vcs.log.VcsLogDataPack;
import com.intellij.vcs.log.VcsRef;
import com.intellij.vcs.log.impl.MainVcsLogUiProperties;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.event.InputEvent;
import java.awt.event.MouseEvent;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.function.Supplier;

import static com.intellij.vcs.log.visible.filters.VcsLogFilterObject.fromBranchPatterns;

public final class BranchFilterPopupComponent extends MultipleValueFilterPopupComponent<BranchFilters, BranchFilterModel> {
  private final BranchFilterModel myBranchFilterModel;

  public BranchFilterPopupComponent(@NotNull MainVcsLogUiProperties uiProperties,
                                    @NotNull BranchFilterModel filterModel) {
    super("Branch", VcsLogBundle.messagePointer("vcs.log.branch.filter.label"), uiProperties, filterModel);
    myBranchFilterModel = filterModel;
  }

  @Override
  protected @NotNull List<String> getFilterValues(@NotNull BranchFilters filters) {
    return BranchFilterModel.getFilterPresentation(filters);
  }

  @Override
  protected @Nullable BranchFilters createFilter(@NotNull List<String> values) {
    return myFilterModel.createFilterFromPresentation(values);
  }

  @Override
  protected @NotNull MultilinePopupBuilder.CompletionPrefixProvider getCompletionPrefixProvider() {
    return (text, offset) -> {
      int index = 0;
      for (String s : getCompletionSeparators()) {
        int separatorIndex = text.lastIndexOf(s, offset - s.length());
        if (separatorIndex > index) {
          index = separatorIndex + s.length();
        }
      }
      String prefix = text.substring(index, offset);
      return StringUtil.trimLeading(prefix, '-');
    };
  }

  @Override
  protected @NotNull List<String> parseLocalizedValues(@NotNull Collection<String> values) {
    return new ArrayList<>(values);
  }

  @Override
  protected @NotNull List<String> getLocalizedValues(@NotNull Collection<String> values) {
    return new ArrayList<>(values);
  }

  private static @NotNull List<String> getCompletionSeparators() {
    List<String> separators = new ArrayList<>();
    for (char c : MultilinePopupBuilder.SEPARATORS) {
      separators.add(String.valueOf(c));
    }
    separators.add("..");
    return separators;
  }

  @Override
  protected @NotNull ListPopup createPopupMenu() {
    return new MyBranchLogSpeedSearchPopup();
  }

  @Override
  protected @NotNull ActionGroup createActionGroup() {
    DefaultActionGroup actionGroup = new DefaultActionGroup();

    actionGroup.add(createSelectMultipleValuesAction());

    VcsLogDataPack logData = myFilterModel.getDataPack();

    List<List<String>> branchFilters = processRecentBranchFilters(
      ContainerUtil.map2Set(logData.getRefs().getBranches(), VcsRef::getName),
      getRecentValuesFromSettings());

    actionGroup.add(new MyBranchPopupBuilder(logData, myBranchFilterModel.getVisibleRoots(), branchFilters).build());

    return actionGroup;
  }

  @Override
  protected @NotNull List<String> getAllValues() {
    Collection<VcsRef> branches = myFilterModel.getDataPack().getRefs().getBranches();
    if (myBranchFilterModel.getVisibleRoots() != null) {
      branches = ContainerUtil.filter(branches, branch -> myBranchFilterModel.getVisibleRoots().contains(branch.getRoot()));
    }
    return ContainerUtil.map(branches, VcsRef::getName);
  }

  private static @NotNull List<List<String>> processRecentBranchFilters(@NotNull Set<String> availableBranches,
                                                                        @NotNull List<List<String>> recentBranchFilters) {
    if (availableBranches.isEmpty()) {
      return recentBranchFilters;
    }

    return ContainerUtil.filter(recentBranchFilters, recentFilter ->
      !fromBranchPatterns(recentFilter, availableBranches, true).isEmpty());
  }

  private class MyBranchPopupBuilder extends BranchPopupBuilder {
    protected MyBranchPopupBuilder(@NotNull VcsLogDataPack dataPack,
                                   @Nullable Collection<? extends VirtualFile> visibleRoots,
                                   @Nullable List<? extends List<String>> recentItems) {
      super(dataPack, visibleRoots, recentItems);
    }

    @Override
    public @NotNull AnAction createAction(@NotNull String name, @NotNull Collection<? extends VcsRef> refs) {
      return new BranchFilterAction(() -> name, refs);
    }

    @Override
    protected void createRecentAction(@NotNull List<AnAction> actionGroup, @NotNull List<String> recentItems) {
      actionGroup.add(new PredefinedValueAction(recentItems));
    }

    @Override
    protected @NotNull AnAction createCollapsedAction(@NotNull String actionName, @NotNull Collection<? extends VcsRef> refs) {
      return new BranchFilterAction(() -> actionName, refs);
    }

    @Override
    protected void createFavoritesAction(@NotNull List<AnAction> actionGroup, @NotNull List<String> favorites) {
      actionGroup.add(new PredefinedValueAction(favorites, VcsLogBundle.messagePointer("vcs.log.branch.filter.favorites"), false));
    }

    private final class BranchFilterAction extends PredefinedValueAction {
      private final @NotNull LayeredIcon myIcon;
      private final @NotNull LayeredIcon myHoveredIcon;
      private final @NotNull Collection<? extends VcsRef> myReferences;
      private boolean myIsFavorite;

      BranchFilterAction(@NotNull Supplier<String> displayName, @NotNull Collection<? extends VcsRef> references) {
        super(new ArrayList<>(ContainerUtil.map2LinkedSet(references, ref -> ref.getName())), displayName, true);
        myReferences = references;
        myIcon = LayeredIcon.layeredIcon(new Icon[]{AllIcons.Nodes.Favorite, EmptyIcon.ICON_16});
        myHoveredIcon = LayeredIcon.layeredIcon(new Icon[]{AllIcons.Nodes.Favorite, AllIcons.Nodes.NotFavoriteOnHover});
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

    MyBranchLogSpeedSearchPopup() {
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
