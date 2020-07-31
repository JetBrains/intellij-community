// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.structuralsearch.inspection;

import com.intellij.codeInsight.daemon.HighlightDisplayKey;
import com.intellij.codeInspection.LocalInspectionTool;
import com.intellij.codeInspection.ex.InspectionProfileModifiableModel;
import com.intellij.ide.DataManager;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.actionSystem.DefaultActionGroup;
import com.intellij.openapi.project.DumbAwareAction;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.popup.JBPopupFactory;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.structuralsearch.SSRBundle;
import com.intellij.structuralsearch.plugin.ui.*;
import com.intellij.ui.AnActionButton;
import com.intellij.ui.DoubleClickListener;
import com.intellij.ui.ToolbarDecorator;
import com.intellij.ui.awt.RelativePoint;
import com.intellij.ui.components.JBList;
import com.intellij.util.SmartList;
import com.intellij.util.ui.FormBuilder;
import com.intellij.util.ui.JBUI;
import org.intellij.lang.annotations.Pattern;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.awt.event.MouseEvent;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

/**
 * @author Bas Leijdekkers
 */
public class StructuralSearchFakeInspection extends LocalInspectionTool {

  private Configuration myMainConfiguration;
  @NotNull private final List<Configuration> myConfigurations;

  public StructuralSearchFakeInspection(@NotNull Collection<@NotNull Configuration> configurations) {
    if (configurations.isEmpty()) throw new IllegalArgumentException();
    myConfigurations = new SmartList<>(configurations);
    myConfigurations.sort(Comparator.comparingInt(Configuration::getOrder));
    myMainConfiguration = myConfigurations.get(0);
  }

  @Nls(capitalization = Nls.Capitalization.Sentence)
  @NotNull
  @Override
  public String getDisplayName() {
    return myMainConfiguration.getName();
  }

  @NotNull
  @Override
  public String getShortName() {
    return myMainConfiguration.getUuid().toString();
  }

  @Override
  public boolean isEnabledByDefault() {
    return true;
  }

  @SuppressWarnings("PatternValidation")
  @Pattern(VALID_ID_PATTERN)
  @NotNull
  @Override
  public String getID() {
    final HighlightDisplayKey key = HighlightDisplayKey.find(getShortName());
    if (key != null) {
      return key.getID(); // to avoid using a new suppress id before it is registered.
    }
    final String suppressId = myMainConfiguration.getSuppressId();
    return !StringUtil.isEmpty(suppressId) ? suppressId : SSBasedInspection.SHORT_NAME;
  }

  @Override
  public @Nullable String getAlternativeID() {
    return SSBasedInspection.SHORT_NAME;
  }

  @Nullable
  @Override
  public String getMainToolId() {
    return SSBasedInspection.SHORT_NAME;
  }

  @Nls(capitalization = Nls.Capitalization.Sentence)
  @NotNull
  @Override
  public String getGroupDisplayName() {
    return SSRBundle.message("structural.search.group.name");
  }

  @Nullable
  @Override
  public String getStaticDescription() {
    final String description = myMainConfiguration.getDescription();
    if (StringUtil.isEmpty(description)) {
      return SSRBundle.message("no.description.message");
    }
    return description;
  }

  public @NotNull List<Configuration> getConfigurations() {
    return Collections.unmodifiableList(myConfigurations);
  }

  @Override
  public @Nullable JComponent createOptionsPanel() {
    final MyListModel model = new MyListModel();
    final JButton button = new JButton(SSRBundle.message("edit.metadata.button"));
    button.addActionListener(e -> performEditMetaData(button));

    final JList<Configuration> list = new JBList<>(model);
    list.setCellRenderer(new ConfigurationCellRenderer());
    final JPanel listPanel = ToolbarDecorator.createDecorator(list)
      .setAddAction(b -> performAdd(list, b))
      .setAddActionName(SSRBundle.message("add.pattern.action"))
      .setRemoveAction(b -> performRemove(list))
      .setRemoveActionUpdater(e -> list.getSelectedValuesList().size() < list.getModel().getSize())
      .setEditAction(b -> performEdit(list))
      .setMoveUpAction(b -> performMove(list, true))
      .setMoveDownAction(b -> performMove(list, false))
      .createPanel();

    new DoubleClickListener() {
      @Override
      protected boolean onDoubleClick(@NotNull MouseEvent e) {
        performEdit(list);
        return true;
      }
    }.installOn(list);

    final JPanel panel = new FormBuilder()
      .addComponent(button)
      .addLabeledComponentFillVertically(SSRBundle.message("templates.title"), listPanel)
      .getPanel();
    panel.setBorder(JBUI.Borders.emptyTop(10));
    return panel;
  }

