// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.maddyhome.idea.copyright.ui;

import com.intellij.copyright.CopyrightBundle;
import com.intellij.copyright.CopyrightManager;
import com.intellij.ide.DataManager;
import com.intellij.ide.util.scopeChooser.PackageSetChooserCombo;
import com.intellij.ide.util.scopeChooser.ScopeChooserConfigurable;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.options.ex.Settings;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.ComboBox;
import com.intellij.openapi.ui.LabeledComponent;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.util.NlsContexts;
import com.intellij.openapi.util.NlsSafe;
import com.intellij.packageDependencies.DependencyValidationManager;
import com.intellij.psi.search.scope.packageSet.CustomScopesProviderEx;
import com.intellij.psi.search.scope.packageSet.NamedScope;
import com.intellij.psi.search.scope.packageSet.NamedScopesHolder;
import com.intellij.ui.HyperlinkLabel;
import com.intellij.ui.JBColor;
import com.intellij.ui.ToolbarDecorator;
import com.intellij.ui.components.editors.JBComboBoxTableCellEditorComponent;
import com.intellij.ui.dsl.listCellRenderer.BuilderKt;
import com.intellij.ui.table.TableView;
import com.intellij.util.ui.*;
import com.maddyhome.idea.copyright.CopyrightProfile;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.event.HyperlinkEvent;
import javax.swing.event.HyperlinkListener;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.TableCellEditor;
import javax.swing.table.TableCellRenderer;
import java.awt.*;
import java.util.*;
import java.util.List;

public class ProjectSettingsPanel {
  private final Project myProject;
  private final CopyrightProfilesPanel myProfilesModel;
  private final CopyrightManager myManager;

  private final TableView<ScopeSetting> myScopeMappingTable;
  private final ListTableModel<ScopeSetting> myScopeMappingModel;
  private final JComboBox<CopyrightProfile> myProfilesComboBox = new ComboBox<>();

  private final HyperlinkLabel myScopesLink = new HyperlinkLabel();

  public ProjectSettingsPanel(Project project, CopyrightProfilesPanel profilesModel) {
    myProject = project;
    myProfilesModel = profilesModel;
    myProfilesModel.addItemsChangeListener(new Runnable() {
      @Override
      public void run() {
        final Object selectedItem = myProfilesComboBox.getSelectedItem();
        reloadCopyrightProfiles();
        myProfilesComboBox.setSelectedItem(selectedItem);
        final ArrayList<ScopeSetting> toRemove = new ArrayList<>();
        for (ScopeSetting setting : myScopeMappingModel.getItems()) {
          if (setting.getProfile() == null) {
            toRemove.add(setting);
          }
        }
        for (ScopeSetting setting : toRemove) {
          myScopeMappingModel.removeRow(myScopeMappingModel.indexOf(setting));
        }
      }
    });
    myManager = CopyrightManager.getInstance(project);

    ColumnInfo[] columns = {new ScopeColumn(), new SettingColumn()};
    myScopeMappingModel = new ListTableModel<>(columns, new ArrayList<>(), 0);
    myScopeMappingTable = new TableView<>(myScopeMappingModel);
    myScopeMappingTable.setShowGrid(false);

    reloadCopyrightProfiles();
    myProfilesComboBox.setRenderer(BuilderKt.textListCellRenderer(CopyrightBundle.message("copyright.no.text"), CopyrightProfile::getName));

    myScopesLink.setVisible(!myProject.isDefault());
    myScopesLink.setHyperlinkText(CopyrightBundle.message("copyright.select.scopes.label"));
    myScopesLink.addHyperlinkListener(new HyperlinkListener() {
      @Override
      public void hyperlinkUpdate(final HyperlinkEvent e) {
        if (e.getEventType() == HyperlinkEvent.EventType.ACTIVATED) {
          DataContext context = DataManager.getInstance().getDataContextFromFocus().getResult();
          if (context != null) {
            Settings settings = Settings.KEY.getData(context);
            if (settings != null) {
              settings.select(settings.find(ScopeChooserConfigurable.PROJECT_SCOPES));
            }
          }
        }
      }
    });
  }

  public void reloadCopyrightProfiles() {
    final DefaultComboBoxModel boxModel = (DefaultComboBoxModel)myProfilesComboBox.getModel();
    boxModel.removeAllElements();
    boxModel.addElement(null);
    for (CopyrightProfile profile : myProfilesModel.getAllProfiles().values()) {
      boxModel.addElement(profile);
    }
  }

