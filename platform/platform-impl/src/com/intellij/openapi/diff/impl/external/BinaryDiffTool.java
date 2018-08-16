// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.diff.impl.external;

import com.intellij.ide.diff.DiffElement;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.diff.*;
import com.intellij.openapi.fileEditor.ex.FileEditorProviderManager;
import com.intellij.openapi.fileTypes.UIBasedFileType;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.awt.*;
import java.io.IOException;
import java.util.Arrays;

/**
 * @author Konstantin Bulenkov
 * @author max
 */
public class BinaryDiffTool implements DiffTool {
  private static final Logger LOG = Logger.getInstance("#com.intellij.openapi.diff.impl.external.BinaryDiffTool");
  public static final DiffTool INSTANCE = new BinaryDiffTool();

  @Override
  public void show(final DiffRequest data) {
    final DiffContent current = data.getContents()[0];
    final DiffContent upToDate = data.getContents()[1];

    final Project project = data.getProject();
    if ((current instanceof FileContent && upToDate instanceof FileContent)
        || (current.getContentType() instanceof UIBasedFileType && upToDate.getContentType() instanceof UIBasedFileType)) {
      final VirtualFile src = current.getFile();
      final VirtualFile trg = upToDate.getFile();
      if (src != null && trg != null) {
        final PanelCreator creator = new PanelCreator(data);
        if (creator.isCanCreatePanel()) {
          new DialogWrapper(data.getProject()) {
            public DiffPanel myPanel;
            {
              setModal(false);
              init();
            }

            @Override
            protected String getDimensionServiceKey() {
              return "BinaryDiffDialog";
            }

            @NotNull
            @Override
            protected Action[] createActions() {
              final Action close = getCancelAction();
              close.putValue(Action.NAME, "&Close");
              return new Action[]{close};
            }

            @Override
            protected JComponent createCenterPanel() {
              myPanel = creator.create(getWindow(), getDisposable(), BinaryDiffTool.this);
              return myPanel.getComponent();
            }
          }.show();
          return;
        } else {
          final DirDiffManager diffManager = DirDiffManager.getInstance(project);
          final DiffElement before = diffManager.createDiffElement(src);
          final DiffElement after  = diffManager.createDiffElement(trg);

          if (before != null && after != null && diffManager.canShow(after, before)) {
            diffManager.showDiff(before, after);
            return;
          }
        }
      }
    }
    try {
      final boolean equal = Arrays.equals(current.getBytes(), upToDate.getBytes());
      Messages.showMessageDialog(equal
                                 ? DiffBundle.message("binary.files.are.identical.message")
                                 : DiffBundle.message("binary.files.are.different.message"),
                                 equal
                                 ? DiffBundle.message("files.are.identical.dialog.title")
                                 : DiffBundle.message("files.are.different.dialog.title"),
                                 Messages.getInformationIcon());
    } catch (IOException e) {
      LOG.error(e);
    }
  }

  private static class PanelCreator {
    private final boolean myCanCreatePanel;
    private final DiffRequest myData;
    private final VirtualFile mySrc;
    private final VirtualFile myTrg;

    private PanelCreator(final DiffRequest data) {
      myData = data;
      final DiffContent current = data.getContents()[0];
      final DiffContent upToDate = data.getContents()[1];
      final Project project = data.getProject();
      mySrc = current.getFile();
      myTrg = upToDate.getFile();
      myCanCreatePanel = canShowContent(project, mySrc) && canShowContent(project, myTrg);
    }

    private boolean canShowContent(final Project project, final VirtualFile file) {
      if (file == null) return true;
      return FileEditorProviderManager.getInstance().getProviders(project, file).length > 0;
    }

    public boolean isCanCreatePanel() {
      return myCanCreatePanel;
    }

    public DiffPanel create(final Window window, final Disposable disposable, final BinaryDiffTool tool) {
      if (! myCanCreatePanel) return null;

      final DiffPanel panel = DiffManagerImpl.createDiffPanel(myData, window, disposable, tool);
      panel.removeStatusBar();
      return panel;
    }
  }

  @Override
  public boolean canShow(final DiffRequest data) {
    final DiffContent[] contents = data.getContents();
    if (contents.length != 2) {
      return false;
    }
    for (DiffContent content : contents) {
      final VirtualFile file = content.getFile();
      if (file == null) {
        if (content.isEmpty()) continue;
        return false;
      }
      if (file.isDirectory()) {
        return false;
      }
    }
    return true;
  }

  @Override
  public DiffViewer createComponent(String title, DiffRequest request, Window window, @NotNull Disposable parentDisposable) {
    final PanelCreator creator = new PanelCreator(request);
    if (! creator.isCanCreatePanel()) return null;
    return creator.create(window, parentDisposable, this);
  }

  public static boolean canShow(@NotNull Project project, VirtualFile file) {
    if (file == null) return false;
    if (FileEditorProviderManager.getInstance().getProviders(project, file).length > 0) return true;

    final DirDiffManager diffManager = DirDiffManager.getInstance(project);
    return diffManager.createDiffElement(file) != null;
  }
}
