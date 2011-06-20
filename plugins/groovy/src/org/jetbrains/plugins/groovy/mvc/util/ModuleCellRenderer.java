package org.jetbrains.plugins.groovy.mvc.util;

import com.intellij.openapi.module.Module;

import javax.swing.*;
import java.awt.*;

/**
 * @author Sergey Evdokimov
 */
public class ModuleCellRenderer extends DefaultListCellRenderer {
  public Component getListCellRendererComponent(JList list, final Object value, int index, boolean isSelected, boolean cellHasFocus) {
    super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);
    final Module module = (Module)value;
    if (module != null) {
      setIcon(module.getModuleType().getNodeIcon(false));
      setText(module.getName());
    }
    return this;
  }
}
