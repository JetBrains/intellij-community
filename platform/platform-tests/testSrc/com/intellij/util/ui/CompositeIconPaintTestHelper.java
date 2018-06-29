// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.util.ui;

import com.intellij.openapi.util.IconLoader;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.ScalableIcon;
import com.intellij.ui.RestoreScaleRule;
import com.intellij.util.ui.JBUI.ScaleContext;
import com.intellij.util.ui.paint.ImageComparator;
import org.junit.ClassRule;
import org.junit.rules.ExternalResource;

import javax.swing.*;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.File;
import java.net.MalformedURLException;
import java.util.function.BiFunction;

import static com.intellij.util.ui.JBUI.ScaleType.*;
import static com.intellij.util.ui.TestScaleHelper.*;

/**
 * @author tav
 */
public abstract class CompositeIconPaintTestHelper {
  @ClassRule
  public static final ExternalResource manageState = new RestoreScaleRule();

  protected void test() {
    overrideJreHiDPIEnabled(true);

    BiFunction<Integer, Integer, Integer> bit2scale = (mask, bit) -> ((mask >> bit) & 0x1) + 1;

    for (int mask=0; mask<7; mask++) {
      int iconScale = bit2scale.apply(mask, 2);
      int usrScale = bit2scale.apply(mask, 1);
      int sysScale = bit2scale.apply(mask, 0);
      assert iconScale * usrScale * sysScale <= 4;
      test(ScaleContext.create(SYS_SCALE.of(sysScale), USR_SCALE.of(usrScale), OBJ_SCALE.of(iconScale)));
    }
  }

  private void test(final ScaleContext ctx) {
    assume(ctx);

    JBUI.setUserScaleFactor((float)ctx.getScale(USR_SCALE));

    ScaleContext ctx_noObjScale = ctx.copy();
    ctx_noObjScale.update(OBJ_SCALE.of(1));

    String[] cellIconsPaths = getCellIconsPaths();
    int count = cellIconsPaths.length;

    IconLoader.CachedImageIcon[] cellIcons = new IconLoader.CachedImageIcon[count];
    for (int i = 0; i < count; i++) {
      try {
        cellIcons[i] = new IconLoader.CachedImageIcon(new File(cellIconsPaths[i]).toURI().toURL());
      }
      catch (MalformedURLException e) {
        throw new RuntimeException(e);
      }
      cellIcons[i].updateScaleContext(ctx_noObjScale);
    }

    Icon scaledIcon = createCompositeIcon(ctx_noObjScale, cellIcons).scale((float)ctx.getScale(OBJ_SCALE));
    test(scaledIcon, ctx);
  }

  private void test(Icon icon, final ScaleContext ctx) {
    Pair<BufferedImage, Graphics2D> pair = createImageAndGraphics(ctx.getScale(SYS_SCALE), icon.getIconWidth(), icon.getIconHeight());
    BufferedImage iconImage = pair.first;
    Graphics2D g2d = pair.second;

    icon.paintIcon(null, g2d, 0, 0);

    if (shouldSaveGoldImage()) saveImage(iconImage, getGoldImagePath(ctx));

    BufferedImage goldImage = loadImage(getGoldImagePath(ctx));

    ImageComparator.compareAndAssert(
      new ImageComparator.AASmootherComparator(0.1, 0.1, new Color(0, 0, 0, 0)), goldImage, iconImage, null);
  }

  protected void assume(ScaleContext ctx) {}

  protected abstract ScalableIcon createCompositeIcon(ScaleContext ctx, Icon... cellIcons);

  protected abstract String[] getCellIconsPaths();

  protected abstract String getGoldImagePath(ScaleContext ctx);

  protected abstract boolean shouldSaveGoldImage();
}
