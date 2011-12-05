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

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.nio.MappedByteBuffer;
import java.nio.channels.FileChannel;

/**
 * @author max
 */
public class ReadOnlyMappedBufferWrapper extends MappedBufferWrapper {
  protected ReadOnlyMappedBufferWrapper(final File file, final int pos) {
    super(file, pos, file.length() - pos);
  }

  @Override
  protected MappedByteBuffer map() throws IOException {
    final FileInputStream stream = new FileInputStream(myFile);
    try {
      final FileChannel channel = stream.getChannel();
      try {
        return channel.map(FileChannel.MapMode.READ_ONLY, myPosition, myLength);
      }
      finally {
        channel.close();
      }
    }
    finally {
      stream.close();
    }
  }
}