  private void performEditMetaData(Component context) {
    final Project project = CommonDataKeys.PROJECT.getData(DataManager.getInstance().getDataContext(context));
    final InspectionProfileModifiableModel profile = InspectionProfileUtil.getInspectionProfile(context);
    if (profile == null) {
      return;
    }
    final SSBasedInspection inspection = InspectionProfileUtil.getStructuralSearchInspection(profile);
    final StructuralSearchProfileActionProvider.InspectionDataDialog dialog =
      new StructuralSearchProfileActionProvider.InspectionDataDialog(project, inspection, myMainConfiguration, false);
    if (!dialog.showAndGet()) {
      return;
    }
    final String name = dialog.getName();
    for (Configuration c : myConfigurations) {
      c.setName(name);
    }
    inspection.removeConfigurationsWithUuid(myMainConfiguration.getUuid());
    inspection.addConfigurations(myConfigurations);
    profile.setModified(true);
    InspectionProfileUtil.fireProfileChanged(profile);
  }

  private void performMove(JList<Configuration> list, boolean up) {
    final MyListModel model = (MyListModel)list.getModel();
    final List<Configuration> values = list.getSelectedValuesList();
    final Comparator<Configuration> c = Comparator.comparingInt(Configuration::getOrder);
    values.sort(up ? c : c.reversed());
    final int[] indices = new int[values.size()];
    for (int i = 0, size = values.size(); i < size; i++) {
      final Configuration value = values.get(i);
      final int order = value.getOrder();
      model.swap(order, order + (up ? -1 : +1));
      indices[i] = value.getOrder();
    }
    myMainConfiguration = moveMetaData(myMainConfiguration, myConfigurations.get(0));
    list.setSelectedIndices(indices);
    list.scrollRectToVisible(list.getCellBounds(indices[0], indices[indices.length - 1]));
    model.fireContentsChanged(list);

    final InspectionProfileModifiableModel profile = InspectionProfileUtil.getInspectionProfile(list);
    if (profile == null) return;
    final SSBasedInspection inspection = InspectionProfileUtil.getStructuralSearchInspection(profile);
    inspection.removeConfigurationsWithUuid(myMainConfiguration.getUuid());
    inspection.addConfigurations(myConfigurations);
    profile.setModified(true);
  }

  private static Configuration moveMetaData(Configuration source, Configuration target) {
    if (source == target) return source;
    target.setDescription(source.getDescription());
    target.setSuppressId(source.getSuppressId());
    target.setProblemDescriptor(source.getProblemDescriptor());
    source.setDescription(null);
    source.setSuppressId(null);
    source.setProblemDescriptor(null);
    return target;
  }

  private void performAdd(JList<Configuration> list, AnActionButton b) {
    final AnAction[] children = new AnAction[]{new AddTemplateAction(list, false), new AddTemplateAction(list, true)};
    final RelativePoint point = b.getPreferredPopupPoint();
    if (point == null) return;
    JBPopupFactory.getInstance().createActionGroupPopup(null, new DefaultActionGroup(children),
                                                        DataManager.getInstance().getDataContext(b.getContextComponent()),
                                                        JBPopupFactory.ActionSelectionAid.SPEEDSEARCH, true).show(point);
  }

  private void performRemove(JList<Configuration> list) {
    boolean metaData = false;
    for (Configuration configuration : list.getSelectedValuesList()) {
      if (configuration.getOrder() == 0) {
        metaData = true;
      }
      myConfigurations.remove(configuration);
    }
    if (metaData) {
      myMainConfiguration = moveMetaData(myMainConfiguration, myConfigurations.get(0));
    }
    final int size = myConfigurations.size();
    for (int i = 0; i < size; i++){
      myConfigurations.get(i).setOrder(i);
    }
    final int maxIndex = list.getMaxSelectionIndex();
    if (maxIndex != list.getMinSelectionIndex()) {
      list.setSelectedIndex(maxIndex);
    }
    ((MyListModel)list.getModel()).fireContentsChanged(list);
    if (list.getSelectedIndex() >= size) {
      list.setSelectedIndex(size - 1);
    }
    final int index = list.getSelectedIndex();
    list.scrollRectToVisible(list.getCellBounds(index, index));


    final InspectionProfileModifiableModel profile = InspectionProfileUtil.getInspectionProfile(list);
    if (profile == null) return;
    final SSBasedInspection inspection = InspectionProfileUtil.getStructuralSearchInspection(profile);
    inspection.removeConfigurationsWithUuid(myMainConfiguration.getUuid());
    inspection.addConfigurations(myConfigurations);
    profile.setModified(true);
  }

