// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.util;

import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.SeekableByteChannel;
import java.nio.file.Files;
import java.nio.file.OpenOption;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.Set;

/**
 * Storage level for {@link SVGLoaderCache} and {@link SVGLoaderPrebuilt}
 * Do not forget to update the hash computation {@link SVGLoaderCache} if changing the format
 */
@ApiStatus.Internal
public class SVGLoaderCacheIO {
  private static final Set<OpenOption> OPEN_OPTION_SET = ContainerUtil
    .set(StandardOpenOption.CREATE, StandardOpenOption.WRITE, StandardOpenOption.TRUNCATE_EXISTING);

  @Nullable
  @ApiStatus.Internal
  public static BufferedImage readImageFile(@NotNull byte[] bytes,
                                            @NotNull ImageLoader.Dimension2DDouble docSize) {
    ByteBuffer buff = ByteBuffer.wrap(bytes);

    double width = buff.getDouble();
    double height = buff.getDouble();
    int actualWidth = buff.getInt();
    int actualHeight = buff.getInt();

    //sanity check to make sure file is not corrupted
    if (actualWidth <= 0 || actualHeight <= 0 || actualWidth * actualHeight <= 0) return null;

    @SuppressWarnings("UndesirableClassUsage")
    //we do not need a specific image here, it will be wrapped later
    BufferedImage image = new BufferedImage(actualWidth, actualHeight, BufferedImage.TYPE_INT_ARGB);

    for (int y = 0; y < actualHeight; y++) {
      for (int x = 0; x < actualWidth; x++) {
        image.setRGB(x, y, buff.getInt());
      }
    }

    docSize.setSize(width, height);
    return image;
  }

  @ApiStatus.Internal
  public static void writeImageFile(@NotNull Path file,
                                    @NotNull BufferedImage image,
                                    @NotNull ImageLoader.Dimension2DDouble size) throws IOException {

    int actualWidth = image.getWidth();
    int actualHeight = image.getHeight();

    ByteBuffer buff = ByteBuffer.allocate(actualHeight * actualWidth * Integer.BYTES + 2 * Double.BYTES + 2 * Integer.BYTES);
    buff.putDouble(size.getWidth());
    buff.putDouble(size.getHeight());
    buff.putInt(actualWidth);
    buff.putInt(actualHeight);

    for (int y = 0; y < actualHeight; y++) {
      for (int x = 0; x < actualWidth; x++) {
        buff.putInt(image.getRGB(x, y));
      }
    }

    buff.flip();

    Files.createDirectories(file.getParent());
    try (SeekableByteChannel channel = Files.newByteChannel(file, OPEN_OPTION_SET)) {
      channel.write(buff);
    }
  }
}
