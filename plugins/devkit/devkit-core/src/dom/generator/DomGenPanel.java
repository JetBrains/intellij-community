// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.idea.devkit.dom.generator;

import com.intellij.CommonBundle;
import com.intellij.ide.util.PropertiesComponent;
import com.intellij.openapi.fileChooser.FileChooserDescriptor;
import com.intellij.openapi.fileChooser.FileChooserDescriptorFactory;
import com.intellij.openapi.fileChooser.FileTypeDescriptor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.ui.TextFieldWithBrowseButton;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.wm.IdeFocusManager;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiManager;
import com.intellij.psi.xml.XmlAttribute;
import com.intellij.psi.xml.XmlDocument;
import com.intellij.psi.xml.XmlFile;
import com.intellij.psi.xml.XmlTag;
import com.intellij.ui.components.JBCheckBox;
import com.intellij.util.ArrayUtilRt;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.idea.devkit.DevKitBundle;

import javax.swing.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.File;
import java.util.ArrayList;

/**
 * @author Konstantin Bulenkov
 */
public class DomGenPanel {
  private JPanel mainPanel;
  private JTextField myNamespace;
  private JTextField mySuperClass;
  private TextFieldWithBrowseButton mySchemaLocation;
  private JTextField myPackage;
  private TextFieldWithBrowseButton myOutputDir;
  private JTextArea mySkipSchemas;
  private JTextField myAuthor;
  private JBCheckBox myUseQualifiedClassNames;
  private final Project myProject;

  public DomGenPanel(Project project) {
    myProject = project;
  }

  private void createUIComponents() {
    mySchemaLocation = new TextFieldWithBrowseButton();
    mySchemaLocation.addBrowseFolderListener(DevKitBundle.message("dom.generator.dialog.choose.scheme.label"),
                                             DevKitBundle.message("dom.generator.dialog.folder.browser.description"),
                                             myProject,
                                             new FileTypeDescriptor(DevKitBundle.message("dom.generator.dialog.choose.scheme.label"), "xsd",
                                                                    "dtd"));
    mySchemaLocation.getTextField().setEditable(false);
    mySchemaLocation.addActionListener(new ActionListener() {
      @Override
      public void actionPerformed(ActionEvent e) {
        final File file = new File(mySchemaLocation.getText());
        if (file.exists() && StringUtil.toLowerCase(file.getName()).endsWith(".xsd")) {
          final VirtualFile vf = LocalFileSystem.getInstance().findFileByIoFile(file);
          if (vf != null) {
            final PsiFile psiFile = PsiManager.getInstance(myProject).findFile(vf);
            if (psiFile instanceof XmlFile) {
              final XmlDocument xml = ((XmlFile)psiFile).getDocument();
              if (xml != null) {
                final XmlTag rootTag = xml.getRootTag();
                if (rootTag != null) {
                  String target = null;
                  ArrayList<String> ns = new ArrayList<>();
                  for (XmlAttribute attr : rootTag.getAttributes()) {
                    if ("targetNamespace".equals(attr.getName())) {
                      target = attr.getValue();
                    }
                    else if (attr.getName().startsWith("xmlns")) {
                      ns.add(attr.getValue());
                    }
                  }

                  ns.remove(target);
                  if (target != null) {
                    myNamespace.setText(target);
                  }
                  mySkipSchemas.setText(StringUtil.join(ArrayUtilRt.toStringArray(ns), "\n"));
                }
              }
            }
          }
        }
      }
    });
    myOutputDir = new TextFieldWithBrowseButton();
    FileChooserDescriptor descriptor = FileChooserDescriptorFactory.createSingleFolderDescriptor();
    myOutputDir.addBrowseFolderListener(DevKitBundle.message("dom.generator.dialog.folder.output"), "", myProject, descriptor);
  }

  public JComponent getComponent() {
    return mainPanel;
  }

  public NamespaceDesc getNamespaceDescriptor() {
    return new NamespaceDesc(myNamespace.getText().trim(), myPackage.getText().trim(), mySuperClass.getText().trim(), "", null, null, null,
                             null);
  }

  public String getLocation() {
    return mySchemaLocation.getText();
  }

  public String getOutputDir() {
    return myOutputDir.getText();
  }

  private static final @NonNls String PREFIX = "DomGenPanel.";

  public void restore() {
    myNamespace.setText(getValue("namespace", ""));
    myPackage.setText(getValue("package", "com.intellij.myframework.model"));
    mySchemaLocation.setText(getValue("schemaLocation", ""));
    mySuperClass.setText(getValue("superClass", "com.intellij.util.xml.DomElement"));
    myOutputDir.setText(getValue("output", ""));
    mySkipSchemas.setText(getValue("skipSchemas", "http://www.w3.org/2001/XMLSchema\nhttp://www.w3.org/2001/XMLSchema-instance"));
    myAuthor.setText(getValue("author", ""));
    myUseQualifiedClassNames.setSelected(getValue("useFQNs", "false").equals("true"));
  }

  private static @NonNls String getValue(@NonNls String name, @NonNls String defaultValue) {
    return PropertiesComponent.getInstance().getValue(PREFIX + name, defaultValue);
  }

  private static void setValue(@NonNls String name, @NonNls String value) {
    PropertiesComponent.getInstance().setValue(PREFIX + name, value);
  }

  public void saveAll() {
    setValue("namespace", myNamespace.getText());
    setValue("package", myPackage.getText());
    setValue("schemaLocation", mySchemaLocation.getText());
    setValue("superClass", mySuperClass.getText());
    setValue("output", myOutputDir.getText());
    setValue("skipSchemas", mySkipSchemas.getText());
    setValue("author", myAuthor.getText());
    setValue("useFQNs", myUseQualifiedClassNames.isSelected() ? "true" : "false");
  }

  public boolean validate() {
    if (!new File(mySchemaLocation.getText()).exists()) {
      Messages.showErrorDialog(myProject, DevKitBundle.message("dom.generator.dialog.error.schema.not.exist"),
                               CommonBundle.getErrorTitle());
      IdeFocusManager.getInstance(myProject).requestFocus(mySchemaLocation, true);
      return false;
    }

    if (!new File(myOutputDir.getText()).exists()) {
      Messages.showErrorDialog(myProject, DevKitBundle.message("dom.generator.dialog.error.output.not.exist"),
                         CommonBundle.getErrorTitle());
      IdeFocusManager.getInstance(myProject).requestFocus(myOutputDir, true);
      return false;
    }

    return true;
  }

  public String[] getSkippedSchemas() {
    final String schemes = mySkipSchemas.getText().replaceAll("\r\n", "\n").trim();
    if (schemes.length() > 0) {
      return schemes.split("\n");
    }
    return ArrayUtilRt.EMPTY_STRING_ARRAY;
  }

  public String getAuthor() {
    return myAuthor.getText();
  }

  public boolean isUseQualifiedClassNames() {
    return myUseQualifiedClassNames.isSelected();
  }
}
