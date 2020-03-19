// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.structuralsearch.inspection;

import com.intellij.codeInspection.LocalInspectionTool;
import com.intellij.codeInspection.ex.InspectionProfileImpl;
import com.intellij.codeInspection.ex.InspectionToolWrapper;
import com.intellij.ide.DataManager;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.actionSystem.DefaultActionGroup;
import com.intellij.openapi.project.DumbAwareAction;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectManager;
import com.intellij.openapi.ui.popup.JBPopupFactory;
import com.intellij.openapi.util.JDOMUtil;
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
import org.jdom.Element;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.event.MouseEvent;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

/**
 * @author Bas Leijdekkers
 */
public class StructuralSearchFakeInspection extends LocalInspectionTool {

  @NotNull private Configuration myConfiguration;
  private InspectionProfileImpl myProfile = null;
  private Element myOldConfig = null;

  public StructuralSearchFakeInspection(@NotNull Configuration configuration) {
    myConfiguration = configuration;
  }

  public StructuralSearchFakeInspection(StructuralSearchFakeInspection copy) {
    myConfiguration = copy.myConfiguration;
    myProfile = copy.myProfile;
    myOldConfig = null;
  }

  @Nls(capitalization = Nls.Capitalization.Sentence)
  @NotNull
  @Override
  public String getDisplayName() {
    return myConfiguration.getName();
  }

  @NotNull
  @Override
  public String getShortName() {
    return myConfiguration.getUuid().toString();
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
    final String suppressId = myConfiguration.getSuppressId();
    if (!StringUtil.isEmpty(suppressId)) {
      return suppressId;
    }
    return SSBasedInspection.SHORT_NAME;
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
    final String description = myConfiguration.getDescription();
    if (StringUtil.isEmpty(description)) {
      return SSRBundle.message("no.description.message");
    }
    return description;
  }

  public void setProfile(InspectionProfileImpl profile) {
    myProfile = profile;
  }

  @Override
  public void writeSettings(@NotNull Element node) {
    super.writeSettings(node);
    if (isModified()) node.setAttribute("modified", String.valueOf(true));
  }

  private boolean isModified() {
    if (myOldConfig == null) return false;
    return !JDOMUtil.areElementsEqual(myOldConfig, getSettingsElement());
  }

  private Element getSettingsElement() {
    final SSBasedInspection inspection = getStructuralSearchInspection();
    final Element element = new Element("inspection");
    inspection.writeSettings(element);
    return element;
  }

  @Override
  public @Nullable JComponent createOptionsPanel() {
    myOldConfig = getSettingsElement();
    final MyListModel model = new MyListModel();
    final JButton button = new JButton(SSRBundle.message("edit.metadata.button"));
    button.addActionListener(e -> {
      Project project = CommonDataKeys.PROJECT.getData(DataManager.getInstance().getDataContext(button));
      if (project == null) project = ProjectManager.getInstance().getDefaultProject();
      if (StructuralSearchProfileActionProvider.saveInspection(project, getStructuralSearchInspection(), model.getElementAt(0))) {
        myProfile.getProfileManager().fireProfileChanged(myProfile);
      }
    });

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

  private void performMove(JList<Configuration> list, boolean up) {
    final MyListModel model = (MyListModel)list.getModel();
    final List<Configuration> values = list.getSelectedValuesList();
    final Comparator<Configuration> c = Comparator.comparingInt(Configuration::getOrder);
    Collections.sort(values, up ? c : c.reversed());
    final int[] indices = new int[values.size()];
    for (int i = 0, size = values.size(); i < size; i++) {
      final Configuration value = values.get(i);
      final int order = value.getOrder();
      model.swap(order, order + (up ? -1 : +1));
      indices[i] = value.getOrder();
    }
    myConfiguration = moveMetaData(myConfiguration, model.getElementAt(0));
    list.setSelectedIndices(indices);
    model.fireContentsChanged(list);
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
    final SSBasedInspection inspection = getStructuralSearchInspection();
    boolean metaData = false;
    for (Configuration configuration : list.getSelectedValuesList()) {
      if (configuration.getOrder() == 0) {
        metaData = true;
      }
      inspection.removeConfiguration(configuration);
    }

    final MyListModel model = (MyListModel)list.getModel();
    model.clearCache();
    if (metaData) {
      myConfiguration = moveMetaData(myConfiguration, model.getElementAt(0));
    }
    for (int i = 0, max = model.getSize(); i < max; i++){
      model.getElementAt(i).setOrder(i);
    }
    model.fireContentsChanged(list);
  }

  private void performEdit(JList<Configuration> list) {
    final Project project = CommonDataKeys.PROJECT.getData(DataManager.getInstance().getDataContext(list));
    if (project == null) {
      return;
    }
    final Configuration configuration = list.getSelectedValue();
    if (configuration == null) {
      return;
    }
    final SearchContext searchContext = new SearchContext(project);
    final StructuralSearchDialog dialog = new StructuralSearchDialog(searchContext, !(configuration instanceof SearchConfiguration), true);
    dialog.loadConfiguration(configuration);
    dialog.setUseLastConfiguration(true);
    if (!dialog.showAndGet()) return;
    final Configuration newConfiguration = dialog.getConfiguration();
    final SSBasedInspection inspection = getStructuralSearchInspection();
    if (configuration.getOrder() == 0) {
      myConfiguration = newConfiguration;
    }
    inspection.removeConfiguration(configuration);
    inspection.addConfiguration(newConfiguration);
    final MyListModel model = (MyListModel)list.getModel();
    model.clearCache();
    model.fireContentsChanged(list);
  }

  private SSBasedInspection getStructuralSearchInspection() {
    final InspectionToolWrapper<?, ?> wrapper = myProfile.getInspectionTool(SSBasedInspection.SHORT_NAME, (Project)null);
    assert wrapper != null;
    return (SSBasedInspection)wrapper.getTool();
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
      configuration.setUuid(myConfiguration.getUuid());
      configuration.setName(myConfiguration.getName());
      final MyListModel model = (MyListModel)myList.getModel();
      configuration.setOrder(model.getSize());

      getStructuralSearchInspection().addConfiguration(configuration);
      model.clearCache();
      model.fireContentsChanged(myList);
    }
  }

  private class MyListModel extends AbstractListModel<Configuration> {

    private final List<Configuration> cache = new SmartList<>();

    @Override
    public int getSize() {
      initialize();
      return cache.size();
    }

    @Override
    public Configuration getElementAt(int index) {
      initialize();
      return cache.get(index);
    }

    public void fireContentsChanged(Object source) {
      fireContentsChanged(source, -1, -1);
    }

    public void clearCache() {
      cache.clear();
    }

    public void swap(int first, int second) {
      final Configuration one = cache.get(first);
      final Configuration two = cache.get(second);
      final int order = one.getOrder();
      one.setOrder(two.getOrder());
      two.setOrder(order);
      cache.set(second, one);
      cache.set(first, two);
    }

    private void initialize() {
      if (!cache.isEmpty()) return;

      final List<Configuration> configurations = getStructuralSearchInspection().getConfigurationsWithUuid(myConfiguration.getUuid());
      cache.addAll(configurations);
      Collections.sort(cache, Comparator.comparingInt(Configuration::getOrder));
    }
  }
}
