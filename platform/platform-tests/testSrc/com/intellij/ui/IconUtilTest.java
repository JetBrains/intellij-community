// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ui;

import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.project.DumbService;
import com.intellij.openapi.util.Iconable;
import com.intellij.openapi.util.LastComputedIconCache;
import com.intellij.openapi.util.ThrowableComputable;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.testFramework.HeavyPlatformTestCase;
import com.intellij.testFramework.PlatformTestUtil;
import com.intellij.testFramework.PsiTestUtil;
import com.intellij.util.FileContentUtilCore;
import com.intellij.util.IconUtil;
import com.intellij.util.PlatformIcons;
import com.intellij.util.TimeoutUtil;
import org.jetbrains.annotations.NotNull;

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
    icon.paintIcon(new JLabel(), g, 0, 0);  // force to eval
    TimeoutUtil.sleep(1000); // give chance to evaluate

    Icon icon2 = IconUtil.getIcon(file, Iconable.ICON_FLAG_VISIBILITY, getProject());
    assertSame(icon, icon2);

    FileContentUtilCore.reparseFiles(file);
    Icon icon3 = IconUtil.getIcon(file, Iconable.ICON_FLAG_VISIBILITY, getProject());
    assertNotSame(icon2, icon3);
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
}
