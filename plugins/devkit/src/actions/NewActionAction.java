/*
 * Copyright 2000-2005 JetBrains s.r.o.
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
package org.jetbrains.idea.devkit.actions;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.keymap.KeymapManager;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiDirectory;
import com.intellij.psi.PsiElement;
import com.intellij.psi.xml.XmlFile;
import com.intellij.psi.xml.XmlTag;
import com.intellij.util.IncorrectOperationException;

/**
 * @author yole
 */
public class NewActionAction extends GeneratePluginClassAction {
  private NewActionDialog myDialog;

  public NewActionAction() {
    super("Action", "Create New Action", null);
  }

  protected PsiElement[] invokeDialog(Project project, PsiDirectory directory) {
    myDialog = new NewActionDialog(project);
    myDialog.show();
    if (myDialog.getExitCode() == DialogWrapper.OK_EXIT_CODE) {
      final MyInputValidator validator = new MyInputValidator(project, directory);
      // this actually runs the action to create the class from template
      validator.canClose(myDialog.getActionName());
      myDialog = null;
      return validator.getCreatedElements();
    }
    myDialog = null;
    return new PsiElement[0];
  }

  protected void checkBeforeCreate(String newName, PsiDirectory directory) throws IncorrectOperationException {
    //To change body of implemented methods use File | Settings | File Templates.
  }

  protected String getClassTemplateName() {
    return "Action.java";
  }

  protected void patchPluginXml(final XmlFile pluginXml, final PsiClass klass) throws IncorrectOperationException {
    final XmlTag rootTag = pluginXml.getDocument().getRootTag();
    if (rootTag != null && "idea-plugin".equals(rootTag.getName())) {
      XmlTag actions = rootTag.findFirstSubTag("actions");
      if (actions == null) {
        actions = (XmlTag)rootTag.add(rootTag.createChildTag("actions", rootTag.getNamespace(), null, false));
      }

      XmlTag actionTag = (XmlTag)actions.add(actions.createChildTag("action", actions.getNamespace(), null, false));
      actionTag.setAttribute("id", myDialog.getActionId());
      actionTag.setAttribute("class", klass.getQualifiedName());
      actionTag.setAttribute("text", myDialog.getActionText());
      String description = myDialog.getActionDescription();
      if (description != null && description.length() > 0) {
        actionTag.setAttribute("description", description);
      }

      String groupId = myDialog.getSelectedGroupId();
      if (groupId != null) {
        XmlTag groupTag = (XmlTag)actionTag.add(actionTag.createChildTag("add-to-group", actions.getNamespace(), null, false));
        groupTag.setAttribute("group-id", groupId);
        final String anchor = myDialog.getSelectedAnchor();
        groupTag.setAttribute("anchor", anchor);
        if (anchor.equals("first") || anchor.equals("last")) {
          groupTag.setAttribute("relative-to-action", myDialog.getSelectedActionId());
        }
      }

      String firstKeyStroke = myDialog.getFirstKeyStroke();
      if (firstKeyStroke != null && firstKeyStroke.length() > 0) {
        XmlTag keyTag = (XmlTag)actionTag.add(actionTag.createChildTag("keyboard-shortcut", actions.getNamespace(), null, false));
        keyTag.setAttribute("keymap", KeymapManager.DEFAULT_IDEA_KEYMAP);
        keyTag.setAttribute("first-keystroke", firstKeyStroke);
        if (myDialog.getSecondKeyStroke().length() > 0) {
          keyTag.setAttribute("second-keystroke", myDialog.getSecondKeyStroke());
        }
      }
    }
  }

  protected String getErrorTitle() {
    return "Cannot create action";
  }

  protected String getCommandName() {
    return "Create Action";
  }

  protected String getActionName(PsiDirectory directory, String newName) {
    return "Creating new action: " + directory + "." + newName;
  }
}
