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
package com.intellij.util.ui;

import org.jetbrains.annotations.NotNull;

import java.awt.*;
import java.awt.image.BufferedImage;

/**
 * @author Konstantin Bulenkov
 */
public class ImageUtil {
  public static BufferedImage toBufferedImage(@NotNull Image image) {
    if (image instanceof BufferedImage) {
      return (BufferedImage)image;
    }

    @SuppressWarnings("UndesirableClassUsage")
    BufferedImage bufferedImage = new BufferedImage(image.getWidth(null), image.getHeight(null), BufferedImage.TYPE_INT_ARGB);
    Graphics2D g = bufferedImage.createGraphics();
    g.drawImage(image, 0, 0, null);
    g.dispose();
    return bufferedImage;
  }
}
