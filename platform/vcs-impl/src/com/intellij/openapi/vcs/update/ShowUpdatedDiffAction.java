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
package com.intellij.openapi.vcs.update;

import com.intellij.history.ByteContent;
import com.intellij.history.Label;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.fileEditor.impl.LoadTextUtil;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Condition;
import com.intellij.openapi.util.IconLoader;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.vcs.*;
import com.intellij.openapi.vcs.changes.Change;
import com.intellij.openapi.vcs.changes.ContentRevision;
import com.intellij.openapi.vcs.changes.actions.ShowDiffAction;
import com.intellij.openapi.vcs.changes.actions.ShowDiffUIContext;
import com.intellij.openapi.vcs.history.VcsRevisionNumber;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.encoding.EncodingManager;
import com.intellij.openapi.vfs.pointers.VirtualFilePointer;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.io.File;
import java.lang.ref.SoftReference;
import java.util.Iterator;

public class ShowUpdatedDiffAction extends AnAction implements DumbAware {
  private static Icon ourIcon;
  private static String ourText;
  private static String ourDescription;

  private static void initData() {
    ourIcon = IconLoader.getIcon("/actions/diff.png");
    ourText = VcsBundle.message("updated.info.tree.show.diff.text");
    ourDescription = VcsBundle.message("updated.info.tree.show.diff.description");
  }

  @Override
  public void update(AnActionEvent e) {
    if (ourIcon == null) {
      initData();
    }

    final DataContext dc = e.getDataContext();

    final Presentation presentation = e.getPresentation();
    presentation.setIcon(ourIcon);
    presentation.setText(ourText);
    presentation.setDescription(ourDescription);

    //presentation.setVisible(isVisible(dc));
    presentation.setEnabled(isVisible(dc) && isEnabled(dc));
  }

  private boolean isVisible(final DataContext dc) {
    final Project project = PlatformDataKeys.PROJECT.getData(dc);
    return (project != null) && (VcsDataKeys.LABEL_BEFORE.getData(dc) != null) && (VcsDataKeys.LABEL_AFTER.getData(dc) != null);
  }

  private boolean isEnabled(final DataContext dc) {
    final Iterable<VirtualFilePointer> iterable = VcsDataKeys.UPDATE_VIEW_FILES_ITERABLE.getData(dc);
    return iterable != null;
  }

  public void actionPerformed(AnActionEvent e) {
    final DataContext dc = e.getDataContext();
    if ((! isVisible(dc)) || (! isEnabled(dc))) return;

    final Project project = PlatformDataKeys.PROJECT.getData(dc);
    final Iterable<VirtualFilePointer> iterable = VcsDataKeys.UPDATE_VIEW_FILES_ITERABLE.getData(dc);
    final Label before = (Label) VcsDataKeys.LABEL_BEFORE.getData(dc);
    final Label after = (Label) VcsDataKeys.LABEL_AFTER.getData(dc);

    final String selectedUrl = VcsDataKeys.UPDATE_VIEW_SELECTED_PATH.getData(dc);

    ShowDiffAction.showDiffForChange(new MyIterableWrapper(iterable.iterator(), before, after), new MySelectionMarker(selectedUrl),
                                     project, new ShowDiffUIContext(true));
  }

  private static class MySelectionMarker implements Condition<Change> {
    private final String mySelectedPath;
    private boolean myFirstSelected;

    public MySelectionMarker(String selectedPath) {
      mySelectedPath = selectedPath;
    }

    public boolean value(Change change) {
      if (mySelectedPath == null) {
        if (myFirstSelected) {
          myFirstSelected = true;
          return true;
        }
        return false;
      }
      final String url = ((MyCheckpointContentRevision)change.getBeforeRevision()).getUrl();
      return mySelectedPath.equals(url);
    }
  }

  private static class MyIterableWrapper implements Iterable<Change> {
    private final Iterator<VirtualFilePointer> myVfIterator;
    private final Label myBefore;
    private final Label myAfter;

    private MyIterableWrapper(Iterator<VirtualFilePointer> vfIterator, final Label before, final Label after) {
      myVfIterator = vfIterator;
      myBefore = before;
      myAfter = after;
    }

    public Iterator<Change> iterator() {
      return new MyIteratorWrapper(myVfIterator, myBefore, myAfter);
    }
  }

  private static class MyLoader {
    private final Label myLabel;

    private MyLoader(Label label) {
      myLabel = label;
    }

    @Nullable
    public String convert(final VirtualFilePointer pointer) {
      final String path = pointer.getPresentableUrl();
      final ByteContent byteContent = myLabel.getByteContent(FileUtil.toSystemIndependentName(path));
      if (byteContent.isDirectory() || byteContent.getBytes() == null) {
        return null;
      }
      final VirtualFile vf = pointer.getFile();
      if (vf == null) {
        return LoadTextUtil.getTextByBinaryPresentation(byteContent.getBytes(), EncodingManager.getInstance().getDefaultCharset()).toString();
      } else {
        return LoadTextUtil.getTextByBinaryPresentation(byteContent.getBytes(), vf).toString();
      }
    }
  }

  private static class MyCheckpointContentRevision implements ContentRevision {
    private SoftReference<String> myContent;
    private final MyLoader myLoader;
    private final VirtualFilePointer myPointer;
    private final boolean myBefore;

    private MyCheckpointContentRevision(final VirtualFilePointer pointer, final MyLoader loader, final boolean before) {
      myLoader = loader;
      myPointer = pointer;
      myBefore = before;
    }

    public String getContent() throws VcsException {
      if (myContent != null) {
        final String s = myContent.get();
        if (s != null) {
          return s;
        }
      }

      final String loaded = myLoader.convert(myPointer);
      myContent = new SoftReference<String>(loaded);

      return loaded;
    }

    public String getUrl() {
      return myPointer.getUrl();
    }

    @NotNull
    public FilePath getFile() {
      final VirtualFile vf = myPointer.getFile();
      if (vf != null) {
        return new FilePathImpl(vf);
      }
      return new FilePathImpl(new File(myPointer.getPresentableUrl()), false);
    }

    @NotNull
    public VcsRevisionNumber getRevisionNumber() {
      return new VcsRevisionNumber() {
        public String asString() {
          return myBefore ? "Before update" : "After update";
        }

        public int compareTo(VcsRevisionNumber o) {
          return myBefore ? -1 : 1;
        }
      };
    }
  }

  private static class MyIteratorWrapper implements Iterator<Change> {
    private final MyLoader myBeforeLoader;
    private final MyLoader myAfterLoader;
    private final Iterator<VirtualFilePointer> myVfIterator;

    public MyIteratorWrapper(final Iterator<VirtualFilePointer> vfIterator, final Label before, final Label after) {
      myVfIterator = vfIterator;
      myBeforeLoader = new MyLoader(before);
      myAfterLoader = new MyLoader(after);
    }

    public boolean hasNext() {
      return myVfIterator.hasNext();
    }

    public Change next() {
      final VirtualFilePointer pointer = myVfIterator.next();

      return new Change(new MyCheckpointContentRevision(pointer, myBeforeLoader, true),
                        new MyCheckpointContentRevision(pointer, myAfterLoader, false));
    }

    public void remove() {
      throw new UnsupportedOperationException();
    }
  }
}
