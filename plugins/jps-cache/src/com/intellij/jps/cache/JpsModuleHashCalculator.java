package com.intellij.jps.cache;

import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.project.DumbAwareAction;
import com.intellij.openapi.roots.ModuleRootManager;
import com.intellij.openapi.ui.DialogWrapper;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;

public class JpsModuleHashCalculator extends DumbAwareAction {
  @Override
  public void actionPerformed(@NotNull AnActionEvent e) {
    SimpleDialogWrapper wrapper = new SimpleDialogWrapper();
    if (wrapper.showAndGet()) {
      String text = wrapper.textField.getText();
      ModuleManager moduleManager = ModuleManager.getInstance(e.getProject());
      System.out.println(text);
      Module module = moduleManager.findModuleByName(text);
      ModuleRootManager instance = ModuleRootManager.getInstance(module);
      System.out.println("Prod: " + JpsBinaryDataSyncAction.calculateProductionSourceRootsHash(instance));
      System.out.println("Test: " + JpsBinaryDataSyncAction.calculateTestSourceRootsHash(instance));
    }
  }

  class SimpleDialogWrapper extends DialogWrapper {
    TextField textField;

    SimpleDialogWrapper() {
      super(true);
      init();
      setTitle("Module Hash");
    }

    @Nullable
    @Override
    protected JComponent createCenterPanel() {
      JPanel dialogPanel = new JPanel(new BorderLayout());
      textField = new TextField();
      textField.setPreferredSize(new Dimension(100, 100));
      dialogPanel.add(textField, BorderLayout.CENTER);
      return dialogPanel;
    }
  }
}