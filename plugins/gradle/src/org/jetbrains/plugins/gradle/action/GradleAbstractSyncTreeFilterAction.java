package org.jetbrains.plugins.gradle.action;

import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.ToggleAction;
import com.intellij.openapi.editor.colors.EditorColorsManager;
import com.intellij.openapi.editor.colors.TextAttributesKey;
import com.intellij.openapi.options.colors.AttributesDescriptor;
import com.intellij.util.ui.ColorIcon;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.gradle.sync.GradleProjectStructureTreeModel;
import org.jetbrains.plugins.gradle.ui.GradleDataKeys;
import org.jetbrains.plugins.gradle.ui.GradleProjectStructureNode;
import org.jetbrains.plugins.gradle.ui.GradleProjectStructureNodeFilter;

import javax.swing.*;
import java.awt.*;

/**
 * @author Denis Zhdanov
 * @since 3/7/12 3:48 PM
 */
public abstract class GradleAbstractSyncTreeFilterAction extends ToggleAction {
  
  @NotNull private final MyFilter myFilter;

  protected GradleAbstractSyncTreeFilterAction(@NotNull AttributesDescriptor descriptor) {
    myFilter = new MyFilter(descriptor.getKey());
    getTemplatePresentation().setText(descriptor.getDisplayName());
    final Color color = EditorColorsManager.getInstance().getGlobalScheme().getAttributes(descriptor.getKey()).getForegroundColor();
    getTemplatePresentation().setIcon(new ColorIcon(new JLabel("").getFont().getSize(), color));
  }

  @Override
  public boolean isSelected(AnActionEvent e) {
    final GradleProjectStructureTreeModel model = GradleDataKeys.SYNC_TREE_MODEL.getData(e.getDataContext());
    if (model == null) {
      return false;
    }
    
    return model.hasFilter(myFilter);
  }

  @Override
  public void setSelected(AnActionEvent e, boolean state) {
    final GradleProjectStructureTreeModel treeModel = GradleDataKeys.SYNC_TREE_MODEL.getData(e.getDataContext());
    if (treeModel == null) {
      return;
    }
    if (state) {
      treeModel.addFilter(myFilter);
    }
    else {
      treeModel.removeFilter(myFilter);
    }
  }
  
  private static class MyFilter implements GradleProjectStructureNodeFilter {

    @NotNull private final TextAttributesKey myKey;

    MyFilter(@NotNull TextAttributesKey key) {
      myKey = key;
    }

    @Override
    public boolean isVisible(@NotNull GradleProjectStructureNode<?> node) {
      return myKey.equals(node.getDescriptor().getAttributes());
    }
  }
}
