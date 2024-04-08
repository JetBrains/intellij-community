// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ui;

import com.intellij.icons.AllIcons;
import com.intellij.ide.FileIconPatcher;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.project.DumbService;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Iconable;
import com.intellij.openapi.util.LastComputedIconCache;
import com.intellij.openapi.util.ThrowableComputable;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiDirectory;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiManager;
import com.intellij.testFramework.HeavyPlatformTestCase;
import com.intellij.testFramework.PlatformTestUtil;
import com.intellij.testFramework.PsiTestUtil;
import com.intellij.ui.icons.IconWithOverlay;
import com.intellij.util.FileContentUtilCore;
import com.intellij.util.IconUtil;
import com.intellij.util.PlatformIcons;
import com.intellij.util.TimeoutUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

public class IconUtilTest extends HeavyPlatformTestCase {
  @Override
  protected boolean isIconRequired() {
    return true;
  }

  @Override
  protected void setUp() throws Exception {
    super.setUp();
    while (DumbService.isDumb(getProject())) {
      PlatformTestUtil.dispatchAllInvocationEventsInIdeEventQueue();
    }
  }

  public void testIconDeferrerDoesNotDeferIconsAdInfinitum() {
    VirtualFile file = getTempDir().createVirtualFile(".txt", "hkjh");

    Icon icon = IconUtil.getIcon(file, Iconable.ICON_FLAG_VISIBILITY, getProject());
    assertTrue(icon instanceof DeferredIcon);

    Graphics g = IconTestUtil.createMockGraphics();
    // force to eval
    icon.paintIcon(new JLabel(), g, 0, 0);
    // give chance to evaluate
    TimeoutUtil.sleep(1000);

    Icon icon2 = IconUtil.getIcon(file, Iconable.ICON_FLAG_VISIBILITY, getProject());
    assertThat(icon2).isNotInstanceOf(DeferredIcon.class);
    assertThat(icon).isNotSameAs(icon2);

    FileContentUtilCore.reparseFiles(file);
    Icon icon3 = IconUtil.getIcon(file, Iconable.ICON_FLAG_VISIBILITY, getProject());
    assertThat(icon2).isNotSameAs(icon3);
  }

  public void testLockedPatchSmallIconAppliedOnlyOnceToJavaFile() throws IOException {
    File dir = createTempDir("my");

    VirtualFile sourceRoot = LocalFileSystem.getInstance().refreshAndFindFileByIoFile(dir);
    PsiTestUtil.addSourceRoot(getModule(), sourceRoot);
    VirtualFile file = createChildData(sourceRoot, "X.java");

    assertJustOneLockedIcon(file);
  }

  private void assertJustOneLockedIcon(@NotNull VirtualFile file) throws IOException {
    if (!Registry.is("ide.locked.icon.enabled", false)) return;
    LastComputedIconCache.clear(file);

    WriteCommandAction.runWriteCommandAction(getProject(), (ThrowableComputable<Void, IOException>)() -> {
      file.setBinaryContent("class X {}".getBytes(StandardCharsets.UTF_8));
      file.setWritable(false);
      return null;
    });
    // write actions
    PlatformTestUtil.dispatchAllInvocationEventsInIdeEventQueue();
    PlatformTestUtil.dispatchAllInvocationEventsInIdeEventQueue();
    try {
      List<Icon> icons = IconTestUtil.renderDeferredIcon(IconUtil.getIcon(file, -1, getProject()));
      List<Icon> result = new ArrayList<>();
      for (Icon icon : icons) {
        if (IconTestUtil.unwrapIcon(icon) == PlatformIcons.LOCKED_ICON) {
          result.add(icon);
        }
      }
      assertThat(result).hasSize(1);
    }
    finally {
      WriteCommandAction.runWriteCommandAction(getProject(), (ThrowableComputable<Void, IOException>)() -> {
        file.setWritable(true);
        return null;
      });
    }
  }

  public void testLockedPatchSmallIconAppliedOnlyOnceToTxtFile() throws IOException {
    File dir = createTempDir("my");

    VirtualFile sourceRoot = LocalFileSystem.getInstance().refreshAndFindFileByIoFile(dir);
    VirtualFile file = createChildData(sourceRoot, "X.txt");

    PsiTestUtil.addSourceRoot(getModule(), sourceRoot);

    assertJustOneLockedIcon(file);
  }

  public void testIconPatchersWorkForPsiItems() throws IOException {
    FileIconPatcher.EP_NAME.getPoint().registerExtension(new FileIconPatcher() {
      @Override
      public @NotNull Icon patchIcon(@NotNull Icon icon, @NotNull VirtualFile file, int flags, @Nullable Project project) {
        return new IconWithOverlay(icon, AllIcons.Actions.Scratch) {
          @Override
          public @Nullable Shape getOverlayShape(int x, int y) {
            return null;
          }
        };
      }
    }, getTestRootDisposable());

    File dir = createTempDir("my");

    VirtualFile vDir = LocalFileSystem.getInstance().refreshAndFindFileByIoFile(dir);
    VirtualFile vFile = createChildData(vDir, "X.txt");

    PsiDirectory psiDir = PsiManager.getInstance(getProject()).findDirectory(vDir);
    PsiFile psiFile = PsiManager.getInstance(getProject()).findFile(vFile);

    int flags = 0;

    Icon psiDirIcon = psiDir.getIcon(flags);
    Icon psiFileIcon = psiFile.getIcon(flags);

    Icon vDirIcon = IconUtil.computeFileIcon(vDir, flags, getProject());
    Icon vFileIcon = IconUtil.computeFileIcon(vFile, flags, getProject());

    IconTestUtil.renderDeferredIcon(psiDirIcon);
    IconTestUtil.renderDeferredIcon(psiFileIcon);

    assertSameElements("dir icons do not match",
                       IconTestUtil.renderDeferredIcon(psiDirIcon),
                       IconTestUtil.renderDeferredIcon(vDirIcon));
    assertSameElements("file icons do not match",
                       IconTestUtil.renderDeferredIcon(psiFileIcon),
                       IconTestUtil.renderDeferredIcon(vFileIcon));
  }
}
