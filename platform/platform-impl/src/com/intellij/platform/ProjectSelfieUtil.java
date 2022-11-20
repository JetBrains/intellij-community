// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform;

import com.intellij.openapi.application.PathManagerEx;
import com.intellij.openapi.util.registry.Registry;
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
public final class ProjectSelfieUtil {
  public static boolean isEnabled() {
    return Registry.is("ide.project.loading.show.last.state", true);
  }

  public static @NotNull Path getSelfieLocation(@NotNull String projectWorkspaceId) {
    return PathManagerEx.getAppSystemDir().resolve("project-selfies").resolve(projectWorkspaceId + ".png");
  }

  public static Image readProjectSelfie(@NotNull String value, @NotNull ScaleContext scaleContext) throws IOException {
    Path location = getSelfieLocation(value);
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
