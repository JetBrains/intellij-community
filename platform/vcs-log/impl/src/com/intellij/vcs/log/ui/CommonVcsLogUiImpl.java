// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.vcs.log.ui;

import com.intellij.vcs.log.VcsLogFilterCollection;
import com.intellij.vcs.log.VcsLogHighlighter;
import com.intellij.vcs.log.data.VcsLogData;
import com.intellij.vcs.log.graph.actions.ActionController;
import com.intellij.vcs.log.impl.CommonUiProperties;
import com.intellij.vcs.log.impl.MainVcsLogUiProperties;
import com.intellij.vcs.log.impl.VcsLogUiProperties;
import com.intellij.vcs.log.ui.highlighters.VcsLogHighlighterFactory;
import com.intellij.vcs.log.ui.table.column.TableColumnWidthProperty;
import com.intellij.vcs.log.visible.VisiblePackRefresher;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

import javax.swing.JComponent;
import java.util.LinkedHashMap;

@ApiStatus.Internal
public abstract class CommonVcsLogUiImpl extends AbstractVcsLogUi {

  private final @NotNull MainVcsLogUiProperties myUiProperties;
  private final @NotNull MyVcsLogUiPropertiesListener myPropertiesListener;
  private final @NotNull LinkedHashMap<String, VcsLogHighlighter> myHighlighters = new LinkedHashMap<>();

  public CommonVcsLogUiImpl(@NotNull String id,
                            @NotNull VcsLogData logData,
                            @NotNull VcsLogColorManager manager,
                            @NotNull MainVcsLogUiProperties uiProperties,
                            @NotNull VisiblePackRefresher refresher) {
    super(id, logData, manager, refresher);
    myUiProperties = uiProperties;
    myPropertiesListener = new MyVcsLogUiPropertiesListener();
    myUiProperties.addChangeListener(myPropertiesListener, this);
  }


  @Override
  protected void onVisiblePackUpdated(boolean permGraphChanged) {
    updateDataPack(permGraphChanged);
    myPropertiesListener.onShowLongEdgesChanged();
  }

  protected abstract void updateDataPack(boolean permGraphChanged);

  private boolean isHighlighterEnabled(@NotNull String id) {
    MainVcsLogUiProperties.VcsLogHighlighterProperty property = MainVcsLogUiProperties.VcsLogHighlighterProperty.get(id);
    return myUiProperties.exists(property) && myUiProperties.get(property);
  }

  protected void applyFiltersAndUpdateUi(@NotNull VcsLogFilterCollection filters) {
    myRefresher.onFiltersChange(filters);
    JComponent toolbar = getToolbar();
    toolbar.revalidate();
    toolbar.repaint();
  }

  @Override
  public @NotNull MainVcsLogUiProperties getProperties() {
    return myUiProperties;
  }

  protected void updateHighlighters() {
    myHighlighters.forEach((s, highlighter) -> getTable().removeHighlighter(highlighter));
    myHighlighters.clear();

    for (VcsLogHighlighterFactory factory : LOG_HIGHLIGHTER_FACTORY_EP.getExtensionList()) {
      VcsLogHighlighter highlighter = factory.createHighlighter(myLogData, this);
      myHighlighters.put(factory.getId(), highlighter);
      if (isHighlighterEnabled(factory.getId())) {
        getTable().addHighlighter(highlighter);
      }
    }

    getTable().repaint();
  }

  protected abstract @NotNull JComponent getToolbar();

  private class MyVcsLogUiPropertiesListener implements VcsLogUiProperties.PropertiesChangeListener {

    @Override
    public <T> void onPropertyChanged(@NotNull VcsLogUiProperties.VcsLogUiProperty<T> property) {
      if (MainVcsLogUiProperties.SHOW_LONG_EDGES.equals(property)) {
        onShowLongEdgesChanged();
      }
      else if (MainVcsLogUiProperties.GRAPH_OPTIONS.equals(property)) {
        myRefresher.onGraphOptionsChange(myUiProperties.get(MainVcsLogUiProperties.GRAPH_OPTIONS));
      }
      else if (CommonUiProperties.COLUMN_ID_ORDER.equals(property)) {
        getTable().onColumnOrderSettingChanged();
      }
      else if (property instanceof MainVcsLogUiProperties.VcsLogHighlighterProperty) {
        VcsLogHighlighter highlighter = myHighlighters.get(((MainVcsLogUiProperties.VcsLogHighlighterProperty)property).getId());
        if ((boolean)myUiProperties.get(property)) {
          getTable().addHighlighter(highlighter);
        }
        else {
          getTable().removeHighlighter(highlighter);
        }
        getTable().repaint();
      }
      else if (property instanceof TableColumnWidthProperty) {
        getTable().forceReLayout(((TableColumnWidthProperty)property).getColumn());
      }
    }

    private void onShowLongEdgesChanged() {
      ActionController<Integer> actionController = myVisiblePack.getVisibleGraph().getActionController();
      boolean oldLongEdgesHiddenValue = actionController.areLongEdgesHidden();
      boolean newLongEdgesHiddenValue = !myUiProperties.get(MainVcsLogUiProperties.SHOW_LONG_EDGES);
      if (newLongEdgesHiddenValue != oldLongEdgesHiddenValue) {
        actionController.setLongEdgesHidden(newLongEdgesHiddenValue);
        getTable().repaint();
      }
    }
  }
}
