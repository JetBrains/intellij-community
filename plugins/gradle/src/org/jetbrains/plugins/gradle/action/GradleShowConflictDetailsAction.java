package org.jetbrains.plugins.gradle.action;

import com.intellij.codeInsight.hint.HintManager;
import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.externalSystem.service.project.ProjectStructureServices;
import com.intellij.openapi.externalSystem.util.ExternalSystemBundle;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.popup.Balloon;
import com.intellij.openapi.ui.popup.JBPopupAdapter;
import com.intellij.openapi.ui.popup.JBPopupFactory;
import com.intellij.openapi.ui.popup.LightweightWindowEvent;
import com.intellij.ui.awt.RelativePoint;
import com.intellij.ui.treeStructure.Tree;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.gradle.sync.conflict.GradleConflictControlFactory;
import com.intellij.openapi.externalSystem.ui.ProjectStructureNode;
import org.jetbrains.plugins.gradle.ui.GradleUiListener;
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
    // TODO den implement
//    getTemplatePresentation().setText(ExternalSystemBundle.message("gradle.action.show.conflict.text"));
//    getTemplatePresentation().setDescription(ExternalSystemBundle.message("gradle.action.show.conflict.description"));
  }

  @Override
  protected void filterNodes(@NotNull Collection<ProjectStructureNode<?>> nodes) {
    if (nodes.size() != 1) {
      // Don't provide details if more than one node is selected
      nodes.clear();
      return;
    }
    final ProjectStructureNode<?> node = nodes.iterator().next();
    if (node.getConflictChanges().isEmpty()) {
      nodes.clear();
    }
  }

  @Override
  protected void doActionPerformed(@NotNull Collection<ProjectStructureNode<?>> nodes, @NotNull final Project project, @NotNull Tree tree) {
    assert nodes.size() == 1;
    final ProjectStructureNode<?> node = nodes.iterator().next();
    final ProjectStructureServices context = ServiceManager.getService(project, ProjectStructureServices.class);
    // TODO den implement
    final Object entity = null;
//    final Object entity = node.getDescriptor().getElement().mapToEntity(context);
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
    if (hintPosition == null) {
      return;
    }
    final Balloon balloon = JBPopupFactory.getInstance().createBalloonBuilder(control).setDisposable(project)
      .setFillColor(tree.getBackground()).createBalloon();
    final GradleUiListener publisher = project.getMessageBus().syncPublisher(GradleUiListener.TOPIC);
    publisher.beforeConflictUiShown();
    balloon.addListener(new JBPopupAdapter() {
      @Override
      public void onClosed(LightweightWindowEvent event) {
        if (!project.isDisposed()) {
          publisher.afterConflictUiShown();
        }
      }
    });
    balloon.show(new RelativePoint(tree, hintPosition), Balloon.Position.below);
  }
}