  public JComponent getMainComponent() {

    final LabeledComponent<JComboBox> component = new LabeledComponent<>();
    component.setText(CopyrightBundle.message("copyright.default.project.copyright"));
    component.setLabelLocation(BorderLayout.WEST);
    component.setComponent(myProfilesComboBox);
    ElementProducer<ScopeSetting> producer = new ElementProducer<>() {
      @Override
      public ScopeSetting createElement() {
        return new ScopeSetting(CustomScopesProviderEx.getAllScope(), myProfilesModel.getAllProfiles().values().iterator().next());
      }

      @Override
      public boolean canCreateElement() {
        return true;
      }
    };
    ToolbarDecorator decorator = ToolbarDecorator.createDecorator(myScopeMappingTable, producer)
                                                 .setAddActionUpdater(e -> !myProfilesModel.getAllProfiles().isEmpty());
    return JBUI.Panels.simplePanel(0, 10)
      .addToTop(component)
      .addToCenter(decorator.createPanel())
      .addToBottom(myScopesLink);
  }

  public boolean isModified() {
    CopyrightProfile defaultCopyright = myManager.getDefaultCopyright();
    final Object selected = myProfilesComboBox.getSelectedItem();
    if (defaultCopyright != selected && (selected == null || defaultCopyright == null || !defaultCopyright.equals(selected))) {
      return true;
    }
    final Map<String, String> map = myManager.getScopeToCopyright();
    if (map.size() != myScopeMappingModel.getItems().size()) return true;
    final Iterator<String> iterator = map.keySet().iterator();
    for (ScopeSetting setting : myScopeMappingModel.getItems()) {
      final NamedScope scope = setting.getScope();
      if (!iterator.hasNext()) return true;
      final String scopeName = iterator.next();
      if (scope == null || !Comparing.strEqual(scopeName, scope.getScopeId())) return true;
      final String profileName = map.get(scope.getScopeId());
      if (profileName == null) return true;
      if (!profileName.equals(setting.getProfileName())) return true;
    }
    return false;
  }

  public void apply() {
    myManager.setDefaultCopyright((CopyrightProfile)myProfilesComboBox.getSelectedItem());
    myManager.clearMappings();
    for (ScopeSetting scopeSetting : myScopeMappingModel.getItems()) {
      myManager.mapCopyright(scopeSetting.getScope().getScopeId(), scopeSetting.getProfileName());
    }
  }

  public void reset() {
    myProfilesComboBox.setSelectedItem(myManager.getDefaultCopyright());
    final List<ScopeSetting> mappings = new ArrayList<>();
    final Map<String, String> copyrights = myManager.getScopeToCopyright();
    final DependencyValidationManager manager = DependencyValidationManager.getInstance(myProject);
    final Set<String> scopes2Unmap = new HashSet<>();
    for (final String scopeName : copyrights.keySet()) {
      final NamedScope scope = manager.getScope(scopeName);
      if (scope != null) {
        mappings.add(new ScopeSetting(scope, copyrights.get(scopeName)));
      }
      else {
        scopes2Unmap.add(scopeName);
      }
    }
    for (String scopeName : scopes2Unmap) {
      myManager.unmapCopyright(scopeName);
    }
    myScopeMappingModel.setItems(mappings);
  }

  public boolean hasAnyCopyrights() {
    return myProfilesComboBox.getSelectedItem() != null || !myScopeMappingModel.getItems().isEmpty();
  }

  private class ScopeSetting {
    private NamedScope myScope;
    private CopyrightProfile myProfile;
    private String myProfileName;

    private ScopeSetting(NamedScope scope, CopyrightProfile profile) {
      myScope = scope;
      myProfile = profile;
      if (myProfile != null) {
        myProfileName = myProfile.getName();
      }
    }

    ScopeSetting(NamedScope scope, String profile) {
      myScope = scope;
      myProfileName = profile;
    }

    public CopyrightProfile getProfile() {
      if (myProfileName != null) {
        myProfile = myProfilesModel.getAllProfiles().get(getProfileName());
      }
      return myProfile;
    }

    public void setProfile(@NotNull CopyrightProfile profile) {
      myProfile = profile;
      myProfileName = profile.getName();
    }

    public NamedScope getScope() {
      return myScope;
    }

    public void setScope(NamedScope scope) {
      myScope = scope;
    }

    public @NlsSafe String getProfileName() {
      return myProfile != null ? myProfile.getName() : myProfileName;
    }
  }

  private final class SettingColumn extends MyColumnInfo<CopyrightProfile> {
    private SettingColumn() {
      super(CopyrightBundle.message("copyright.copyright.column"));
    }

