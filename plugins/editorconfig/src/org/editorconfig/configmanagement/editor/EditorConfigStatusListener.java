// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.editorconfig.configmanagement.editor;

import com.intellij.application.options.CodeStyle;
import com.intellij.codeInsight.daemon.DaemonCodeAnalyzer;
import com.intellij.navigation.NavigationItem;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.progress.Task;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VfsUtilCore;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileVisitor;
import com.intellij.psi.*;
import com.intellij.psi.codeStyle.CodeStyleSettings;
import com.intellij.psi.codeStyle.CodeStyleSettingsChangeEvent;
import com.intellij.psi.codeStyle.CodeStyleSettingsListener;
import com.intellij.psi.codeStyle.CodeStyleSettingsManager;
import com.intellij.ui.EditorNotifications;
import com.intellij.util.ObjectUtils;
import com.intellij.util.containers.ContainerUtil;
import org.editorconfig.Utils;
import org.editorconfig.configmanagement.ConfigEncodingManager;
import org.editorconfig.configmanagement.EditorConfigEncodingCache;
import org.editorconfig.language.messages.EditorConfigBundle;
import org.editorconfig.language.psi.EditorConfigOption;
import org.editorconfig.language.psi.EditorConfigSection;
import org.editorconfig.settings.EditorConfigSettings;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public final class EditorConfigStatusListener implements CodeStyleSettingsListener, Disposable {
  private boolean myEnabledStatus;
  private final VirtualFile myVirtualFile;
  private final Project myProject;
  private Set<String> myEncodings;

  private MyReloadTask myReloadTask;

  public EditorConfigStatusListener(@NotNull Project project, @NotNull VirtualFile virtualFile) {
    myProject = project;
    myEnabledStatus = Utils.isEnabled(project);
    myVirtualFile = virtualFile;
    myEncodings = extractEncodings();
    CodeStyleSettingsManager.getInstance(project).addListener(this);
  }

  @Override
  public void codeStyleSettingsChanged(@NotNull CodeStyleSettingsChangeEvent event) {
    CodeStyleSettings settings = CodeStyle.getSettings(myProject);
    if (settings.getCustomSettingsIfCreated(EditorConfigSettings.class) == null) {
      // plugin is currently being unloaded, can't run any updates
      return;
    }

    boolean newEnabledStatus = Utils.isEnabled(myProject);
    if (myEnabledStatus != newEnabledStatus) {
      myEnabledStatus = newEnabledStatus;
      onEditorConfigEnabled(newEnabledStatus);
    }
    Set<String> newEncodings = extractEncodings();
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

  @Override
  public void dispose() {
    CodeStyleSettingsManager.removeListener(myProject, this);
  }

  private void onEncodingChanged() {
    if (myReloadTask != null) {
      myReloadTask.interrupt();
    }
    MyReloadTask reloadTask = new MyReloadTask();
    ProgressManager.getInstance().run(reloadTask);
    myReloadTask = reloadTask;
  }

  private static boolean containsValidEncodings(@NotNull Set<String> encodings) {
    return ContainerUtil.and(encodings, encoding -> ConfigEncodingManager.toCharset(encoding) != null);
  }

  private class MyReloadTask extends Task.Backgroundable {
    private volatile boolean myInterrupted;

    private MyReloadTask() {
      super(EditorConfigStatusListener.this.myProject, EditorConfigBundle.message("encoding.change.reloading.files"), false);
    }

    @Override
    public void run(@NotNull ProgressIndicator indicator) {
      EditorConfigEncodingCache.getInstance().reset();
      List<VirtualFile> filesToReload = new ArrayList<>();
      VirtualFile parentDir = myVirtualFile.getParent();
      final FileDocumentManager fileDocumentManager = FileDocumentManager.getInstance();
      VfsUtilCore.visitChildrenRecursively(
        parentDir,
        new VirtualFileVisitor<>() {
          @Override
          public boolean visitFile(@NotNull VirtualFile file) {
            if (myInterrupted) throw new ProcessCanceledException();
            if (!file.isDirectory() &&
                !Utils.EDITOR_CONFIG_FILE_NAME.equals(file.getName()) &&
                fileDocumentManager.getCachedDocument(file) != null) {
              filesToReload.add(file);
            }
            return true;
          }
        }
      );
      ApplicationManager.getApplication().invokeLater(
        () -> {
          if (myProject != null) {
            filesToReload.forEach(file -> {
              EditorConfigEncodingCache.getInstance().cacheEncoding(myProject, file);
            });
          }
          fileDocumentManager.reloadFiles(filesToReload.toArray(VirtualFile.EMPTY_ARRAY));
        });
    }


    void interrupt() {
      myInterrupted = true;
    }
  }

  @NotNull
  private Set<String> extractEncodings() {
    final Set<String> charsets = new HashSet<>();
    PsiFile psiFile = PsiManager.getInstance(myProject).findFile(myVirtualFile);
    if (psiFile != null) {
      PsiRecursiveElementVisitor visitor = new PsiRecursiveElementVisitor() {
        @Override
        public void visitElement(@NotNull PsiElement element) {
          if (element instanceof PsiFile || element instanceof EditorConfigSection) {
            super.visitElement(element);
          }
          else if (element instanceof EditorConfigOption) {
            String keyName = ObjectUtils.doIfNotNull(((EditorConfigOption)element).getFlatOptionKey(), NavigationItem::getName);
            if (ConfigEncodingManager.charsetKey.equals(keyName)) {
              String charsetStr =
                ObjectUtils.doIfNotNull(((EditorConfigOption)element).getOptionValueIdentifier(), NavigationItem::getName);
              if (charsetStr != null) {
                charsets.add(charsetStr);
              }
            }
          }
        }
      };
      psiFile.accept(visitor);
    }
    return charsets;
  }
}
