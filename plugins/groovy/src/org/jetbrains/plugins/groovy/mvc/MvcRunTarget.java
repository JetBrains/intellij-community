package org.jetbrains.plugins.groovy.mvc;

import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.util.Pair;
import org.jetbrains.annotations.NotNull;

/**
 * @author ilyas
 */
public class MvcRunTarget extends MvcActionBase {

  @Override
  protected void actionPerformed(@NotNull AnActionEvent e, @NotNull Module module, @NotNull MvcFramework framework) {
    MvcRunTargetDialog dialog = new MvcRunTargetDialog(module, framework);
    dialog.show();
    if (!dialog.isOK()) {
      return;
    }

    Module selectedModule = dialog.getSelectedModule();

    String[] targetArgs = dialog.getTargetArguments();

    Pair<String, String[]> parsedCmd = MvcFramework.parsedCmd(targetArgs);

    ProcessBuilder pb = framework.createCommandAndShowErrors(dialog.getVmOptions(), selectedModule, parsedCmd.first, parsedCmd.second);
    if (pb == null) return;

    MvcConsole.getInstance(selectedModule.getProject()).executeProcess(selectedModule, pb, null, false);
  }

}
