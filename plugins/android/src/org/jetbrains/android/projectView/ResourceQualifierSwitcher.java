package org.jetbrains.android.projectView;

import com.android.resources.ResourceFolderType;
import com.intellij.ide.highlighter.XmlFileType;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.fileEditor.FileEditor;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleUtil;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.popup.JBPopupFactory;
import com.intellij.openapi.ui.popup.ListPopup;
import com.intellij.openapi.ui.popup.ListPopupStep;
import com.intellij.openapi.ui.popup.PopupStep;
import com.intellij.openapi.ui.popup.util.BaseListPopupStep;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.ui.EditorNotifications;
import com.intellij.util.containers.BidirectionalMap;
import org.jetbrains.android.facet.AndroidFacet;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.ArrayList;

/**
 * @author yole
 */
public class ResourceQualifierSwitcher extends EditorNotifications.Provider<ResourceQualifierSwitcher.ResourceQualifierSwitcherPanel> {
  private static final Key<ResourceQualifierSwitcherPanel> KEY = Key.create("android.projectView.QualifierSwitcherPanel");
  private final Project myProject;

  public ResourceQualifierSwitcher(Project project) {
    myProject = project;
  }

  @Override
  public Key<ResourceQualifierSwitcherPanel> getKey() {
    return KEY;
  }

  @Nullable
  @Override
  public ResourceQualifierSwitcherPanel createNotificationPanel(VirtualFile file, FileEditor fileEditor) {
    if (!ApplicationManager.getApplication().isInternal()) {
      return null;
    }
    if (file.getFileType() != XmlFileType.INSTANCE) {
      return null;
    }
    VirtualFile parent = file.getParent();
    if (parent == null) {
      return null;
    }
    parent = parent.getParent();
    if (parent == null || !parent.getName().equals("res")) {
      return null;
    }
    Module module = ModuleUtil.findModuleForFile(file, myProject);
    AndroidFacet facet = module == null ? null : AndroidFacet.getInstance(module);
    if (facet == null) {
      return null;
    }
    BidirectionalMap<String, VirtualFile> qualifiers = collectQualifiers(parent, file);
    if (qualifiers.size() <= 1) {
      return null;
    }
    return new ResourceQualifierSwitcherPanel(myProject, file, qualifiers);
  }

  private static BidirectionalMap<String, VirtualFile> collectQualifiers(VirtualFile resDirectory, VirtualFile file) {
    BidirectionalMap<String, VirtualFile> result = new BidirectionalMap<String, VirtualFile>();
    ResourceFolderType type = ResourceFolderType.getFolderType(file.getParent().getName());
    for (VirtualFile dir : resDirectory.getChildren()) {
      ResourceFolderType otherType = ResourceFolderType.getFolderType(dir.getName());
      if (otherType == type) {
        VirtualFile fileWithQualifier = dir.findChild(file.getName());
        if (fileWithQualifier != null) {
          String childName = dir.getName();
          int dashPos = childName.indexOf('-');
          String qualifier = dashPos > 0 ? childName.substring(dashPos+1) : "<default>";
          result.put(qualifier, fileWithQualifier);
        }
      }
    }
    return result;
  }

  public static class ResourceQualifierSwitcherPanel extends JPanel {
    private final Project myProject;
    private final VirtualFile myFile;
    private final BidirectionalMap<String, VirtualFile> myQualifiers;

    public ResourceQualifierSwitcherPanel(final Project project, final VirtualFile file, BidirectionalMap<String, VirtualFile> qualifiers) {
      super(new BorderLayout());
      myProject = project;
      myFile = file;
      myQualifiers = qualifiers;

      final String currentFileQualifier = qualifiers.getKeysByValue(file).get(0);
      final JLabel label = new JLabel(currentFileQualifier);
      label.addMouseListener(new MouseAdapter() {
        @Override
        public void mouseClicked(MouseEvent event) {
          if (event.getButton() != MouseEvent.BUTTON1 || event.getClickCount() != 1) {
            return;
          }
          BidirectionalMap<String, VirtualFile> map = collectQualifiers(file.getParent().getParent(), file);
          ListPopupStep popupStep = new BaseListPopupStep<String>("Choose Qualifier", new ArrayList<String>(map.keySet())) {
            @Override
            public PopupStep onChosen(String selectedValue, boolean finalChoice) {
              switchToFile(selectedValue);
              return FINAL_CHOICE;
            }
          };
          ListPopup popup = JBPopupFactory.getInstance().createListPopup(popupStep);
          popup.showUnderneathOf(label);
        }
      });
      add(label, BorderLayout.WEST);
    }

    private void switchToFile(String qualifier) {
      VirtualFile newFile = myQualifiers.get(qualifier);
      if (newFile != myFile) {
        FileEditorManager.getInstance(myProject).openFile(newFile, true);
      }
    }
  }
}