    @Override
    public TableCellRenderer getRenderer(final ScopeSetting scopeSetting) {
      return new DefaultTableCellRenderer() {
        @Override
        public Component getTableCellRendererComponent(JTable table, Object value, boolean isSelected, boolean hasFocus, int row, int column) {
          final Component rendererComponent = super.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, column);
          if (!isSelected) {
            final CopyrightProfile profile = myProfilesModel.getAllProfiles().get(scopeSetting.getProfileName());
            setForeground(profile == null ? JBColor.RED : UIUtil.getTableForeground());
          }
          setText(scopeSetting.getProfileName());
          return rendererComponent;
        }
      };
    }

    @Override
    public TableCellEditor getEditor(final ScopeSetting scopeSetting) {
      return new AbstractTableCellEditor() {
        private final JBComboBoxTableCellEditorComponent myProfilesChooser = new JBComboBoxTableCellEditorComponent();

        @Override
        public Object getCellEditorValue() {
          return myProfilesChooser.getEditorValue();
        }

        @Override
        public Component getTableCellEditorComponent(JTable table, Object value, boolean isSelected, int row, int column) {
          final List<CopyrightProfile> copyrights = new ArrayList<>(myProfilesModel.getAllProfiles().values());
          copyrights.sort((o1, o2) -> o1.getName().compareToIgnoreCase(o2.getName()));
          myProfilesChooser.setCell(table, row, column);
          myProfilesChooser.setOptions(copyrights.toArray());
          myProfilesChooser.setDefaultValue(scopeSetting.getProfile());
          myProfilesChooser.setToString(o -> ((CopyrightProfile)o).getName());
          return myProfilesChooser;
        }
      };
    }

    @Override
    public CopyrightProfile valueOf(final ScopeSetting object) {
      return object.getProfile();
    }

    @Override
    public void setValue(final ScopeSetting scopeSetting, final CopyrightProfile copyrightProfile) {
      if (copyrightProfile != null) {
        scopeSetting.setProfile(copyrightProfile);
      }
    }
  }

  private final class ScopeColumn extends MyColumnInfo<NamedScope> {
    private ScopeColumn() {
      super(CopyrightBundle.message("copyright.scope.column"));
    }

    @Override
    public TableCellRenderer getRenderer(final ScopeSetting mapping) {
      return new DefaultTableCellRenderer() {
        @Override
        public Component getTableCellRendererComponent(JTable table, Object value, boolean isSelected, boolean hasFocus, int row, int column) {
          super.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, column);
          if (value == null) {
            setText("");
          }
          else {
            final String scopeId = ((NamedScope)value).getScopeId();
            if (!isSelected) {
              final NamedScope scope = NamedScopesHolder.getScope(myProject, scopeId);
              if (scope == null) setForeground(JBColor.RED);
            }
            setText(((NamedScope)value).getPresentableName());
          }
          return this;
        }
      };
    }

    @Override
    public TableCellEditor getEditor(final ScopeSetting mapping) {
      return new AbstractTableCellEditor() {
        private PackageSetChooserCombo myScopeChooser;

        @Override
        @Nullable
        public Object getCellEditorValue() {
          return myScopeChooser.getSelectedScope();
        }

        @Override
        public Component getTableCellEditorComponent(final JTable table, Object value, boolean isSelected, int row, int column) {
          myScopeChooser = new PackageSetChooserCombo(myProject, value == null ? null : ((NamedScope)value).getScopeId(), false, false){
            @Override
            protected NamedScope[] createModel() {
              final NamedScope[] model = super.createModel();
              final ArrayList<NamedScope> filteredScopes = new ArrayList<>(Arrays.asList(model));
              CustomScopesProviderEx.filterNoSettingsScopes(myProject, filteredScopes);
              return filteredScopes.toArray(NamedScope.EMPTY_ARRAY);
            }
          };

          ((JBComboBoxTableCellEditorComponent)myScopeChooser.getChildComponent()).setCell(table, row, column);
          return myScopeChooser;
        }
      };
    }

    @Override
    public NamedScope valueOf(final ScopeSetting mapping) {
      return mapping.getScope();
    }

    @Override
    public void setValue(final ScopeSetting mapping, final NamedScope set) {
      mapping.setScope(set);
    }
  }

  private static abstract class MyColumnInfo<T> extends ColumnInfo<ScopeSetting, T> {
    protected MyColumnInfo(final @NlsContexts.ColumnName String name) {
      super(name);
    }

    @Override
    public boolean isCellEditable(final ScopeSetting item) {
      return true;
    }
  }
}
