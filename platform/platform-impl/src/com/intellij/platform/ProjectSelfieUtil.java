// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.platform;

import com.intellij.openapi.wm.impl.IdeFrameImpl;
import com.intellij.ui.scale.ScaleContext;
import com.intellij.util.ui.ImageUtil;
import org.jetbrains.annotations.NotNull;

import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.stream.MemoryCacheImageInputStream;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;

// will be used very earlier, better to not use Kotlin
final class ProjectSelfieUtil {
  public static Image readProjectSelfie(@NotNull String value, @NotNull ScaleContext scaleContext) throws IOException {
    Path location = IdeFrameImpl.getSelfieLocation(value);
    BufferedImage bufferedImage;
    try (InputStream input = Files.newInputStream(location)) {
      ImageReader reader = ImageIO.getImageReadersByFormatName("png").next();
      try (MemoryCacheImageInputStream stream = new MemoryCacheImageInputStream(input)) {
        reader.setInput(stream, true, true);
        bufferedImage = reader.read(0, null);
      }
      finally {
        reader.dispose();
      }
    }
    catch (NoSuchFileException ignore) {
      return null;
    }

    return ImageUtil.ensureHiDPI(bufferedImage, scaleContext);
  }
}
