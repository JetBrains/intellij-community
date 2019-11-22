// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.vcs.log.ui.filter;

import com.intellij.openapi.actionSystem.ActionGroup;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.DefaultActionGroup;
import com.intellij.openapi.project.DumbAwareAction;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.popup.JBPopup;
import com.intellij.openapi.ui.popup.JBPopupAdapter;
import com.intellij.openapi.ui.popup.LightweightWindowEvent;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.util.ObjectUtils;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.textCompletion.TextCompletionProvider;
import com.intellij.vcs.log.impl.MainVcsLogUiProperties;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.Collections;
import java.util.List;

abstract class MultipleValueFilterPopupComponent<Filter, Model extends FilterModel<Filter>>
  extends FilterPopupComponent<Filter, Model> {

  private static final int MAX_FILTER_VALUE_LENGTH = 20;

  @NotNull protected final MainVcsLogUiProperties myUiProperties;

  MultipleValueFilterPopupComponent(@NotNull String filterName,
                                    @NotNull MainVcsLogUiProperties uiProperties,
                                    @NotNull Model filterModel) {
    super(filterName, filterModel);
    myUiProperties = uiProperties;
  }

  @NotNull
  protected List<List<String>> getRecentValuesFromSettings() {
    return myUiProperties.getRecentlyFilteredGroups(myName);
  }

  protected void rememberValuesInSettings(@NotNull Collection<String> values) {
    myUiProperties.addRecentlyFilteredGroup(myName, values);
  }

  @NotNull
  protected abstract List<String> getAllValues();

  @Nullable
  protected abstract Filter createFilter(@NotNull List<String> values);

  @NotNull
  protected abstract List<String> getFilterValues(@NotNull Filter filter);

  @NotNull
  protected ActionGroup createRecentItemsActionGroup() {
    DefaultActionGroup group = new DefaultActionGroup();
    List<List<String>> recentlyFiltered = getRecentValuesFromSettings();
    if (!recentlyFiltered.isEmpty()) {
      group.addSeparator("Recent");
      for (List<String> recentGroup : recentlyFiltered) {
        if (!recentGroup.isEmpty()) {
          group.add(new PredefinedValueAction(recentGroup));
        }
      }
      group.addSeparator();
    }
    return group;
  }

  @NotNull
  @Override
  protected String getText(@NotNull Filter filter) {
    return displayableText(getFilterValues(filter), MAX_FILTER_VALUE_LENGTH);
  }

  @Nullable
  @Override
  protected String getToolTip(@NotNull Filter filter) {
    return tooltip(getFilterValues(filter));
  }

  @NotNull
  static String displayableText(@NotNull Collection<String> values, int maxLength) {
    String text;
    if (values.size() == 1) {
      text = ObjectUtils.notNull(ContainerUtil.getFirstItem(values));
    }
    else {
      text = StringUtil.join(values, "|");
    }
    return StringUtil.shortenTextWithEllipsis(text, maxLength, 0, true);
  }

  @NotNull
  static String tooltip(@NotNull Collection<String> values) {
    return StringUtil.join(values, ", ");
  }

  @NotNull
  protected AnAction createSelectMultipleValuesAction() {
    return new SelectMultipleValuesAction();
  }

  /**
   * By default, the completion prefix is calculated based on the item separators.
   * If a filter popup supports some special syntax, it can redefine this method which will be provided to
   * {@link TextCompletionProvider#getPrefix}.
   */
  @Nullable
  protected MultilinePopupBuilder.CompletionPrefixProvider getCompletionPrefixProvider() {
    return null;
  }

  @NotNull
  private static String getActionName(@NotNull List<String> values) {
    if (values.size() == 1) return ObjectUtils.notNull(ContainerUtil.getFirstItem(values));
    return displayableText(values, 2 * MAX_FILTER_VALUE_LENGTH);
  }

  protected class PredefinedValueAction extends DumbAwareAction {
    @NotNull protected final List<String> myValues;

    private final boolean myAddToRecent;

    public PredefinedValueAction(@NotNull String value) {
      this(Collections.singletonList(value));
    }

    public PredefinedValueAction(@NotNull List<String> values) {
      this(getActionName(values), values, true);
    }

    public PredefinedValueAction(@NotNull String name, @NotNull List<String> values, boolean addToRecent) {
      super(null, tooltip(values), null);
      getTemplatePresentation().setText(name, false);
      myValues = values;
      myAddToRecent = addToRecent;
    }

    @Override
    public void actionPerformed(@NotNull AnActionEvent e) {
      myFilterModel.setFilter(createFilter(myValues));
      if (myAddToRecent) rememberValuesInSettings(myValues);
    }
  }

  private class SelectMultipleValuesAction extends DumbAwareAction {

    @NotNull private final Collection<String> myVariants;

    SelectMultipleValuesAction() {
      super("Select...");
      myVariants = getAllValues();
    }

    @Override
    public void actionPerformed(@NotNull AnActionEvent e) {
      Project project = e.getProject();
      if (project == null) {
        return;
      }

      Filter filter = myFilterModel.getFilter();
      List<String> values = filter == null ? Collections.emptyList() : getFilterValues(filter);
      final MultilinePopupBuilder popupBuilder = new MultilinePopupBuilder(project, myVariants,
                                                                           getPopupText(values),
                                                                           getCompletionPrefixProvider());
      JBPopup popup = popupBuilder.createPopup();
      popup.addListener(new JBPopupAdapter() {
        @Override
        public void onClosed(@NotNull LightweightWindowEvent event) {
          if (event.isOk()) {
            List<String> selectedValues = popupBuilder.getSelectedValues();
            if (selectedValues.isEmpty()) {
              myFilterModel.setFilter(null);
            }
            else {
              myFilterModel.setFilter(createFilter(selectedValues));
              rememberValuesInSettings(selectedValues);
            }
          }
        }
      });
      popup.showUnderneathOf(MultipleValueFilterPopupComponent.this);
    }

    @NotNull
    private String getPopupText(@Nullable Collection<String> selectedValues) {
      return selectedValues == null || selectedValues.isEmpty() ? "" : StringUtil.join(selectedValues, "\n");
    }
  }
}
