// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ui;

import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.project.DumbService;
import com.intellij.openapi.util.Iconable;
import com.intellij.openapi.util.ThrowableComputable;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.testFramework.HeavyPlatformTestCase;
import com.intellij.testFramework.PsiTestUtil;
import com.intellij.util.FileContentUtilCore;
import com.intellij.util.IconUtil;
import com.intellij.util.PlatformIcons;
import com.intellij.util.TimeoutUtil;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.ui.UIUtil;

import javax.swing.*;
import java.awt.*;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;

public class IconUtilTest extends HeavyPlatformTestCase {
  @Override
  protected boolean isIconRequired() {
    return false;
  }

  @Override
  protected void setUp() throws Exception {
    super.setUp();
    while (DumbService.isDumb(getProject())) {
      UIUtil.dispatchAllInvocationEvents();
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

  private void assertJustOneLockedIcon(VirtualFile file) throws IOException {
    WriteCommandAction.runWriteCommandAction(getProject(),
                                             (ThrowableComputable<Void,IOException>)() -> {
                                               file.setBinaryContent("class X {}".getBytes(StandardCharsets.UTF_8));
                                               file.setWritable(false);
                                               return null;
                                             });
    UIUtil.dispatchAllInvocationEvents(); // write actions
    UIUtil.dispatchAllInvocationEvents();
    try {
      Icon icon = IconUtil.getIcon(file, -1, getProject());
      List<Icon> icons = IconTestUtil.renderDeferredIcon(icon);
      assertOneElement(ContainerUtil.filter(icons, ic -> ic == IconTestUtil.unwrapRetrievableIcon(PlatformIcons.LOCKED_ICON)));
    }
    finally {
      WriteCommandAction.runWriteCommandAction(getProject(),
                                               (ThrowableComputable<Void,IOException>)() -> {
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
