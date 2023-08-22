// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.lang.ant.config.explorer;

import com.intellij.execution.RunManagerEx;
import com.intellij.icons.AllIcons;
import com.intellij.ide.util.treeView.NodeDescriptor;
import com.intellij.lang.ant.AntBundle;
import com.intellij.lang.ant.config.*;
import com.intellij.lang.ant.config.impl.AntBeforeRunTask;
import com.intellij.lang.ant.config.impl.AntBeforeRunTaskProvider;
import com.intellij.lang.ant.config.impl.ExecuteCompositeTargetEvent;
import com.intellij.lang.ant.config.impl.MetaTarget;
import com.intellij.openapi.actionSystem.Shortcut;
import com.intellij.openapi.editor.markup.EffectType;
import com.intellij.openapi.editor.markup.TextAttributes;
import com.intellij.openapi.keymap.KeymapUtil;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ui.CellAppearanceEx;
import com.intellij.openapi.roots.ui.util.CompositeAppearance;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.ui.JBColor;
import com.intellij.ui.SimpleColoredComponent;
import com.intellij.ui.SimpleTextAttributes;
import com.intellij.util.ui.UIUtil;
import icons.AntIcons;
import org.jetbrains.annotations.NotNull;

import java.awt.*;
import java.util.ArrayList;

final class AntTargetNodeDescriptor extends AntNodeDescriptor {
  private static final TextAttributes ourPostfixAttributes = new TextAttributes(new JBColor(new Color(128, 0, 0), JBColor.RED), null, null, EffectType.BOXED, Font.PLAIN);

  private final AntBuildTargetBase myTarget;
  private CompositeAppearance myHighlightedText;

  AntTargetNodeDescriptor(final Project project, final NodeDescriptor parentDescriptor, final AntBuildTargetBase target) {
    super(project, parentDescriptor);
    myTarget = target;
    myHighlightedText = new CompositeAppearance();
  }

  @Override
  public Object getElement() {
    return myTarget;
  }

  public AntBuildTargetBase getTarget() {
    return myTarget;
  }

  @Override
  public boolean update() {
    final CompositeAppearance oldText = myHighlightedText;
    final boolean isMeta = myTarget instanceof MetaTarget;

    setIcon(isMeta ? AntIcons.MetaTarget : AllIcons.Nodes.Target);

    myHighlightedText = new CompositeAppearance();

    final AntBuildFile buildFile = isMeta ? ((MetaTarget)myTarget).getBuildFile() : myTarget.getModel().getBuildFile();
    final Color color = buildFile.isTargetVisible(myTarget) ? UIUtil.getLabelForeground() : UIUtil.getLabelDisabledForeground();
    TextAttributes nameAttributes = new TextAttributes(color, null, null, EffectType.BOXED, myTarget.isDefault() ? Font.BOLD : Font.PLAIN);

    myHighlightedText.getEnding().addText(myTarget.getDisplayName(), nameAttributes);

    AntConfigurationBase antConfiguration = AntConfigurationBase.getInstance(myProject);
    final ArrayList<String> addedNames = new ArrayList<>(4);
    for (final ExecutionEvent event : antConfiguration.getEventsForTarget(myTarget)) {
      final String presentableName;
      if ((event instanceof ExecuteCompositeTargetEvent)) {
        presentableName = ((ExecuteCompositeTargetEvent)event).getMetaTargetName();
        if (presentableName.equals(myTarget.getName())) {
          continue;
        }
      }
      else {
        presentableName = event.getPresentableName();
      }
      if (!addedNames.contains(presentableName)) {
        addedNames.add(presentableName);
        myHighlightedText.getEnding().addText(" (" + presentableName + ')', ourPostfixAttributes);
      }
    }
    final VirtualFile vFile = buildFile.getVirtualFile();
    if (vFile != null) {
      for (AntBeforeRunTask task : RunManagerEx.getInstanceEx(myProject).getBeforeRunTasks(AntBeforeRunTaskProvider.ID)) {
        if (task.isRunningTarget(myTarget)) {
          myHighlightedText.getEnding().addText(AntBundle.message("ant.target.node.before.run.debug"), ourPostfixAttributes);
          break;
        }
      }
    }
    myName = myHighlightedText.getText();

    final AntBuildTargetBase target = getTarget();
    if (!addShortcutText(target.getActionId())) {
      if (target.isDefault()) {
        addShortcutText(((AntBuildModelBase)target.getModel()).getDefaultTargetActionId());
      }
    }

    return !Comparing.equal(myHighlightedText, oldText);
  }

  private boolean addShortcutText(String actionId) {
    return addShortcutText(actionId, myHighlightedText);
  }

  public static boolean addShortcutText(String actionId, CompositeAppearance appearance) {
    Shortcut shortcut = KeymapUtil.getPrimaryShortcut(actionId);
    if (shortcut != null) {
      appearance.getEnding().addText(" (" + KeymapUtil.getShortcutText(shortcut) + ")", SimpleTextAttributes.GRAY_ATTRIBUTES);
      return true;
    } else return false;
  }

  public CellAppearanceEx getHighlightedText() {
    return myHighlightedText;
  }

  @Override
  public void customize(@NotNull SimpleColoredComponent component) {
    getHighlightedText().customize(component);
    component.setIcon(getIcon());
    String toolTipText = getTarget().getNotEmptyDescription();
    component.setToolTipText(toolTipText);
  }
}