package org.jetbrains.plugins.gradle.action;

import com.intellij.codeInsight.hint.HintManager;
import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.popup.Balloon;
import com.intellij.openapi.ui.popup.JBPopupFactory;
import com.intellij.ui.awt.RelativePoint;
import com.intellij.ui.treeStructure.Tree;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.gradle.sync.conflict.GradleConflictControlFactory;
import org.jetbrains.plugins.gradle.ui.GradleProjectStructureNode;
import org.jetbrains.plugins.gradle.util.GradleBundle;
import org.jetbrains.plugins.gradle.util.GradleProjectStructureContext;
import org.jetbrains.plugins.gradle.util.GradleUtil;

import javax.swing.*;
import java.awt.*;
import java.util.Collection;

/**
 * There is a possible case that particular project structure entity has conflicting setup at the gradle and intellij
 * (e.g. particular library contains one set of binaries at the gradle and another one at the intellij). We want to
 * show the conflict and allow to resolve it then. This action is a trigger mechanism for that.
 * <p/>
 * Not thread-safe.
 * 
 * @author Denis Zhdanov
 * @since 3/2/12 2:43 PM
 */
public class GradleShowConflictDetailsAction extends AbstractGradleSyncTreeNodeAction {

  public GradleShowConflictDetailsAction() {
    getTemplatePresentation().setText(GradleBundle.message("gradle.action.show.conflict.text"));
    getTemplatePresentation().setDescription(GradleBundle.message("gradle.action.show.conflict.description"));
  }

  @Override
  protected void filterNodes(@NotNull Collection<GradleProjectStructureNode<?>> nodes) {
    if (nodes.size() != 1) {
      // Don't provide details if more than one node is selected
      nodes.clear();
      return;
    }
    final GradleProjectStructureNode<?> node = nodes.iterator().next();
    if (node.getConflictChanges().isEmpty()) {
      nodes.clear();
    }
  }

  @Override
  protected void doActionPerformed(@NotNull Collection<GradleProjectStructureNode<?>> nodes, @NotNull Project project, @NotNull Tree tree) {
    assert nodes.size() == 1;
    final GradleProjectStructureNode<?> node = nodes.iterator().next();
    final GradleProjectStructureContext context = project.getComponent(GradleProjectStructureContext.class);
    final Object entity = node.getDescriptor().getElement().mapToEntity(context);
    if (entity == null) {
      return;
    }
    
    final HintManager hintManager = HintManager.getInstance();
    hintManager.hideAllHints();

    final GradleConflictControlFactory factory = ServiceManager.getService(GradleConflictControlFactory.class);
    JComponent control = factory.getDiffControl(entity, node.getConflictChanges());
    if (control == null) {
      return;
    }
    final Point hintPosition = GradleUtil.getHintPosition(node, tree);
    final Balloon balloon = JBPopupFactory.getInstance().createBalloonBuilder(control).setFillColor(tree.getBackground()).createBalloon();
    balloon.show(new RelativePoint(tree, hintPosition), Balloon.Position.below);
  }
}
