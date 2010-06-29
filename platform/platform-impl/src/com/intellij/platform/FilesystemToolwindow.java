/*
 * Copyright 2000-2009 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

/*
 * @author max
 */
package com.intellij.platform;

import com.intellij.openapi.actionSystem.DataProvider;
import com.intellij.openapi.actionSystem.PlatformDataKeys;
import com.intellij.openapi.fileChooser.FileChooserDescriptor;
import com.intellij.openapi.fileChooser.FileSystemTree;
import com.intellij.openapi.fileChooser.ex.FileSystemTreeImpl;
import com.intellij.openapi.fileEditor.OpenFileDescriptor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.wm.ToolWindow;
import com.intellij.openapi.wm.ToolWindowAnchor;
import com.intellij.openapi.wm.ToolWindowManager;
import com.intellij.ui.ScrollPaneFactory;
import com.intellij.ui.content.Content;
import com.intellij.ui.content.ContentFactory;
import com.intellij.ui.content.ContentFactoryImpl;
import com.intellij.util.EditSourceOnDoubleClickHandler;
import com.intellij.util.EditSourceOnEnterKeyHandler;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;

public class FilesystemToolwindow {
  private final VirtualFile myRoot;
  private final Project myProject;
  private final ToolWindow myToolWindow;
  private final JPanel myContent;
  private final FileSystemTree myFsTree;

  public FilesystemToolwindow(final VirtualFile root, Project project) {
    myRoot = root;
    myProject = project;

    myToolWindow = ToolWindowManager.getInstance(project).registerToolWindow("File System", false, ToolWindowAnchor.LEFT);
    myContent = new MyContent();

    final FileChooserDescriptor descriptor = new FileChooserDescriptor(true, true, true, false, true, true);
    descriptor.addRoot(myRoot);

    myFsTree = new FileSystemTreeImpl(project, descriptor);
    myContent.add(ScrollPaneFactory.createScrollPane(myFsTree.getTree()), BorderLayout.CENTER);
    EditSourceOnDoubleClickHandler.install(myFsTree.getTree());
    EditSourceOnEnterKeyHandler.install(myFsTree.getTree());

    final ContentFactory contentFactory = new ContentFactoryImpl();
    final Content content = contentFactory.createContent(myContent, null, false);
    myToolWindow.getContentManager().addContent(content);
  }


  private class MyContent extends JPanel implements DataProvider {
    public MyContent() {
      super(new BorderLayout());
    }

    @Nullable
    public Object getData(@NonNls final String dataId) {
      if (PlatformDataKeys.NAVIGATABLE.is(dataId)) {
        final VirtualFile file = myFsTree.getSelectedFile();
        if (file != null) {
          return new OpenFileDescriptor(myProject, file);
        }
      }
      else if (PlatformDataKeys.VIRTUAL_FILE.is(dataId)) {
        return myFsTree.getSelectedFile();
      }
      return null;
    }
  }

}
