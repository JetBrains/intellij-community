// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.util.io;

import java.io.IOException;
import java.nio.MappedByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.nio.file.Path;

import static com.intellij.util.io.FileChannelUtil.unInterruptible;

public class ReadOnlyMappedBufferWrapper extends MappedBufferWrapper {
  protected ReadOnlyMappedBufferWrapper(Path file, final int pos) throws IOException {
    super(file, pos, Files.size(file) - pos);
  }

  @Override
  protected MappedByteBuffer map() throws IOException {
    try (FileChannel channel = unInterruptible(FileChannel.open(myFile))) {
      return channel.map(FileChannel.MapMode.READ_ONLY, myPosition, myLength);
    }
  }
}
