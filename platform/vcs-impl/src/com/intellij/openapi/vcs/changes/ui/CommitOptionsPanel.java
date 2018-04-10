/*
 * Copyright 2000-2017 JetBrains s.r.o.
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
package com.intellij.openapi.vcs.changes.ui;

import com.intellij.openapi.Disposable;
import com.intellij.openapi.vcs.AbstractVcs;
import com.intellij.openapi.vcs.CheckinProjectPanel;
import com.intellij.openapi.vcs.changes.ChangesUtil;
import com.intellij.openapi.vcs.changes.LocalChangeList;
import com.intellij.openapi.vcs.changes.PseudoMap;
import com.intellij.openapi.vcs.checkin.CheckinChangeListSpecificComponent;
import com.intellij.openapi.vcs.checkin.CheckinEnvironment;
import com.intellij.openapi.vcs.checkin.CheckinHandler;
import com.intellij.openapi.vcs.ui.Refreshable;
import com.intellij.openapi.vcs.ui.RefreshableOnComponent;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.ui.IdeBorderFactory;
import com.intellij.ui.ScrollPaneFactory;
import com.intellij.util.ui.JBUI;
import com.intellij.util.ui.components.BorderLayoutPanel;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.awt.*;
import java.util.*;
import java.util.List;

import static com.intellij.openapi.vcs.VcsBundle.message;
import static com.intellij.util.containers.ContainerUtil.*;
import static java.util.Collections.unmodifiableList;

public class CommitOptionsPanel extends BorderLayoutPanel implements Refreshable, Disposable {

  private static final Comparator<AbstractVcs> VCS_COMPARATOR =
    Comparator.comparing(it -> it.getKeyInstanceMethod().getName(), String::compareToIgnoreCase);

  @NotNull private final CheckinProjectPanel myCommitPanel;
  @NotNull private final Collection<CheckinHandler> myHandlers;
  @NotNull private final Map<AbstractVcs, JPanel> myPerVcsOptionsPanels = newHashMap();
  @NotNull private final List<RefreshableOnComponent> myAdditionalComponents = newArrayList();
  @NotNull private final Set<CheckinChangeListSpecificComponent> myCheckinChangeListSpecificComponents = newHashSet();
  @NotNull private final PseudoMap<Object, Object> myAdditionalData = new PseudoMap<>();
  private final boolean myEmpty;

  public CommitOptionsPanel(@NotNull CheckinProjectPanel panel,
                            @NotNull Collection<CheckinHandler> handlers,
                            @NotNull Collection<AbstractVcs> vcses) {
    myCommitPanel = panel;
    myHandlers = handlers;
    myEmpty = init(vcses);
  }

  public boolean isEmpty() {
    return myEmpty;
  }

  @NotNull
  public List<RefreshableOnComponent> getAdditionalComponents() {
    return unmodifiableList(myAdditionalComponents);
  }

  @NotNull
  public PseudoMap<Object, Object> getAdditionalData() {
    return myAdditionalData;
  }

  @Override
  public void saveState() {
    myAdditionalComponents.forEach(RefreshableOnComponent::saveState);
  }

  @Override
  public void restoreState() {
    myAdditionalComponents.forEach(RefreshableOnComponent::restoreState);
  }

  @Override
  public void refresh() {
    myAdditionalComponents.forEach(RefreshableOnComponent::refresh);
  }

  public void onChangeListSelected(@NotNull LocalChangeList changeList, List<VirtualFile> unversionedFiles) {
    Set<AbstractVcs> affectedVcses = union(
      ChangesUtil.getAffectedVcses(changeList.getChanges(), myCommitPanel.getProject()),
      ChangesUtil.getAffectedVcsesForFiles(unversionedFiles, myCommitPanel.getProject()));
    for (Map.Entry<AbstractVcs, JPanel> entry : myPerVcsOptionsPanels.entrySet()) {
      entry.getValue().setVisible(affectedVcses.contains(entry.getKey()));
    }

    myCheckinChangeListSpecificComponents.forEach(component -> component.onChangeListSelected(changeList));
  }

  public void saveChangeListComponentsState() {
    myCheckinChangeListSpecificComponents.forEach(CheckinChangeListSpecificComponent::saveState);
  }

  @Override
  public void dispose() {
  }

  private boolean init(@NotNull Collection<AbstractVcs> vcses) {
    String borderTitleName = myCommitPanel.getCommitActionName().replace("_", "").replace("&", "");
    boolean hasVcsOptions = false;
    Box vcsCommitOptions = Box.createVerticalBox();
    for (AbstractVcs vcs : sorted(vcses, VCS_COMPARATOR)) {
      CheckinEnvironment checkinEnvironment = vcs.getCheckinEnvironment();
      if (checkinEnvironment != null) {
        RefreshableOnComponent options = checkinEnvironment.createAdditionalOptionsPanel(myCommitPanel, myAdditionalData);
        if (options != null) {
          JPanel vcsOptions = new JPanel(new BorderLayout());
          vcsOptions.add(options.getComponent(), BorderLayout.CENTER);
          vcsOptions.setBorder(IdeBorderFactory.createTitledBorder(vcs.getDisplayName(), true));
          vcsCommitOptions.add(vcsOptions);
          myPerVcsOptionsPanels.put(vcs, vcsOptions);
          myAdditionalComponents.add(options);
          if (options instanceof CheckinChangeListSpecificComponent) {
            myCheckinChangeListSpecificComponents.add((CheckinChangeListSpecificComponent)options);
          }
          hasVcsOptions = true;
        }
      }
    }

    boolean beforeVisible = false;
    boolean afterVisible = false;
    Box beforeBox = Box.createVerticalBox();
    Box afterBox = Box.createVerticalBox();
    for (CheckinHandler handler : myHandlers) {
      RefreshableOnComponent beforePanel = handler.getBeforeCheckinConfigurationPanel();
      if (beforePanel != null) {
        beforeVisible = true;
        addCheckinHandlerComponent(beforePanel, beforeBox);
      }

      RefreshableOnComponent afterPanel = handler.getAfterCheckinConfigurationPanel(this);
      if (afterPanel != null) {
        afterVisible = true;
        addCheckinHandlerComponent(afterPanel, afterBox);
      }
    }

    if (!hasVcsOptions && !beforeVisible && !afterVisible) return true;

    Box optionsBox = Box.createVerticalBox();
    if (hasVcsOptions) {
      vcsCommitOptions.add(Box.createVerticalGlue());
      optionsBox.add(vcsCommitOptions);
    }

    if (beforeVisible) {
      beforeBox.add(Box.createVerticalGlue());
      JPanel beforePanel = new JPanel(new BorderLayout());
      beforePanel.add(beforeBox);
      beforePanel.setBorder(IdeBorderFactory.createTitledBorder(
        message("border.standard.checkin.options.group", borderTitleName), true));
      optionsBox.add(beforePanel);
    }

    if (afterVisible) {
      afterBox.add(Box.createVerticalGlue());
      JPanel afterPanel = new JPanel(new BorderLayout());
      afterPanel.add(afterBox);
      afterPanel.setBorder(IdeBorderFactory.createTitledBorder(
        message("border.standard.after.checkin.options.group", borderTitleName), true));
      optionsBox.add(afterPanel);
    }

    optionsBox.add(Box.createVerticalGlue());
    JPanel additionalOptionsPanel = new JPanel(new BorderLayout());
    additionalOptionsPanel.add(optionsBox, BorderLayout.NORTH);

    JScrollPane optionsPane = ScrollPaneFactory.createScrollPane(additionalOptionsPanel, true);
    addToCenter(optionsPane).withBorder(JBUI.Borders.emptyLeft(10));
    return false;
  }

  private void addCheckinHandlerComponent(@NotNull RefreshableOnComponent component, @NotNull Box container) {
    container.add(component.getComponent());
    myAdditionalComponents.add(component);
    if (component instanceof CheckinChangeListSpecificComponent) {
      myCheckinChangeListSpecificComponents.add((CheckinChangeListSpecificComponent)component);
    }
  }
}
