// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.vcs.log.ui.filter;

import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.Separator;
import com.intellij.openapi.project.DumbAwareAction;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.popup.JBPopup;
import com.intellij.openapi.ui.popup.JBPopupListener;
import com.intellij.openapi.ui.popup.LightweightWindowEvent;
import com.intellij.openapi.util.NlsContexts;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.textCompletion.TextCompletionProvider;
import com.intellij.vcs.log.VcsLogBundle;
import com.intellij.vcs.log.impl.MainVcsLogUiProperties;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;
import java.util.function.Supplier;

abstract class MultipleValueFilterPopupComponent<Filter, Model extends FilterModel<Filter>>
  extends FilterPopupComponent<Filter, Model> {

  private static final int MAX_FILTER_VALUE_LENGTH = 20;

  @NotNull protected final MainVcsLogUiProperties myUiProperties;
  @NonNls @NotNull private final String myName;

  MultipleValueFilterPopupComponent(@NonNls @NotNull String filterName,
                                    @NotNull Supplier<@NlsContexts.Label @NotNull String> displayName,
                                    @NotNull MainVcsLogUiProperties uiProperties,
                                    @NotNull Model filterModel) {
    super(displayName, filterModel);
    myName = filterName;
    myUiProperties = uiProperties;
  }

  @NotNull
  protected List<List<String>> getRecentValuesFromSettings() {
    return myUiProperties.getRecentlyFilteredGroups(myName);
  }

  protected void rememberValuesInSettings(@NotNull Collection<String> values) {
    myUiProperties.addRecentlyFilteredGroup(myName, parseLocalizedValues(values));
  }

  @NotNull
  protected abstract List<String> getAllValues();

  @Nullable
  protected abstract Filter createFilter(@NotNull List<String> values);

  @NotNull
  protected abstract List<String> getFilterValues(@NotNull Filter filter);

  @NotNull
  protected List<AnAction> createRecentItemsActionGroup() {
    List<AnAction> group = new ArrayList<>();
    List<List<String>> recentlyFiltered = getRecentValuesFromSettings();
    if (!recentlyFiltered.isEmpty()) {
      group.add(Separator.create(VcsLogBundle.message("vcs.log.filter.recent")));
      for (List<String> recentGroup : recentlyFiltered) {
        if (!recentGroup.isEmpty()) {
          group.add(new PredefinedValueAction(recentGroup));
        }
      }
      group.add(Separator.getInstance());
    }
    return group;
  }

  @NotNull
  @Override
  protected String getText(@NotNull Filter filter) {
    return displayableText(getLocalizedValues(getFilterValues(filter)), MAX_FILTER_VALUE_LENGTH);
  }

  @Nls
  @Override
  protected String getToolTip(@NotNull Filter filter) {
    return getTooltip(getFilterValues(filter));
  }

  @NotNull
  @NlsContexts.Tooltip
  protected String getTooltip(@NotNull Collection<String> values) {
    return StringUtil.join(getLocalizedValues(values), ", ");
  }

  @NotNull
  protected abstract List<String> parseLocalizedValues(@NotNull Collection<String> values);

  @NotNull
  protected abstract List<@Nls String> getLocalizedValues(@NotNull Collection<String> values);

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
  protected String getActionName(@NotNull List<String> values) {
    List<String> localizedValues = getLocalizedValues(values);
    if (localizedValues.size() == 1) return Objects.requireNonNull(ContainerUtil.getFirstItem(localizedValues));
    return displayableText(localizedValues, 2 * MAX_FILTER_VALUE_LENGTH);
  }

  @NotNull
  @Nls
  static String displayableText(@NotNull Collection<@Nls String> values, int maxLength) {
    String text;
    if (values.size() == 1) {
      text = Objects.requireNonNull(ContainerUtil.getFirstItem(values));
    }
    else {
      text = StringUtil.join(values, "|");
    }
    return StringUtil.shortenTextWithEllipsis(text, maxLength, 0, true);
  }

  protected class PredefinedValueAction extends DumbAwareAction {
    @NotNull protected final List<String> myValues;

    private final boolean myAddToRecent;

    public PredefinedValueAction(@NotNull List<String> values) {
      this(values, null, true);
    }

    public PredefinedValueAction(@NotNull List<String> values,
                                 @Nullable Supplier<String> displayName,
                                 boolean addToRecent) {
      super(null, getTooltip(values), null);
      getTemplatePresentation().setText(displayName != null ? displayName : () -> getActionName(values), false);
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
      super(VcsLogBundle.messagePointer("vcs.log.filter.action.select"));
      myVariants = getAllValues();
    }

    @Override
    public void actionPerformed(@NotNull AnActionEvent e) {
      Project project = e.getProject();
      if (project == null) {
        return;
      }

      Filter filter = myFilterModel.getFilter();
      List<String> values = filter == null ? Collections.emptyList() :
                            getLocalizedValues(MultipleValueFilterPopupComponent.this.getFilterValues(filter));
      MultilinePopupBuilder popupBuilder = new MultilinePopupBuilder(project, myVariants, getPopupText(values),
                                                                     getCompletionPrefixProvider());
      JBPopup popup = popupBuilder.createPopup();
      popup.addListener(new JBPopupListener() {
        @Override
        public void onClosed(@NotNull LightweightWindowEvent event) {
          if (event.isOk()) {
            List<String> selectedValues = parseLocalizedValues(popupBuilder.getSelectedValues());
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
