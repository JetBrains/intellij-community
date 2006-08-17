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

package org.jetbrains.idea.devkit.util;

import com.intellij.openapi.actionSystem.ActionGroup;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.keymap.KeymapManager;
import com.intellij.psi.PsiClass;
import com.intellij.psi.xml.XmlFile;
import com.intellij.psi.xml.XmlTag;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.NonNls;

/**
 * @author swr
 */
public enum ActionType {
  ACTION(AnAction.class, "action"),
  GROUP(ActionGroup.class, "group");

  public final String myClassName;
  private final String myName;

  public interface Processor {
    boolean process(ActionType type, XmlTag action);
  }

  ActionType(Class<? extends AnAction> clazz, @NonNls String name) {
    myClassName = clazz.getName();
    myName = name;
  }

  public boolean isOfType(PsiClass klass) {
    final PsiClass psiClass = klass.getManager().findClass(myClassName, klass.getResolveScope());
    return psiClass != null && klass.isInheritor(psiClass, true);
  }

  public void process(XmlTag rootTag, Processor processor) {
    final XmlTag[] actions = rootTag.findSubTags("actions");
    for (XmlTag tag : actions) {
      final XmlTag[] components = tag.getSubTags();
      for (XmlTag actionOrGroup : components) {
        if (myName.equals(actionOrGroup.getName())) {
          if (!processor.process(this, actionOrGroup)) {
            return;
          }
        } else if (this == ACTION && GROUP.myName.equals(actionOrGroup.getName())) {
          final XmlTag[] groupActions = actionOrGroup.findSubTags(myName);
          for (XmlTag a : groupActions) {
            if (!processor.process(this, a)) {
              return;
            }
          }
        }
      }
    }
  }

  public void patchPluginXml(XmlFile pluginXml, PsiClass klass, ActionData dialog) throws IncorrectOperationException {
    final XmlTag rootTag = pluginXml.getDocument().getRootTag();
    if (rootTag != null && "idea-plugin".equals(rootTag.getName())) {
      XmlTag actions = rootTag.findFirstSubTag("actions");
      if (actions == null) {
        actions = (XmlTag)rootTag.add(rootTag.createChildTag("actions", rootTag.getNamespace(), null, false));
      }

      XmlTag actionTag = (XmlTag)actions.add(actions.createChildTag(myName, actions.getNamespace(), null, false));
      actionTag.setAttribute("id", dialog.getActionId());
      actionTag.setAttribute("class", klass.getQualifiedName());
      actionTag.setAttribute("text", dialog.getActionText());
      String description = dialog.getActionDescription();
      if (description != null && description.length() > 0) {
        actionTag.setAttribute("description", description);
      }

      String groupId = dialog.getSelectedGroupId();
      if (groupId != null) {
        XmlTag groupTag = (XmlTag)actionTag.add(actionTag.createChildTag("add-to-group", actions.getNamespace(), null, false));
        groupTag.setAttribute("group-id", groupId);
        @NonNls final String anchor = dialog.getSelectedAnchor();
        groupTag.setAttribute("anchor", anchor);
        if (anchor.equals("before") || anchor.equals("after")) {
          groupTag.setAttribute("relative-to-action", dialog.getSelectedActionId());
        }
      }

      String firstKeyStroke = dialog.getFirstKeyStroke();
      if (firstKeyStroke != null && firstKeyStroke.length() > 0) {
        XmlTag keyTag = (XmlTag)actionTag.add(actionTag.createChildTag("keyboard-shortcut", actions.getNamespace(), null, false));
        keyTag.setAttribute("keymap", KeymapManager.DEFAULT_IDEA_KEYMAP);
        keyTag.setAttribute("first-keystroke", firstKeyStroke);
        final String secondKeyStroke = dialog.getSecondKeyStroke();
        if (secondKeyStroke != null && secondKeyStroke.length() > 0) {
          keyTag.setAttribute("second-keystroke", secondKeyStroke);
        }
      }
    }
  }
}
