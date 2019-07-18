// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.editorconfig.configmanagement.editor;

import com.intellij.codeInsight.daemon.LineMarkerInfo;
import com.intellij.codeInsight.daemon.LineMarkerProviderDescriptor;
import com.intellij.openapi.actionSystem.ActionGroup;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.DefaultActionGroup;
import com.intellij.openapi.editor.markup.GutterIconRenderer;
import com.intellij.openapi.fileChooser.FileChooserDescriptor;
import com.intellij.openapi.fileChooser.FileChooserDialog;
import com.intellij.openapi.fileChooser.FileChooserFactory;
import com.intellij.openapi.fileChooser.FileElement;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.project.DumbAwareAction;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.util.Function;
import org.editorconfig.Utils;
import org.editorconfig.core.EditorConfig;
import org.editorconfig.language.messages.EditorConfigBundle;
import org.editorconfig.language.psi.EditorConfigElementTypes;
import org.editorconfig.language.psi.EditorConfigHeader;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collections;

import static com.intellij.icons.AllIcons.General.InspectionsEye;

public class EditorConfigPreviewMarkerProvider extends LineMarkerProviderDescriptor {
  @Nls(capitalization = Nls.Capitalization.Sentence)
  @Nullable("null means disabled")
  @Override
  public String getName() {
    return "Code preview";
  }

  @Nullable
  @Override
  public LineMarkerInfo getLineMarkerInfo(@NotNull PsiElement element) {
    if (element instanceof EditorConfigHeader) {
      if (EditorConfigPreviewManager.getInstance(element.getProject()).getAssociatedPreviewFile(element.getContainingFile().getVirtualFile()) == null) {
        ActionGroup actionGroup = createActions((EditorConfigHeader)element);
        PsiElement child = element.getFirstChild();
        if (child != null && child.getNode().getElementType() == EditorConfigElementTypes.L_BRACKET) {
          return new SectionLineMarkerInfo(actionGroup,
                                           child,
                                           element.getTextRange(),
                                           null);
        }
      }
    }
    return null;
  }

  private static class SectionLineMarkerInfo extends LineMarkerInfo<PsiElement> {
    private final ActionGroup myActionGroup;

    private SectionLineMarkerInfo(@NotNull ActionGroup actionGroup,
                                  @NotNull PsiElement element,
                                  @NotNull TextRange range,
                                  @Nullable Function<? super PsiElement, String> tooltipProvider) {
      super(element, range, InspectionsEye, tooltipProvider, null, GutterIconRenderer.Alignment.LEFT);
      myActionGroup = actionGroup;
    }

    @Nullable
    @Override
    public GutterIconRenderer createGutterRenderer() {
      return new LineMarkerGutterIconRenderer<PsiElement>(this) {
        @Override
        public AnAction getClickAction() {
          return null;
        }

        @Override
        public boolean isNavigateAction() {
          return true;
        }

        @Override
        public ActionGroup getPopupMenuActions() {
          return myActionGroup;
        }
      };
    }
  }

  @NotNull
  private static ActionGroup createActions(@NotNull EditorConfigHeader header) {
    return new DefaultActionGroup(Collections.singletonList(new ChooseFileAction(header)));
  }

  private static class ChooseFileAction extends DumbAwareAction {
    private final @NotNull EditorConfigHeader myHeader;

    private ChooseFileAction(@NotNull EditorConfigHeader header) {
      super(EditorConfigBundle.message("editor.preview.open"));
      myHeader = header;
    }

    @Override
    public void actionPerformed(@NotNull AnActionEvent e) {
      VirtualFile previewFile =
        choosePreviewFile(myHeader.getProject(), getRootDir(myHeader), getPattern(myHeader.getText()));
      if (previewFile != null) {
        VirtualFile editorConfigFile = myHeader.getContainingFile().getVirtualFile();
        openPreview(myHeader.getProject(), editorConfigFile, previewFile);
      }
    }
  }

  private static String getPattern(@NotNull String header) {
    return StringUtil.trimEnd(StringUtil.trimStart(header, "["), "]");
  }

  @Nullable
  private static VirtualFile choosePreviewFile(@NotNull Project project, @NotNull VirtualFile rootDir, @NotNull String pattern) {
    FileChooserDescriptor descriptor = new FileChooserDescriptor(true, false, false, false, false, false) {
      @Override
      public boolean isFileVisible(VirtualFile file, boolean showHiddenFiles) {
        return (showHiddenFiles || !FileElement.isFileHidden(file))
          && !(Utils.EDITOR_CONFIG_FILE_NAME.equals(file.getName()))
          && (file.getLength() <= EditorConfigEditorProvider.MAX_PREVIEW_LENGTH)
          && matchesPattern(rootDir, pattern, file.getPath()) || file.isDirectory();
      }

      @Override
      public boolean isFileSelectable(VirtualFile file) {
        return !file.isDirectory();
      }
    }.withRoots(rootDir);
    FileChooserDialog fileChooser = FileChooserFactory.getInstance()
      .createFileChooser(descriptor, project, null);
    final VirtualFile[] virtualFiles = fileChooser.choose(project, VirtualFile.EMPTY_ARRAY);
    return virtualFiles.length > 0 ? virtualFiles[0] : null;
  }

  private static boolean matchesPattern(@NotNull VirtualFile rootDir, @NotNull String pattern, @NotNull String filePath) {
    return EditorConfig.filenameMatches(rootDir.getPath(), pattern, filePath);
  }

  @NotNull
  private static VirtualFile getRootDir(@NotNull EditorConfigHeader header) {
    PsiFile psiFile = header.getContainingFile();
    return psiFile.getVirtualFile().getParent();
  }

  private static void openPreview(@NotNull Project project, @NotNull VirtualFile editorConfigFile, @NotNull VirtualFile previewFile) {
    FileEditorManager.getInstance(project).closeFile(editorConfigFile);
    EditorConfigPreviewManager.getInstance(project).associateWithPreviewFile(editorConfigFile, previewFile);
    FileEditorManager.getInstance(project).openFile(editorConfigFile, true);
  }

}
