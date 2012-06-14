package org.jetbrains.android.util;

import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleType;
import com.intellij.ui.ListCellRendererWrapper;

import javax.swing.*;

/**
* @author Eugene.Kudelevsky
*/
public class ModuleListCellRendererWrapper extends ListCellRendererWrapper<Module> {
  public ModuleListCellRendererWrapper(ListCellRenderer renderer) {
    super(renderer);
  }

  @Override
  public void customize(JList list, Module module, int index, boolean selected, boolean hasFocus) {
    setText(module.getName());
    setIcon(ModuleType.get(module).getNodeIcon(false));
  }
}
