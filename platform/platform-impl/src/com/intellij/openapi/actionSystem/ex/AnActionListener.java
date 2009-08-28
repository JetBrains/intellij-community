/*
 * Created by IntelliJ IDEA.
 * User: max
 * Date: May 15, 2002
 * Time: 9:58:27 PM
 * To change template for new class use 
 * Code Style | Class Templates options (Tools | IDE Options).
 */
package com.intellij.openapi.actionSystem.ex;

import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.actionSystem.AnActionEvent;

public interface AnActionListener {
  void beforeActionPerformed(AnAction action, DataContext dataContext, AnActionEvent event);
  void afterActionPerformed(AnAction action, DataContext dataContext, AnActionEvent event);
  
  void beforeEditorTyping(char c, DataContext dataContext);
}
