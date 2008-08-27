package org.intellij.images.thumbnail.impl;

import com.intellij.ide.SelectInContext;
import com.intellij.ide.SelectInTarget;
import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.vfs.VirtualFile;
import org.intellij.images.fileTypes.ImageFileTypeManager;
import org.intellij.images.thumbnail.ThumbnailManager;
import org.intellij.images.thumbnail.ThumbnailView;

final class ThumbnailSelectInTarget implements SelectInTarget {
  public ThumbnailSelectInTarget() {
  }

  public boolean canSelect(SelectInContext context) {
    VirtualFile virtualFile = context.getVirtualFile();
    return ImageFileTypeManager.getInstance().isImage(virtualFile) && virtualFile.getParent() != null;
  }

  public void selectIn(SelectInContext context, final boolean requestFocus) {
    VirtualFile virtualFile = context.getVirtualFile();
    VirtualFile parent = virtualFile.getParent();
    if (parent != null) {
      ThumbnailView thumbnailView = ServiceManager.getService(context.getProject(), ThumbnailManager.class).getThumbnailView();
      thumbnailView.setRoot(parent);
      thumbnailView.setVisible(true);
      thumbnailView.setSelected(virtualFile, true);
      thumbnailView.scrollToSelection();
    }
  }

  public String toString() {
    return getToolWindowId();
  }

  public String getToolWindowId() {
    return ThumbnailView.TOOLWINDOW_ID;
  }

  public String getMinorViewId() {
    return null;
  }

  public float getWeight() {
    return 10;
  }
}
