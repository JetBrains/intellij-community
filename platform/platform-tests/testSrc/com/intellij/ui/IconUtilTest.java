/*
 * Copyright 2000-2015 JetBrains s.r.o.
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
package com.intellij.ui;

import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.project.DumbService;
import com.intellij.openapi.util.Iconable;
import com.intellij.openapi.util.ThrowableComputable;
import com.intellij.openapi.vfs.CharsetToolkit;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.testFramework.PlatformTestCase;
import com.intellij.testFramework.PsiTestUtil;
import com.intellij.util.FileContentUtilCore;
import com.intellij.util.IconUtil;
import com.intellij.util.PlatformIcons;
import com.intellij.util.TimeoutUtil;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.awt.*;
import java.awt.image.ImageObserver;
import java.io.File;
import java.io.IOException;
import java.text.AttributedCharacterIterator;
import java.util.Collections;
import java.util.List;

public class IconUtilTest extends PlatformTestCase {
  @Override
  protected void setUp() throws Exception {
    super.setUp();
    while (DumbService.isDumb(getProject())) {
      UIUtil.dispatchAllInvocationEvents();
    }
  }

  public void testIconDeferrerDoesNotDeferIconsAdInfinitum() throws IOException {
    VirtualFile file = createTempFile("txt", null, "hkjh", CharsetToolkit.UTF8_CHARSET);

    Icon icon = IconUtil.getIcon(file, Iconable.ICON_FLAG_VISIBILITY, getProject());
    assertTrue(icon instanceof DeferredIcon);

    Graphics g = createMockGraphics();
    icon.paintIcon(new JLabel(), g, 0, 0);  // force to eval
    TimeoutUtil.sleep(1000); // give chance to evaluate

    Icon icon2 = IconUtil.getIcon(file, Iconable.ICON_FLAG_VISIBILITY, getProject());
    assertSame(icon, icon2);

    FileContentUtilCore.reparseFiles(file);
    Icon icon3 = IconUtil.getIcon(file, Iconable.ICON_FLAG_VISIBILITY, getProject());
    assertNotSame(icon2, icon3);
  }

  @NotNull
  private static Graphics createMockGraphics() {
    return new Graphics() {
        @Override
        public Graphics create() {
          return this;
        }

        @Override
        public void translate(int x, int y) {

        }

        @Override
        public Color getColor() {
          return null;
        }

        @Override
        public void setColor(Color c) {

        }

        @Override
        public void setPaintMode() {

        }

        @Override
        public void setXORMode(Color c1) {

        }

        @Override
        public Font getFont() {
          return null;
        }

        @Override
        public void setFont(Font font) {

        }

        @Override
        public FontMetrics getFontMetrics(Font f) {
          return null;
        }

        @Override
        public Rectangle getClipBounds() {
          return null;
        }

        @Override
        public void clipRect(int x, int y, int width, int height) {

        }

        @Override
        public void setClip(int x, int y, int width, int height) {

        }

        @Override
        public Shape getClip() {
          return null;
        }

        @Override
        public void setClip(Shape clip) {

        }

        @Override
        public void copyArea(int x, int y, int width, int height, int dx, int dy) {

        }

        @Override
        public void drawLine(int x1, int y1, int x2, int y2) {

        }

        @Override
        public void fillRect(int x, int y, int width, int height) {

        }

        @Override
        public void clearRect(int x, int y, int width, int height) {

        }

        @Override
        public void drawRoundRect(int x, int y, int width, int height, int arcWidth, int arcHeight) {

        }

        @Override
        public void fillRoundRect(int x, int y, int width, int height, int arcWidth, int arcHeight) {

        }

        @Override
        public void drawOval(int x, int y, int width, int height) {

        }

        @Override
        public void fillOval(int x, int y, int width, int height) {

        }

        @Override
        public void drawArc(int x, int y, int width, int height, int startAngle, int arcAngle) {

        }

        @Override
        public void fillArc(int x, int y, int width, int height, int startAngle, int arcAngle) {

        }

        @Override
        public void drawPolyline(int[] xPoints, int[] yPoints, int nPoints) {

        }

        @Override
        public void drawPolygon(int[] xPoints, int[] yPoints, int nPoints) {

        }

        @Override
        public void fillPolygon(int[] xPoints, int[] yPoints, int nPoints) {

        }

        @Override
        public void drawString(String str, int x, int y) {

        }

        @Override
        public void drawString(AttributedCharacterIterator iterator, int x, int y) {

        }

        @Override
        public boolean drawImage(Image img, int x, int y, ImageObserver observer) {
          return false;
        }

        @Override
        public boolean drawImage(Image img, int x, int y, int width, int height, ImageObserver observer) {
          return false;
        }

        @Override
        public boolean drawImage(Image img, int x, int y, Color bgcolor, ImageObserver observer) {
          return false;
        }

        @Override
        public boolean drawImage(Image img, int x, int y, int width, int height, Color bgcolor, ImageObserver observer) {
          return false;
        }

        @Override
        public boolean drawImage(Image img, int dx1, int dy1, int dx2, int dy2, int sx1, int sy1, int sx2, int sy2, ImageObserver observer) {
          return false;
        }

        @Override
        public boolean drawImage(Image img,
                                 int dx1,
                                 int dy1,
                                 int dx2,
                                 int dy2,
                                 int sx1,
                                 int sy1,
                                 int sx2,
                                 int sy2,
                                 Color bgcolor,
                                 ImageObserver observer) {
          return false;
        }

        @Override
        public void dispose() {

        }
      };
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
                                               file.setBinaryContent("class X {}".getBytes(CharsetToolkit.UTF8_CHARSET));
                                               file.setWritable(false);
                                               return null;
                                             });
    UIUtil.dispatchAllInvocationEvents(); // write actions
    UIUtil.dispatchAllInvocationEvents();
    try {
      Icon icon = IconUtil.getIcon(file, -1, getProject());
      icon.paintIcon(new JLabel(), createMockGraphics(), 0, 0);  // force to eval
      TimeoutUtil.sleep(1000); // give chance to evaluate
      UIUtil.dispatchAllInvocationEvents();
      UIUtil.dispatchAllInvocationEvents();

      List<Icon> icons = autopsyIconsFrom(icon);
      assertOneElement(ContainerUtil.filter(icons, ic -> ic == PlatformIcons.LOCKED_ICON));
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

  @NotNull
  private static List<Icon> autopsyIconsFrom(@NotNull Icon icon) {
    if (icon instanceof RetrievableIcon) {
      return autopsyIconsFrom(((RetrievableIcon)icon).retrieveIcon());
    }
    if (icon instanceof LayeredIcon) {
      return ContainerUtil.flatten(ContainerUtil.map(((LayeredIcon)icon).getAllLayers(), IconUtilTest::autopsyIconsFrom));
    }
    if (icon instanceof RowIcon) {
      return ContainerUtil.flatten(ContainerUtil.map(((RowIcon)icon).getAllIcons(), IconUtilTest::autopsyIconsFrom));
    }
    return Collections.singletonList(icon);
  }
}
