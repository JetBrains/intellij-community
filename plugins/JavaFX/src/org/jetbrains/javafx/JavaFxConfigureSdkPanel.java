package org.jetbrains.javafx;

import com.intellij.facet.ui.FacetEditorValidator;
import com.intellij.facet.ui.FacetValidatorsManager;
import com.intellij.facet.ui.ValidationResult;
import com.intellij.ide.DataManager;
import com.intellij.openapi.actionSystem.PlatformDataKeys;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectManager;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.openapi.projectRoots.ui.ProjectJdksEditor;
import com.intellij.openapi.ui.LabeledComponent;
import com.intellij.util.ArrayUtil;
import com.intellij.util.Icons;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.javafx.sdk.JavaFxSdkUtil;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.ArrayList;
import java.util.List;

/**
 * Created by IntelliJ IDEA.
 *
 * @author: Alexey.Ivanov
 */
public class JavaFxConfigureSdkPanel extends JComponent {
  private LabeledComponent<JComboBox> mySdkComponent;
  private JButton myConfigureSdksButton;
  private JPanel myMainPanel;

  public JavaFxConfigureSdkPanel() {
    super();

    final JComboBox comboBox = mySdkComponent.getComponent();
    comboBox.setRenderer(new DefaultListCellRenderer() {
      public Component getListCellRendererComponent(JList list, Object value, int index, boolean isSelected, boolean cellHasFocus) {
        super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);
        if (value instanceof Sdk) {
          setText(((Sdk)value).getName());
          setIcon(((Sdk)value).getSdkType().getIcon());
        }
        else {
          setIcon(Icons.ERROR_INTRODUCTION_ICON);
          if (comboBox.isEnabled()) {
            setText("<html><font color='red'>&lt;No SDK&gt;</font></html>");
          }
          else {
            setText("<No SDK>");
          }
        }
        return this;
      }
    });
    initConfigureSdksButton();
    resetSdk(null);
  }

  private void initConfigureSdksButton() {
    myConfigureSdksButton.addActionListener(new ActionListener() {
      public void actionPerformed(ActionEvent e) {
        final Sdk selectedSdk = getSelectedSdk();
        Project project = PlatformDataKeys.PROJECT.getData(DataManager.getInstance().getDataContext());
        if (project == null) {
          project = ProjectManager.getInstance().getDefaultProject();
        }
        final ProjectJdksEditor editor = new ProjectJdksEditor(selectedSdk, project, JavaFxConfigureSdkPanel.this.myMainPanel);
        editor.show();
        if (editor.isOK()) {
          resetSdk(editor.getSelectedJdk());
        }
      }
    });
  }

  public void registerValidator(FacetValidatorsManager validatorsManager) {
    final JComboBox comboBox = mySdkComponent.getComponent();
    validatorsManager.registerValidator(new FacetEditorValidator() {
      @Override
      public ValidationResult check() {
        final Object selectedItem = comboBox.getSelectedItem();
        if (selectedItem instanceof Sdk) {
          return ValidationResult.OK;
        }
        return new ValidationResult(JavaFxBundle.message("invalid.sdk"));
      }
    }, comboBox);
  }

  @Nullable
  public Sdk getSelectedSdk() {
    final Object selectedItem = mySdkComponent.getComponent().getSelectedItem();
    if (selectedItem instanceof Sdk) {
      return (Sdk)selectedItem;
    }
    return null;
  }

  public void resetSdk(@Nullable final Sdk javaFxSdk) {
    Object initiallySelectedItem = javaFxSdk != null ? javaFxSdk : JavaFxBundle.message("sdk.not.yet.selected");
    final List<Sdk> sdks = JavaFxSdkUtil.getAllRelatedSdks();
    final List<Object> sdksForCombo = new ArrayList<Object>(sdks);
    if (javaFxSdk != null) {
      for (final Sdk sdk : sdks) {
        if (sdk.getName().equals(javaFxSdk.getName())) {
          initiallySelectedItem = sdk;
          break;
        }
      }
    }
    final JComboBox comboBox = mySdkComponent.getComponent();
    comboBox.setModel(new DefaultComboBoxModel(ArrayUtil.toObjectArray(sdksForCombo)));
    comboBox.setSelectedItem(initiallySelectedItem);
  }
}
