// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.idea.devkit.util;

import com.intellij.openapi.actionSystem.ActionGroup;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.keymap.KeymapManager;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.JavaPsiFacade;
import com.intellij.psi.PsiClass;
import com.intellij.psi.xml.XmlFile;
import com.intellij.psi.xml.XmlTag;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.idea.devkit.dom.IdeaPlugin;

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
    final PsiClass psiClass = JavaPsiFacade.getInstance(klass.getProject()).findClass(myClassName, klass.getResolveScope());
    return psiClass != null && klass.isInheritor(psiClass, true);
  }

  public void process(XmlTag rootTag, Processor processor) {
    for (XmlTag actionsTag : rootTag.findSubTags("actions")) {
      if (!actionsTag.isPhysical()) continue;

      final XmlTag[] actionOrGroupTags = actionsTag.getSubTags();
      processRecursively(processor, actionOrGroupTags);
    }
  }

  private boolean processRecursively(Processor processor, XmlTag[] tags) {
    for (XmlTag tag : tags) {
      if (myName.equals(tag.getName())) {
        if (!processor.process(this, tag)) {
          return false;
        }
      }

      if (GROUP.myName.equals(tag.getName())) {
        if (this == ACTION && !processRecursively(processor, tag.findSubTags(ACTION.myName))) return false;

        if (!processRecursively(processor, tag.findSubTags(GROUP.myName))) return false;
      }
    }
    return true;
  }

  public void patchPluginXml(XmlFile pluginXml, PsiClass klass, ActionData dialog) throws IncorrectOperationException {
    final XmlTag rootTag = pluginXml.getDocument().getRootTag();
    if (rootTag != null && IdeaPlugin.TAG_NAME.equals(rootTag.getName())) {
      XmlTag actions = rootTag.findFirstSubTag("actions");
      if (actions == null || !actions.isPhysical()) {
        actions = (XmlTag)rootTag.add(rootTag.createChildTag("actions", rootTag.getNamespace(), null, false));
      }

      XmlTag actionTag = (XmlTag)actions.add(actions.createChildTag(myName, actions.getNamespace(), null, false));
      actionTag.setAttribute("id", dialog.getActionId());
      actionTag.setAttribute("class", klass.getQualifiedName());
      actionTag.setAttribute("text", StringUtil.escapeXmlEntities(dialog.getActionText()));
      String description = dialog.getActionDescription();
      if (description != null && !description.isEmpty()) {
        actionTag.setAttribute("description", StringUtil.escapeXmlEntities(description));
      }

      String groupId = dialog.getSelectedGroupId();
      if (groupId != null) {
        XmlTag groupTag = (XmlTag)actionTag.add(actionTag.createChildTag("add-to-group", actions.getNamespace(), null, false));
        groupTag.setAttribute("group-id", groupId);
        final @NonNls String anchor = dialog.getSelectedAnchor();
        groupTag.setAttribute("anchor", anchor);
        if (anchor.equals("before") || anchor.equals("after")) {
          groupTag.setAttribute("relative-to-action", dialog.getSelectedActionId());
        }
      }

      String firstKeyStroke = dialog.getFirstKeyStroke();
      if (firstKeyStroke != null && !firstKeyStroke.isEmpty()) {
        XmlTag keyTag = (XmlTag)actionTag.add(actionTag.createChildTag("keyboard-shortcut", actions.getNamespace(), null, false));
        keyTag.setAttribute("keymap", KeymapManager.DEFAULT_IDEA_KEYMAP);
        keyTag.setAttribute("first-keystroke", firstKeyStroke);
        final String secondKeyStroke = dialog.getSecondKeyStroke();
        if (secondKeyStroke != null && !secondKeyStroke.isEmpty()) {
          keyTag.setAttribute("second-keystroke", secondKeyStroke);
        }
      }
    }
  }
}
