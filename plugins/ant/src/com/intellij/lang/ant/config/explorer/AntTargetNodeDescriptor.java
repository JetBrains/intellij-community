package com.intellij.lang.ant.config.explorer;

import com.intellij.ide.util.treeView.NodeDescriptor;
import com.intellij.lang.ant.config.*;
import com.intellij.lang.ant.config.impl.ExecuteCompositeTargetEvent;
import com.intellij.lang.ant.config.impl.MetaTarget;
import com.intellij.openapi.actionSystem.Shortcut;
import com.intellij.openapi.editor.markup.EffectType;
import com.intellij.openapi.editor.markup.TextAttributes;
import com.intellij.openapi.keymap.Keymap;
import com.intellij.openapi.keymap.KeymapManager;
import com.intellij.openapi.keymap.KeymapUtil;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ui.util.CellAppearance;
import com.intellij.openapi.roots.ui.util.CompositeAppearance;
import com.intellij.openapi.util.Comparing;
import com.intellij.ui.SimpleColoredComponent;
import com.intellij.ui.SimpleTextAttributes;
import com.intellij.util.Icons;

import java.awt.*;
import java.util.ArrayList;

final class AntTargetNodeDescriptor extends AntNodeDescriptor {
  private static final TextAttributes ourPostfixAttributes = new TextAttributes(new Color(128, 0, 0), null, null, EffectType.BOXED, Font.PLAIN);

  private final AntBuildTargetBase myTarget;
  private CompositeAppearance myHighlightedText;

  public AntTargetNodeDescriptor(final Project project, final NodeDescriptor parentDescriptor, final AntBuildTargetBase target) {
    super(project, parentDescriptor);
    myTarget = target;
    myHighlightedText = new CompositeAppearance();
  }

  public Object getElement() {
    return myTarget;
  }

  public AntBuildTargetBase getTarget() {
    return myTarget;
  }

  public boolean update() {
    final CompositeAppearance oldText = myHighlightedText;
    final boolean isMeta = myTarget instanceof MetaTarget;

    myOpenIcon = myClosedIcon = isMeta ? Icons.ANT_META_TARGET_ICON : Icons.ANT_TARGET_ICON;

    myHighlightedText = new CompositeAppearance();

    final AntBuildFile buildFile = isMeta ? ((MetaTarget)myTarget).getBuildFile() : myTarget.getModel().getBuildFile();
    final Color color = buildFile.isTargetVisible(myTarget) ? Color.black : Color.gray;
    TextAttributes nameAttributes = new TextAttributes(color, null, null, EffectType.BOXED, myTarget.isDefault() ? Font.BOLD : Font.PLAIN);

    myHighlightedText.getEnding().addText(myTarget.getName(), nameAttributes);

    AntConfigurationBase antConfiguration = AntConfigurationBase.getInstance(myProject);
    final ArrayList<String> addedNames = new ArrayList<String>(4);
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
    Keymap activeKeymap = KeymapManager.getInstance().getActiveKeymap();
    Shortcut[] shortcuts = activeKeymap.getShortcuts(actionId);
    if (shortcuts != null && shortcuts.length > 0) {
      appearance.getEnding().addText(" (" + KeymapUtil.getShortcutText(shortcuts[0]) + ")", SimpleTextAttributes.GRAY_ATTRIBUTES);
      return true;
    } else return false;
  }

  public CellAppearance getHighlightedText() {
    return myHighlightedText;
  }

  public boolean isAutoExpand() {
    return false;
  }

  public void customize(SimpleColoredComponent component) {
    getHighlightedText().customize(component);
    component.setIcon(getOpenIcon());
    String toolTipText = getTarget().getNotEmptyDescription();
    component.setToolTipText(toolTipText);
  }
}