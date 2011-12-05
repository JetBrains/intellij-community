/*
 * Copyright 2000-2011 JetBrains s.r.o.
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
package com.intellij.util.io;

import org.jetbrains.annotations.NonNls;

import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.MappedByteBuffer;
import java.nio.channels.FileChannel;

/**
 * @author max
 */
public class ReadWriteMappedBufferWrapper extends MappedBufferWrapper {
  @NonNls private static final String RW = "rw";

  protected ReadWriteMappedBufferWrapper(final File file, final int offset, final int len) {
    super(file, offset, len);
  }

  @Override
  protected MappedByteBuffer map() throws IOException {
    final RandomAccessFile file = new RandomAccessFile(myFile, RW);
    try {
      final FileChannel channel = file.getChannel();
      try {
        return channel.map(FileChannel.MapMode.READ_WRITE, myPosition, myLength);
      }
      finally {
        channel.close();
      }
    }
    finally {
      file.close();
    }
  }
}
