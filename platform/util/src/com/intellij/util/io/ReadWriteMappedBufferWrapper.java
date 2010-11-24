/*
 * Copyright 2000-2009 JetBrains s.r.o.
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

/*
 * @author max
 */
package com.intellij.util.io;

import org.jetbrains.annotations.NonNls;

import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.MappedByteBuffer;
import java.nio.channels.FileChannel;

public class ReadWriteMappedBufferWrapper extends MappedBufferWrapper {
  @NonNls private static final String RW = "rw";

  public ReadWriteMappedBufferWrapper(final File file) {
    super(file, 0, file.length());
  }

  public ReadWriteMappedBufferWrapper(final File file, int offset, int len) {
    super(file, offset, len);
  }

  public MappedByteBuffer map() {
    MappedByteBuffer buf;
    try {
      buf = null;
      RandomAccessFile raf = null;
      FileChannel channel = null;
      try {
        raf = new RandomAccessFile(myFile, RW);
        channel = raf.getChannel();
        buf = channel.map(FileChannel.MapMode.READ_WRITE, myPosition, myLength);
      }
      catch (IOException e) {
        final MappingFailedException mapFailed =
          new MappingFailedException("Mapping failed: " + myFile.getAbsolutePath() + ", position=" + myPosition + ", length=" + myLength, e);
        LOG.error(mapFailed);
        throw mapFailed;
      }
      finally {
        if (channel != null) {
          channel.close();
        }
        if (raf != null) {
          raf.close();
        }
      }
    }
    catch (IOException e) {
      buf = null;
    }

    if (buf == null) {
      final MappingFailedException mapFailed =
        new MappingFailedException("Mapping failed: " + myFile.getAbsolutePath() + ", position=" + myPosition + ", length=" + myLength);
      LOG.error(mapFailed);
      throw mapFailed;
    }

    return buf;
  }
}