  private void performEdit(JList<Configuration> list) {
    final Project project = CommonDataKeys.PROJECT.getData(DataManager.getInstance().getDataContext(list));
    if (project == null) return;
    final int index = list.getSelectedIndex();
    final Configuration configuration = myConfigurations.get(index);
    if (configuration == null) return;
    final SearchContext searchContext = new SearchContext(project);
    final StructuralSearchDialog dialog = new StructuralSearchDialog(searchContext, !(configuration instanceof SearchConfiguration), true);
    dialog.loadConfiguration(configuration);
    dialog.setUseLastConfiguration(true);
    if (!dialog.showAndGet()) return;
    final Configuration newConfiguration = dialog.getConfiguration();
    if (configuration.getOrder() == 0) {
      myMainConfiguration = newConfiguration;
    }
    myConfigurations.set(index, newConfiguration);
    final MyListModel model = (MyListModel)list.getModel();
    model.fireContentsChanged(list);

    final InspectionProfileModifiableModel profile = InspectionProfileUtil.getInspectionProfile(list);
    if (profile == null) return;
    final SSBasedInspection inspection = InspectionProfileUtil.getStructuralSearchInspection(profile);
    inspection.removeConfiguration(configuration);
    inspection.addConfiguration(newConfiguration);
    profile.setModified(true);
  }

  private class AddTemplateAction extends DumbAwareAction {

    private final JList<Configuration> myList;
    private final boolean myReplace;

    private AddTemplateAction(JList<Configuration> list, boolean replace) {
      super(replace
            ? SSRBundle.message("SSRInspection.add.replace.template.button")
            : SSRBundle.message("SSRInspection.add.search.template.button"));
      myList = list;
      myReplace = replace;
    }

    @Override
    public void actionPerformed(@NotNull AnActionEvent e) {
      final Project project = e.getData(CommonDataKeys.PROJECT);
      assert project != null;
      final SearchContext context = new SearchContext(project);
      final StructuralSearchDialog dialog = new StructuralSearchDialog(context, myReplace, true);
      if (!dialog.showAndGet()) return;
      final Configuration configuration = dialog.getConfiguration();
      configuration.setUuid(myMainConfiguration.getUuid());
      configuration.setName(myMainConfiguration.getName());
      configuration.setDescription(null);
      configuration.setSuppressId(null);
      configuration.setProblemDescriptor(null);
      final MyListModel model = (MyListModel)myList.getModel();
      final int size = model.getSize();
      configuration.setOrder(size);

      final InspectionProfileModifiableModel profile = InspectionProfileUtil.getInspectionProfile(myList);
      if (profile == null) return;
      if (InspectionProfileUtil.getStructuralSearchInspection(profile).addConfiguration(configuration)) {
        myConfigurations.add(configuration);
        model.fireContentsChanged(myList);
        myList.setSelectedIndex(size);
        myList.scrollRectToVisible(myList.getCellBounds(size, size));
        profile.setModified(true);
      }
      else {
        final int index = myConfigurations.indexOf(configuration);
        if (index >= 0) {
          myList.setSelectedIndex(index);
        }
      }
    }
  }

  private class MyListModel extends AbstractListModel<Configuration> {

    @Override
    public int getSize() {
      return myConfigurations.size();
    }

    @Override
    public Configuration getElementAt(int index) {
      return myConfigurations.get(index);
    }

    public void fireContentsChanged(Object source) {
      fireContentsChanged(source, -1, -1);
    }

    public void swap(int first, int second) {
      final Configuration one = myConfigurations.get(first);
      final Configuration two = myConfigurations.get(second);
      final int order = one.getOrder();
      one.setOrder(two.getOrder());
      two.setOrder(order);
      myConfigurations.set(second, one);
      myConfigurations.set(first, two);
    }
  }
}
