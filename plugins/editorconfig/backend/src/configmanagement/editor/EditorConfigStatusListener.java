// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.editorconfig.configmanagement.editor;

import com.intellij.application.options.CodeStyle;
import com.intellij.codeInsight.daemon.DaemonCodeAnalyzer;
import com.intellij.editorconfig.common.syntax.psi.EditorConfigFlatOptionKey;
import com.intellij.editorconfig.common.syntax.psi.EditorConfigOption;
import com.intellij.editorconfig.common.syntax.psi.EditorConfigOptionValueIdentifier;
import com.intellij.editorconfig.common.syntax.psi.EditorConfigSection;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.*;
import com.intellij.psi.codeStyle.CodeStyleSettings;
import com.intellij.psi.codeStyle.CodeStyleSettingsChangeEvent;
import com.intellij.psi.codeStyle.CodeStyleSettingsListener;
import com.intellij.ui.EditorNotifications;
import org.editorconfig.Utils;
import org.editorconfig.configmanagement.ConfigEncodingCharsetUtil;
import org.editorconfig.configmanagement.EditorConfigEncodingCache;
import org.editorconfig.settings.EditorConfigSettings;
import org.jetbrains.annotations.NotNull;

import java.util.HashSet;
import java.util.Set;

final class EditorConfigStatusListener implements CodeStyleSettingsListener {
  private boolean myEnabledStatus;
  private final VirtualFile myVirtualFile;
  private final Project myProject;
  private Set<String> myEncodings;

  EditorConfigStatusListener(@NotNull Project project,
                             @NotNull VirtualFile virtualFile,
                             @NotNull Set<String> encodings) {
    myProject = project;
    myEnabledStatus = Utils.INSTANCE.isEnabled(project);
    myVirtualFile = virtualFile;
    myEncodings = encodings;
  }

  @Override
  public void codeStyleSettingsChanged(@NotNull CodeStyleSettingsChangeEvent event) {
    CodeStyleSettings settings = CodeStyle.getSettings(myProject);
    if (settings.getCustomSettingsIfCreated(EditorConfigSettings.class) == null) {
      // plugin is currently being unloaded, can't run any updates
      return;
    }

    boolean newEnabledStatus = Utils.INSTANCE.isEnabled(myProject);
    if (myEnabledStatus != newEnabledStatus) {
      myEnabledStatus = newEnabledStatus;
      onEditorConfigEnabled(newEnabledStatus);
    }
    Set<String> newEncodings = extractEncodings(myProject, myVirtualFile);
    if (!myEncodings.equals(newEncodings)) {
      if (containsValidEncodings(newEncodings)) {
        onEncodingChanged();
      }
      myEncodings = newEncodings;
    }
  }

  private void onEditorConfigEnabled(boolean isEnabled) {
    if (!isEnabled) {
      FileEditorManager fileEditorManager = FileEditorManager.getInstance(myProject);
      fileEditorManager.closeFile(myVirtualFile);
      fileEditorManager.openFile(myVirtualFile, false);
    }
    EditorNotifications.getInstance(myProject).updateNotifications(myVirtualFile);
    Document document = FileDocumentManager.getInstance().getDocument(myVirtualFile);
    PsiFile psiFile = document == null ? null : PsiDocumentManager.getInstance(myProject).getPsiFile(document);
    if (psiFile != null) {
      DaemonCodeAnalyzer.getInstance(myProject).restart(psiFile);
    }
  }

  private static void onEncodingChanged() {
    EditorConfigEncodingCache.Companion.getInstance().reset();
  }

  private static boolean containsValidEncodings(@NotNull Set<String> encodings) {
    for (String t : encodings) {
      if (ConfigEncodingCharsetUtil.INSTANCE.toCharset(t) == null) {
        return false;
      }
    }
    return true;
  }

  static @NotNull Set<String> extractEncodings(@NotNull Project project, @NotNull VirtualFile file) {
    Set<String> charsets = new HashSet<>();
    PsiFile psiFile = PsiManager.getInstance(project).findFile(file);
    if (psiFile == null) {
      return charsets;
    }

    PsiRecursiveElementVisitor visitor = new PsiRecursiveElementVisitor() {
      @Override
      public void visitElement(@NotNull PsiElement element) {
        if (element instanceof PsiFile || element instanceof EditorConfigSection) {
          super.visitElement(element);
        }
        else if (element instanceof EditorConfigOption) {
          EditorConfigFlatOptionKey obj1 = ((EditorConfigOption)element).getFlatOptionKey();
          String keyName = obj1 == null ? null : obj1.getName();
          if (ConfigEncodingCharsetUtil.charsetKey.equals(keyName)) {
            EditorConfigOptionValueIdentifier obj = ((EditorConfigOption)element).getOptionValueIdentifier();
            String charsetStr = obj == null ? null : obj.getName();
            if (charsetStr != null) {
              charsets.add(charsetStr);
            }
          }
        }
      }
    };
    psiFile.accept(visitor);
    return charsets;
  }
}
