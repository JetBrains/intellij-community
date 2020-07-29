// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.util;

import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.SeekableByteChannel;
import java.nio.file.*;
import java.util.Set;

import static java.nio.file.StandardCopyOption.ATOMIC_MOVE;
import static java.nio.file.StandardOpenOption.CREATE_NEW;
import static java.nio.file.StandardOpenOption.WRITE;

/**
 * Storage level for {@link SVGLoaderCache} and {@link SVGLoaderPrebuilt}
 * Do not forget to update the hash computation {@link SVGLoaderCache} if changing the format
 */
@ApiStatus.Internal
public final class SVGLoaderCacheIO {
  private static final Set<OpenOption> OPEN_OPTION_SET = ContainerUtil.set(CREATE_NEW, WRITE);

  @Nullable
  @ApiStatus.Internal
  public static BufferedImage readImageFile(byte @NotNull [] bytes,
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

    // we may have a race condition:
    // thread A - is writing the cache
    // thread B - attempts to read the cache, fails, and attempts to remove the same cache file
    //
    // we write the cache file as atomic as possible with write&move strategy (worst, we'll write the same file twice)
    Path tmpFile = file.resolve(file + ".tmp" + System.currentTimeMillis());
    try {
      try (SeekableByteChannel channel = Files.newByteChannel(tmpFile, OPEN_OPTION_SET)) {
        channel.write(buff);
      }

      try {
        Files.move(tmpFile, file, ATOMIC_MOVE);
      }
      catch (AtomicMoveNotSupportedException e) {
        Files.move(tmpFile, file);
      }
    }
    catch (FileAlreadyExistsException e) {
      //parallel thread managed to create the same file, skip it
    }
    catch (Exception e) {
      deleteQuietly(file);
      throw e;
    }
    finally {
      deleteQuietly(tmpFile);
    }
  }

  private static void deleteQuietly(@NotNull Path path) {
    try {
      Files.deleteIfExists(path);
    }
    catch (Exception e) {
      //NOP
    }
  }
}
