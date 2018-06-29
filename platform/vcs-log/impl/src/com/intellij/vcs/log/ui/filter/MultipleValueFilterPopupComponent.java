/*
 * Copyright 2000-2014 JetBrains s.r.o.
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
import com.intellij.vcs.log.VcsLogFilter;
import com.intellij.vcs.log.impl.MainVcsLogUiProperties;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.Collections;
import java.util.List;

abstract class MultipleValueFilterPopupComponent<Filter extends VcsLogFilter> extends FilterPopupComponent<Filter> {

  private static final int MAX_FILTER_VALUE_LENGTH = 30;

  @NotNull protected final MainVcsLogUiProperties myUiProperties;

  MultipleValueFilterPopupComponent(@NotNull String filterName,
                                    @NotNull MainVcsLogUiProperties uiProperties,
                                    @NotNull FilterModel<Filter> filterModel) {
    super(filterName, filterModel);
    myUiProperties = uiProperties;
  }

  @NotNull
  protected abstract List<List<String>> getRecentValuesFromSettings();

  protected abstract void rememberValuesInSettings(@NotNull Collection<String> values);

  @NotNull
  protected abstract List<String> getAllValues();

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
  static String displayableText(@NotNull Collection<String> values) {
    if (values.size() == 1) {
      return values.iterator().next();
    }
    return StringUtil.shortenTextWithEllipsis(StringUtil.join(values, "|"), MAX_FILTER_VALUE_LENGTH, 0, true);
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
   * Return true if the filter supports "negative" values, i.e. values like "-value" which means "match anything but 'value'".
   */
  protected boolean supportsNegativeValues() {
    return false;
  }

  protected class PredefinedValueAction extends DumbAwareAction {
    @NotNull protected final List<String> myValues;
    private boolean myAddToRecent;

    public PredefinedValueAction(@NotNull String value, boolean addToRecent) {
      this(Collections.singletonList(value));
      myAddToRecent = addToRecent;
    }

    public PredefinedValueAction(@NotNull String value) {
      this(value, true);
    }

    public PredefinedValueAction(@NotNull List<String> values) {
      this(displayableText(values), values);
    }

    public PredefinedValueAction(@NotNull String name, @NotNull List<String> values) {
      super(null, tooltip(values), null);
      getTemplatePresentation().setText(name, false);
      myValues = values;
    }

    @Override
    public void actionPerformed(@NotNull AnActionEvent e) {
      myFilterModel.setFilter(myFilterModel.createFilter(myValues));
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
      List<String> values = filter == null
                            ? Collections.emptyList()
                            : myFilterModel.getFilterValues(filter);
      final MultilinePopupBuilder popupBuilder = new MultilinePopupBuilder(project, myVariants,
                                                                           getPopupText(values),
                                                                           supportsNegativeValues());
      JBPopup popup = popupBuilder.createPopup();
      popup.addListener(new JBPopupAdapter() {
        @Override
        public void onClosed(LightweightWindowEvent event) {
          if (event.isOk()) {
            List<String> selectedValues = popupBuilder.getSelectedValues();
            if (selectedValues.isEmpty()) {
              myFilterModel.setFilter(null);
            }
            else {
              myFilterModel.setFilter(myFilterModel.createFilter(selectedValues));
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
