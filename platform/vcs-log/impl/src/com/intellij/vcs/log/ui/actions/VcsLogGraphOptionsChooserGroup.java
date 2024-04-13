// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.vcs.log.ui.actions;

import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.project.DumbAware;
import com.intellij.ui.IconManager;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.ui.JBUI;
import com.intellij.vcs.log.VcsLogBundle;
import com.intellij.vcs.log.VcsLogDataKeys;
import com.intellij.vcs.log.VcsLogUi;
import com.intellij.vcs.log.graph.PermanentGraph;
import com.intellij.vcs.log.impl.MainVcsLogUiProperties;
import com.intellij.vcs.log.impl.VcsLogUiProperties;
import com.intellij.vcs.log.ui.VcsLogActionIds;
import com.intellij.vcs.log.ui.VcsLogInternalDataKeys;
import com.intellij.vcs.log.util.BekUtil;
import com.intellij.vcs.log.util.GraphOptionsUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.util.ArrayList;
import java.util.List;

public class VcsLogGraphOptionsChooserGroup extends DefaultActionGroup implements DumbAware {

  @Override
  public AnAction @NotNull [] getChildren(@Nullable AnActionEvent e) {
    if (e == null) return EMPTY_ARRAY;

    VcsLogUi logUI = e.getData(VcsLogDataKeys.VCS_LOG_UI);
    if (logUI == null) return EMPTY_ARRAY;
    VcsLogUiProperties properties = e.getData(VcsLogInternalDataKeys.LOG_UI_PROPERTIES);
    if (properties == null) return EMPTY_ARRAY;

    List<PermanentGraph.SortType> sortTypes = getAvailableSortTypes();

    List<AnAction> actions = new ArrayList<>();
    actions.add(Separator.create(VcsLogBundle.message("action.vcs.log.sort.type.separator")));
    actions.addAll(ContainerUtil.map(sortTypes, sortType -> {
      return new SelectOptionsAction(logUI, properties, new PermanentGraph.Options.Base(sortType));
    }));
    actions.add(Separator.create(VcsLogBundle.message("action.vcs.log.graph.options.separator")));
    if (BekUtil.isLinearBekEnabled()) {
      actions.add(new SelectNonBaseOptionsAction(logUI, properties, PermanentGraph.Options.LinearBek.INSTANCE));
    }
    actions.add(new SelectNonBaseOptionsAction(logUI, properties, PermanentGraph.Options.FirstParent.INSTANCE));

    actions.add(ActionManager.getInstance().getAction(VcsLogActionIds.BRANCH_ACTIONS_GROUP));

    return actions.toArray(EMPTY_ARRAY);
  }

  @Override
  public void update(@NotNull AnActionEvent e) {
    VcsLogUiProperties properties = e.getData(VcsLogInternalDataKeys.LOG_UI_PROPERTIES);
    boolean isEnabled = properties != null && properties.exists(MainVcsLogUiProperties.GRAPH_OPTIONS);
    e.getPresentation().setEnabled(isEnabled);

    if (isEnabled) {
      Icon icon = getTemplatePresentation().getIcon();
      if (icon != null) {
        if (!PermanentGraph.Options.Default.equals(properties.get(MainVcsLogUiProperties.GRAPH_OPTIONS))) {
          e.getPresentation().setIcon(IconManager.getInstance().withIconBadge(icon, JBUI.CurrentTheme.IconBadge.SUCCESS));
        }
        else {
          e.getPresentation().setIcon(icon);
        }
      }
    }
  }

  @Override
  public @NotNull ActionUpdateThread getActionUpdateThread() {
    return ActionUpdateThread.EDT;
  }

  private static @NotNull List<PermanentGraph.SortType> getAvailableSortTypes() {
    List<PermanentGraph.SortType> sortTypes = new ArrayList<>(PermanentGraph.SortType.getEntries());
    if (!BekUtil.isBekEnabled()) {
      sortTypes.remove(PermanentGraph.SortType.Bek);
    }
    return sortTypes;
  }

  private static class SelectOptionsAction extends ToggleAction implements DumbAware {
    protected final PermanentGraph.Options myGraphOptions;
    private final VcsLogUi myUI;
    private final VcsLogUiProperties myProperties;

    SelectOptionsAction(@NotNull VcsLogUi ui,
                        @NotNull VcsLogUiProperties properties,
                        @NotNull PermanentGraph.Options options) {
      super(() -> GraphOptionsUtil.getLocalizedName(options),
            () -> GraphOptionsUtil.getLocalizedDescription(options) + ".",
            null);
      myUI = ui;
      myProperties = properties;
      myGraphOptions = options;
    }

    @Override
    public void update(@NotNull AnActionEvent e) {
      super.update(e);
      e.getPresentation().setEnabled(!myUI.getDataPack().isEmpty() && myProperties.exists(MainVcsLogUiProperties.GRAPH_OPTIONS));
    }

    @Override
    public boolean isSelected(@NotNull AnActionEvent e) {
      return myProperties.exists(MainVcsLogUiProperties.GRAPH_OPTIONS) &&
             myProperties.get(MainVcsLogUiProperties.GRAPH_OPTIONS).equals(myGraphOptions);
    }

    @Override
    public void setSelected(@NotNull AnActionEvent e, boolean state) {
      if (myProperties.exists(MainVcsLogUiProperties.GRAPH_OPTIONS)) {
        myProperties.set(MainVcsLogUiProperties.GRAPH_OPTIONS, getOptionsToSet(state));
      }
    }

    protected @NotNull PermanentGraph.Options getOptionsToSet(boolean state) {
      return myGraphOptions;
    }

    @Override
    public @NotNull ActionUpdateThread getActionUpdateThread() {
      return ActionUpdateThread.EDT;
    }
  }

  private static class SelectNonBaseOptionsAction extends SelectOptionsAction {
    SelectNonBaseOptionsAction(@NotNull VcsLogUi ui,
                               @NotNull VcsLogUiProperties properties,
                               @NotNull PermanentGraph.Options options) {
      super(ui, properties, options);
    }

    @Override
    protected @NotNull PermanentGraph.Options getOptionsToSet(boolean state) {
      return state ? myGraphOptions : PermanentGraph.Options.Default;
    }
  }
}
