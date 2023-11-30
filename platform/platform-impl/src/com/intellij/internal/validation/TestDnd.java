// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.internal.validation;

import com.intellij.icons.AllIcons;
import com.intellij.ide.dnd.DnDDragStartBean;
import com.intellij.ide.dnd.DnDImage;
import com.intellij.ide.dnd.DnDSupport;
import com.intellij.openapi.actionSystem.ActionUpdateThread;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.ui.components.JBList;
import com.intellij.util.IconUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;

/**
 * @author Konstantin Bulenkov
 */
final class TestDnd extends AnAction {

  @Override
  public @NotNull ActionUpdateThread getActionUpdateThread() {
    return ActionUpdateThread.BGT;
  }

  @Override
  public void actionPerformed(@NotNull AnActionEvent e) {
    new DialogWrapper(getEventProject(e)) {
      {
        setTitle("DnD Test");
        setSize(600, 500);
        init();
      }

      @Override
      protected @Nullable JComponent createCenterPanel() {
        JBList list = new JBList(new String[]{"1111111", "222222", "333333", "44444", "555555555555555555555555"});
        DnDSupport.createBuilder(list)
          .setBeanProvider(info -> new DnDDragStartBean("something"))
          .setImageProvider(info -> new DnDImage(IconUtil.toImage(AllIcons.FileTypes.Text)))
          .install();

        return list;
      }
    }.show();
  }
}