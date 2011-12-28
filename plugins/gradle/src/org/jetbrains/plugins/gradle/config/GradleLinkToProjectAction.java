package org.jetbrains.plugins.gradle.config;

import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.project.DumbAware;
import org.jetbrains.plugins.gradle.util.GradleBundle;

/**
 * Allows to link gradle project to the current IntelliJ IDEA project.
 * <p/>
 * Not thread-safe.
 * 
 * @author Denis Zhdanov
 * @since 12/26/11 5:09 PM
 */
public class GradleLinkToProjectAction extends AnAction implements DumbAware {

  public GradleLinkToProjectAction() {
    getTemplatePresentation().setText(GradleBundle.message("gradle.action.link.project.text"));
    getTemplatePresentation().setDescription(GradleBundle.message("gradle.action.link.project.description"));
  }

  @Override
  public void actionPerformed(AnActionEvent e) {
    // TODO den implement
    System.out.println("action performed");
  }
}
