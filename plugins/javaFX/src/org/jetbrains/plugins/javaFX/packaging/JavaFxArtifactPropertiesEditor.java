/*
 * Copyright 2000-2013 JetBrains s.r.o.
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
package org.jetbrains.plugins.javaFX.packaging;

import com.intellij.execution.JavaExecutionUtil;
import com.intellij.execution.ui.ClassBrowser;
import com.intellij.ide.util.ClassFilter;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.fileChooser.FileChooserDescriptor;
import com.intellij.openapi.fileChooser.FileChooserDescriptorFactory;
import com.intellij.openapi.fileTypes.StdFileTypes;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.TextFieldWithBrowseButton;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.util.Computable;
import com.intellij.packaging.artifacts.Artifact;
import com.intellij.packaging.impl.artifacts.ArtifactUtil;
import com.intellij.packaging.ui.ArtifactPropertiesEditor;
import com.intellij.psi.PsiClass;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.util.InheritanceUtil;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.text.JTextComponent;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.Collections;
import java.util.Set;

/**
 * User: anna
 * Date: 3/12/13
 */
public class JavaFxArtifactPropertiesEditor extends ArtifactPropertiesEditor {
  private final JavaFxArtifactProperties myProperties;

  private JPanel myWholePanel;
  private JTextField myTitleTF;
  private JTextField myVendorTF;
  private JEditorPane myDescriptionEditorPane;
  private TextFieldWithBrowseButton myAppClass;
  private JTextField myWidthTF;
  private JTextField myHeightTF;
  private TextFieldWithBrowseButton myHtmlParams;
  private TextFieldWithBrowseButton myParams;
  private JCheckBox myUpdateInBackgroundCB;
  private JCheckBox myEnableSigningCB;
  private JButton myEditSignCertificateButton;
  private JavaFxEditCertificatesDialog myDialog;

  public JavaFxArtifactPropertiesEditor(JavaFxArtifactProperties properties, final Project project, Artifact artifact) {
    super();
    myProperties = properties;
    new JavaFxApplicationClassBrowser(project, artifact).setField(myAppClass);
    final FileChooserDescriptor descriptor = FileChooserDescriptorFactory.createSingleFileDescriptor(StdFileTypes.PROPERTIES);
    myHtmlParams.addBrowseFolderListener("Choose Properties File", "Parameters for the resulting application to run standalone.", project, descriptor);
    myParams.addBrowseFolderListener("Choose Properties File", "Parameters for the resulting application to run in the browser.", project, descriptor);
    myEditSignCertificateButton.addActionListener(new ActionListener() {
      @Override
      public void actionPerformed(ActionEvent e) {
        myDialog = new JavaFxEditCertificatesDialog(myWholePanel, myProperties, project);
        myDialog.show();
      }
    });
    myEnableSigningCB.addActionListener(new ActionListener() {
      @Override
      public void actionPerformed(ActionEvent e) {
        myEditSignCertificateButton.setEnabled(myEnableSigningCB.isSelected());
      }
    });
  }

  @Override
  public String getTabName() {
    return "Java FX";
  }

  @Nullable
  @Override
  public JComponent createComponent() {
    return myWholePanel;
  }

  @Override
  public boolean isModified() {
    if (isModified(myProperties.getTitle(), myTitleTF)) return true;
    if (isModified(myProperties.getVendor(), myVendorTF)) return true;
    if (isModified(myProperties.getDescription(), myDescriptionEditorPane)) return true;
    if (isModified(myProperties.getWidth(), myWidthTF)) return true;
    if (isModified(myProperties.getHeight(), myHeightTF)) return true;
    if (isModified(myProperties.getAppClass(), myAppClass)) return true;
    if (isModified(myProperties.getHtmlParamFile(), myHtmlParams)) return true;
    if (isModified(myProperties.getParamFile(), myParams)) return true;
    final boolean inBackground = Comparing.strEqual(myProperties.getUpdateMode(), JavaFxPackagerConstants.UPDATE_MODE_BACKGROUND);
    if (inBackground != myUpdateInBackgroundCB.isSelected()) return true;
    if (myProperties.isEnabledSigning() != myEnableSigningCB.isSelected()) return true;
    if (myDialog != null) {
      if (isModified(myProperties.getAlias(), myDialog.myPanel.myAliasTF)) return true;
      if (isModified(myProperties.getKeystore(), myDialog.myPanel.myKeystore)) return true;
      if (isModified(myProperties.getKeypass(), myDialog.myPanel.myKeypassTF)) return true;
      if (isModified(myProperties.getStorepass(), myDialog.myPanel.myStorePassTF)) return true;
      if (myProperties.isSelfSigning() != myDialog.myPanel.mySelfSignedRadioButton.isSelected()) return true;
    }
    return false;
  }

