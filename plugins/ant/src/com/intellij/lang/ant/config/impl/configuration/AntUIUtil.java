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
package com.intellij.lang.ant.config.impl.configuration;

import com.intellij.icons.AllIcons;
import com.intellij.ide.macro.MacrosDialog;
import com.intellij.lang.ant.AntBundle;
import com.intellij.lang.ant.config.impl.AntClasspathEntry;
import com.intellij.lang.ant.config.impl.AntInstallation;
import com.intellij.lang.ant.config.impl.AntReference;
import com.intellij.lang.ant.config.impl.GlobalAntConfiguration;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.openapi.roots.ui.OrderEntryAppearanceService;
import com.intellij.openapi.ui.FixedSizeButton;
import com.intellij.openapi.ui.TextFieldWithBrowseButton;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.ui.ColoredListCellRenderer;
import com.intellij.ui.SimpleColoredComponent;
import com.intellij.ui.SimpleTextAttributes;
import com.intellij.util.IconUtil;
import com.intellij.util.PlatformIcons;
import com.intellij.util.config.AbstractProperty;
import com.intellij.util.ui.AbstractTableCellEditor;
import com.intellij.util.ui.CellEditorComponentWithBrowseButton;
import icons.AntIcons;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import javax.swing.text.BadLocationException;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;

public class AntUIUtil {

  private static final Logger LOG = Logger.getInstance("#com.intellij.ant.impl.configuration.AntUIUtil");

  private AntUIUtil() {
  }

  public interface PropertiesEditor<T> {
    AbstractProperty.AbstractPropertyContainer getProperties(T object);
  }

  public static class AntInstallationRenderer extends ColoredListCellRenderer {
    private final PropertiesEditor<AntInstallation> myEditor;

    public AntInstallationRenderer(PropertiesEditor<AntInstallation> editor) {
      myEditor = editor != null ? editor : new PropertiesEditor<AntInstallation>(){
        public AbstractProperty.AbstractPropertyContainer getProperties(AntInstallation antInstallation) {
          return antInstallation.getProperties();
        }
      };
    }

    protected void customizeCellRenderer(@NotNull JList list, Object value, int index, boolean selected, boolean hasFocus) {
      AntInstallation ant = (AntInstallation)value;
      if (ant == null) return;
      AbstractProperty.AbstractPropertyContainer container = myEditor.getProperties(ant);
      customizeAnt(container, this);
    }
  }

  public static class AntReferenceRenderer extends ColoredListCellRenderer {
    private final GlobalAntConfiguration myConfiguration;

    public AntReferenceRenderer(GlobalAntConfiguration configuration) {
      myConfiguration = configuration;
    }

    protected void customizeCellRenderer(@NotNull JList list, Object value, int index, boolean selected, boolean hasFocus) {
      if (value == null) return;
      customizeReference((AntReference)value, this, myConfiguration);
    }
  }

  public static void customizeReference(AntReference antReference, SimpleColoredComponent component, GlobalAntConfiguration configuration) {
    AntInstallation antInstallation = antReference.find(configuration);
    if (antInstallation != null) customizeAnt(antInstallation.getProperties(), component);
    else {
      component.setIcon(PlatformIcons.INVALID_ENTRY_ICON);
      component.append(antReference.getName(), SimpleTextAttributes.ERROR_ATTRIBUTES);
    }
  }

  public static void customizeAnt(AbstractProperty.AbstractPropertyContainer antProperties, SimpleColoredComponent component) {
    component.setIcon(AntIcons.AntInstallation);
    String name = AntInstallation.NAME.get(antProperties);
    component.append(name, SimpleTextAttributes.REGULAR_ATTRIBUTES);
    String versionString = AntInstallation.VERSION.get(antProperties);
    if (name.indexOf(versionString) == -1)
      component.append(" (" + versionString + ")", SimpleTextAttributes.SYNTHETIC_ATTRIBUTES);
  }


  public static class ClasspathRenderer extends ColoredListCellRenderer {
    protected void customizeCellRenderer(@NotNull JList list, Object value, int index, boolean selected, boolean hasFocus) {
      AntClasspathEntry entry = (AntClasspathEntry)value;
      entry.getAppearance().customize(this);
    }
  }

  public static class PropertyValueCellEditor extends AbstractTableCellEditor {
    private final CellEditorComponentWithBrowseButton<JTextField> myComponent;

    public PropertyValueCellEditor() {
      myComponent = new CellEditorComponentWithBrowseButton<>(new TextFieldWithBrowseButton(), this);
      getChildComponent().setBorder(BorderFactory.createLineBorder(Color.black));

      FixedSizeButton button = myComponent.getComponentWithButton().getButton();
      button.setIcon(IconUtil.getAddIcon());
      button.setToolTipText(AntBundle.message("ant.property.value.editor.insert.macro.tooltip.text"));
      button.addActionListener(new ActionListener() {
        public void actionPerformed(ActionEvent e) {
          MacrosDialog dialog = new MacrosDialog(getChildComponent());
          if (dialog.showAndGet() && dialog.getSelectedMacro() != null) {
            JTextField textField = getChildComponent();

            String macro = dialog.getSelectedMacro().getName();
            int position = textField.getCaretPosition();
            try {
              textField.getDocument().insertString(position, "$" + macro + "$", null);
              textField.setCaretPosition(position + macro.length() + 2);
            }
            catch (BadLocationException ex) {
              LOG.error(ex);
            }
            textField.requestFocus();
          }
        }
      });
    }

    public Object getCellEditorValue() {
      return getChildComponent().getText();
    }

    protected void initializeEditor(Object value) {
      getChildComponent().setText((String)value);
    }

    private JTextField getChildComponent() {
      return myComponent.getChildComponent();
    }

    public Component getTableCellEditorComponent(JTable table, Object value, boolean isSelected, int row, int column) {
      getChildComponent().setText((String)value);
      return myComponent;
    }
  }

  public static class ProjectJdkRenderer extends ColoredListCellRenderer {
    private final boolean myInComboBox;
    private final String myProjectJdkName;

    public ProjectJdkRenderer(boolean inComboBox, String projectJdkName) {
      myInComboBox = inComboBox;
      myProjectJdkName = projectJdkName != null ? projectJdkName : "";
    }

    protected void customizeCellRenderer(@NotNull JList list, Object value, int index, boolean selected, boolean hasFocus) {
      String jdkName = (String)value;
      if (jdkName == null || jdkName.length() == 0) jdkName = "";
      Sdk jdk = GlobalAntConfiguration.findJdk(jdkName);
      if (jdk == null) {
        if (myProjectJdkName.length() > 0) {
          setIcon(AllIcons.General.Jdk);
          append(AntBundle.message("project.jdk.project.jdk.name.list.column.value", myProjectJdkName),
                 selected && !(SystemInfo.isWinVistaOrNewer && UIManager.getLookAndFeel().getName().contains("Windows"))
                 ? SimpleTextAttributes.SELECTED_SIMPLE_CELL_ATTRIBUTES
                 : SimpleTextAttributes.SIMPLE_CELL_ATTRIBUTES);
        }
        else {
          setIcon(PlatformIcons.INVALID_ENTRY_ICON);
          append(AntBundle.message("project.jdk.not.specified.list.column.value"), SimpleTextAttributes.ERROR_ATTRIBUTES);
        }
      }
      else  {
        OrderEntryAppearanceService.getInstance().forJdk(jdk, myInComboBox, selected, true).customize(this);
      }
    }
  }
}
