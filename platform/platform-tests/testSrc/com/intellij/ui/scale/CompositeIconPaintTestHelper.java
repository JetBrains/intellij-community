// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ui.scale;

import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.ScalableIcon;
import com.intellij.ui.icons.CachedImageIcon;
import com.intellij.ui.icons.CachedImageIconKt;
import com.intellij.ui.scale.paint.ImageComparator;

import javax.swing.*;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.nio.file.Path;
import java.util.function.BiFunction;

import static com.intellij.ui.scale.ScaleType.*;
import static com.intellij.ui.scale.TestScaleHelper.*;

/**
 * @author tav
 */
public abstract class CompositeIconPaintTestHelper {
  protected void test() {
    overrideJreHiDPIEnabled(true);

    BiFunction<Integer, Integer, Integer> bit2scale = (mask, bit) -> ((mask >> bit) & 0x1) + 1;
    for (int mask = 0; mask < 7; mask++) {
      int iconScale = bit2scale.apply(mask, 2);
      int usrScale = bit2scale.apply(mask, 1);
      int sysScale = bit2scale.apply(mask, 0);
      assert iconScale * usrScale * sysScale <= 4;
      test(ScaleContext.Companion.of(new Scale[]{SYS_SCALE.of(sysScale), USR_SCALE.of(usrScale), OBJ_SCALE.of(iconScale)}));
    }
  }

  private void test(final ScaleContext ctx) {
    assume(ctx);

    JBUIScale.setUserScaleFactor((float)ctx.getScale(USR_SCALE));

    ScaleContext ctx_noObjScale = ctx.copy();
    ctx_noObjScale.setScale(OBJ_SCALE.of(1));

    String[] cellIconsPaths = getCellIconsPaths();
    int count = cellIconsPaths.length;

    CachedImageIcon[] cellIcons = new CachedImageIcon[count];
    for (int i = 0; i < count; i++) {
      cellIcons[i] = CachedImageIconKt.createCachedIcon(Path.of(cellIconsPaths[i]), ctx_noObjScale);
    }

    Icon scaledIcon = createCompositeIcon(ctx_noObjScale, cellIcons).scale((float)ctx.getScale(OBJ_SCALE));
    test(scaledIcon, ctx);
  }

  private void test(Icon icon, ScaleContext ctx) {
    Pair<BufferedImage, Graphics2D> pair = createImageAndGraphics(ctx.getScale(SYS_SCALE), icon.getIconWidth(), icon.getIconHeight());
    BufferedImage iconImage = pair.first;
    Graphics2D g2d = pair.second;

    icon.paintIcon(null, g2d, 0, 0);

    if (shouldSaveGoldImage()) {
      saveImage(iconImage, getGoldImagePath(ctx));
    }

    BufferedImage goldImage = loadImage(Path.of(getGoldImagePath(ctx)));

    ImageComparator.compareAndAssert(
      new ImageComparator.AASmootherComparator(0.1, 0.1, new Color(0, 0, 0, 0)), goldImage, iconImage, null);
  }

  protected void assume(ScaleContext ctx) { }

  protected abstract ScalableIcon createCompositeIcon(ScaleContext ctx, Icon... cellIcons);

  protected abstract String[] getCellIconsPaths();

  protected abstract String getGoldImagePath(ScaleContext ctx);

  protected abstract boolean shouldSaveGoldImage();
}