  private static boolean isModified(final String title, JTextComponent tf) {
    return !Comparing.strEqual(title, tf.getText().trim());
  }
  
  private static boolean isModified(final String title, TextFieldWithBrowseButton tf) {
    return !Comparing.strEqual(title, tf.getText().trim());
  }

  @Override
  public void apply() {
    myProperties.setTitle(myTitleTF.getText());
    myProperties.setVendor(myVendorTF.getText());
    myProperties.setDescription(myDescriptionEditorPane.getText());
    myProperties.setAppClass(myAppClass.getText());
    myProperties.setWidth(myWidthTF.getText());
    myProperties.setHeight(myHeightTF.getText());
    myProperties.setHtmlParamFile(myHtmlParams.getText());
    myProperties.setParamFile(myParams.getText());
    myProperties.setUpdateMode(myUpdateInBackgroundCB.isSelected() ? JavaFxPackagerConstants.UPDATE_MODE_BACKGROUND 
                                                                   : JavaFxPackagerConstants.UPDATE_MODE_ALWAYS);
    myProperties.setEnabledSigning(myEnableSigningCB.isSelected());
    if (myDialog != null) {
      myProperties.setSelfSigning(myDialog.myPanel.mySelfSignedRadioButton.isSelected());
      myProperties.setAlias(myDialog.myPanel.myAliasTF.getText());
      myProperties.setKeystore(myDialog.myPanel.myKeystore.getText());
      myProperties.setKeypass(myDialog.myPanel.myKeypassTF.getText());
      myProperties.setStorepass(myDialog.myPanel.myStorePassTF.getText());
    }
  }

  @Override
  public void reset() {
    setText(myTitleTF, myProperties.getTitle());
    setText(myVendorTF, myProperties.getVendor());
    setText(myDescriptionEditorPane, myProperties.getDescription());
    setText(myWidthTF, myProperties.getWidth());
    setText(myHeightTF, myProperties.getHeight());
    setText(myAppClass, myProperties.getAppClass());
    setText(myHtmlParams, myProperties.getHtmlParamFile());
    setText(myParams, myProperties.getParamFile());
    myUpdateInBackgroundCB.setSelected(Comparing.strEqual(myProperties.getUpdateMode(), JavaFxPackagerConstants.UPDATE_MODE_BACKGROUND));
    myEnableSigningCB.setSelected(myProperties.isEnabledSigning());
    myEditSignCertificateButton.setEnabled(myProperties.isEnabledSigning());
  }

  private static void setText(TextFieldWithBrowseButton tf, final String title) {
    if (title != null) {
      tf.setText(title.trim());
    }
  }

  private static void setText(JTextComponent tf, final String title) {
    if (title != null) {
      tf.setText(title.trim());
    }
  }

  @Override
  public void disposeUIResources() {
    if (myDialog != null) {
      myDialog.myPanel = null;
    }
  }

  private static class JavaFxApplicationClassBrowser extends ClassBrowser {

    private final Artifact myArtifact;

    public JavaFxApplicationClassBrowser(Project project, Artifact artifact) {
      super(project, "Choose Application Class");
      myArtifact = artifact;
    }

    @Override
    protected ClassFilter.ClassFilterWithScope getFilter() throws NoFilterException {
      return new ClassFilter.ClassFilterWithScope() {
        @Override
        public GlobalSearchScope getScope() {
          return GlobalSearchScope.projectScope(getProject());
        }

        @Override
        public boolean isAccepted(PsiClass aClass) {
          return InheritanceUtil.isInheritor(aClass, "javafx.application.Application");
        }
      };
    }

    @Override
    protected PsiClass findClass(String className) {
      final Set<Module> modules = ApplicationManager.getApplication().runReadAction(new Computable<Set<Module>>() {
        @Override
        public Set<Module> compute() {
          return ArtifactUtil.getModulesIncludedInArtifacts(Collections.singletonList(myArtifact), getProject());
        }
      });
      for (Module module : modules) {
        final PsiClass aClass = JavaExecutionUtil.findMainClass(getProject(), className, GlobalSearchScope.moduleScope(module));
        if (aClass != null) {
          return aClass;
        }
      }
      return null;
    }
  }
}
