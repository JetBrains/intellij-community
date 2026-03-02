// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.actions;

import com.intellij.openapi.actionSystem.ActionPlaces;
import com.intellij.openapi.actionSystem.ActionUiKind;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.actionSystem.impl.SimpleDataContext;
import com.intellij.openapi.ide.CopyPasteManager;
import com.intellij.openapi.vfs.DeprecatedVirtualFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileSystem;
import com.intellij.testFramework.LightVirtualFile;
import com.intellij.testFramework.junit5.TestApplication;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.junit.jupiter.api.Test;

import java.awt.datatransfer.DataFlavor;
import java.awt.datatransfer.StringSelection;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

@TestApplication
public class CopyPathsActionTest {
  @Test
  public void disabledForNonPhysicalFileSystem() {
    CopyPathsAction action = new CopyPathsAction();

    AnActionEvent event = createEvent(action, new LightVirtualFile("notes.txt"));
    action.update(event);

    assertFalse(event.getPresentation().isEnabledAndVisible());
  }

  @Test
  public void enabledForPhysicalLikeFileSystem() {
    CopyPathsAction action = new CopyPathsAction();

    AnActionEvent event = createEvent(action, new PhysicalLikeVirtualFile("notes.txt"));
    action.update(event);

    assertTrue(event.getPresentation().isEnabledAndVisible());
  }

  @Test
  public void actionPerformedSkipsNonPhysicalFiles() {
    String marker = "marker-value";
    CopyPasteManager.getInstance().setContents(new StringSelection(marker));

    CopyPathsAction action = new CopyPathsAction();
    action.actionPerformed(createEvent(action, new LightVirtualFile("notes.txt")));

    assertEquals(marker, CopyPasteManager.getInstance().getContents(DataFlavor.stringFlavor));
  }

  private static @NotNull AnActionEvent createEvent(@NotNull CopyPathsAction action, @NotNull VirtualFile file) {
    DataContext dataContext = SimpleDataContext.builder()
      .add(CommonDataKeys.VIRTUAL_FILE_ARRAY, new VirtualFile[]{file})
      .build();
    return AnActionEvent.createEvent(
      dataContext,
      action.getTemplatePresentation().clone(),
      ActionPlaces.KEYBOARD_SHORTCUT,
      ActionUiKind.NONE,
      null
    );
  }

  private static final class PhysicalLikeVirtualFile extends LightVirtualFile {
    private PhysicalLikeVirtualFile(@NotNull String name) {
      super(name);
    }

    @Override
    public @NotNull VirtualFileSystem getFileSystem() {
      return PHYSICAL_LIKE_FILE_SYSTEM;
    }
  }

  private static final VirtualFileSystem PHYSICAL_LIKE_FILE_SYSTEM = new DeprecatedVirtualFileSystem() {
    @Override
    public @NotNull String getProtocol() {
      return "physical-like";
    }

    @Override
    public @Nullable VirtualFile findFileByPath(@NotNull String path) {
      return null;
    }

    @Override
    public void refresh(boolean asynchronous) {
    }

    @Override
    public @Nullable VirtualFile refreshAndFindFileByPath(@NotNull String path) {
      return null;
    }
  };
}
