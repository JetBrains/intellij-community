/*
 * Copyright (c) 2005 JetBrains s.r.o. All Rights Reserved.
 */
package org.jetbrains.idea.devkit.actions;

import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiDirectory;
import com.intellij.psi.xml.XmlFile;
import com.intellij.psi.xml.XmlTag;
import com.intellij.util.IncorrectOperationException;

/**
 * @author max
 */
public class NewApplicationComponentAction extends GenerateClassAndPatchPluginXmlActionBase {
  /**
   *.
   */
  public NewApplicationComponentAction() {
    super("Application Component", "Create New Application Component", null);
  }

  protected void patchPluginXml(final XmlFile pluginXml, final PsiClass klass) throws IncorrectOperationException {
    final XmlTag rootTag = pluginXml.getDocument().getRootTag();
    if (rootTag != null && "idea-plugin".equals(rootTag.getName())) {
      XmlTag projectComponents = rootTag.findFirstSubTag("application-components");
      if (projectComponents == null) {
        projectComponents = (XmlTag)rootTag.add(rootTag.createChildTag("application-components", rootTag.getNamespace(), null, false));
      }

      XmlTag cmp = (XmlTag)projectComponents.add(projectComponents.createChildTag("component", projectComponents.getNamespace(), null, false));
      cmp.add(cmp.createChildTag("implementation-class", cmp.getNamespace(), klass.getQualifiedName(), false));
    }
  }

  protected String getErrorTitle() {
    return "Cannot create application component";
  }

  protected String getCommandName() {
    return "Create Application Component";
  }

  protected String getClassNamePromptTitle() {
    return "New Application Component";
  }

  protected String getClassTemplateName() {
    return "ApplicationComponent.java";
  }

  protected String getClassNamePrompt() {
    return "Enter new application component name:";
  }

  protected String getActionName(PsiDirectory directory, String newName) {
    return "Creating new application component: " + directory + "." + newName;
  }
}
