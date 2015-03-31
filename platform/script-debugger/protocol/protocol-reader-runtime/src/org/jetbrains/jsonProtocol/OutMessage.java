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
package org.jetbrains.jsonProtocol;

import com.google.gson.stream.JsonWriter;
import com.intellij.openapi.vfs.CharsetToolkit;
import gnu.trove.TIntArrayList;
import gnu.trove.TIntHashSet;
import gnu.trove.TIntProcedure;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufAllocator;
import io.netty.buffer.ByteBufUtf8Writer;
import io.netty.buffer.ByteBufUtilEx;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.io.JsonUtil;

import java.io.IOException;
import java.util.Collection;
import java.util.List;
import java.util.Map;

public abstract class OutMessage {
  // todo don't want to risk - are we really can release it properly? heap buffer for now
  private final ByteBuf buffer = ByteBufAllocator.DEFAULT.heapBuffer();
  public final JsonWriter writer = new JsonWriter(new ByteBufUtf8Writer(buffer));

  private boolean finalized;

  protected OutMessage() {
    try {
      writer.beginObject();
    }
    catch (IOException e) {
      throw new RuntimeException(e);
    }
  }

  protected void beginArguments() throws IOException {
  }

  public final void writeEnum(String name, Enum<?> value) {
    try {
      beginArguments();
      writer.name(name).value(value.toString());
    }
    catch (IOException e) {
      throw new RuntimeException(e);
    }
  }

  public final void writeMap(String name, Map<String, String> value) {
    try {
      beginArguments();
      writer.name(name);
      writer.beginObject();
      for (Map.Entry<String, String> entry : value.entrySet()) {
        writer.name(entry.getKey()).value(entry.getValue());
      }
      writer.endObject();
    }
    catch (IOException e) {
      throw new RuntimeException(e);
    }
  }

  public final void writeInt(String name, int value) {
    try {
      beginArguments();
      writer.name(name).value(value);
    }
    catch (IOException e) {
      throw new RuntimeException(e);
    }
  }

  protected final void writeLongArray(String name, long[] value) {
    try {
      beginArguments();
      writer.name(name);
      writer.beginArray();
      for (long v : value) {
        writer.value(v);
      }
      writer.endArray();
    }
    catch (IOException e) {
      throw new RuntimeException(e);
    }
  }

  public final void writeDoubleArray(String name, double[] value) {
    try {
      beginArguments();
      writer.name(name);
      writer.beginArray();
      for (double v : value) {
        writer.value(v);
      }
      writer.endArray();
    }
    catch (IOException e) {
      throw new RuntimeException(e);
    }
  }

  public final void writeIntArray(@NotNull String name, @NotNull int[] value) {
    try {
      beginArguments();
      writer.name(name);
      writer.beginArray();
      for (int v : value) {
        writer.value(v);
      }
      writer.endArray();
    }
    catch (IOException e) {
      throw new RuntimeException(e);
    }
  }

  public final void writeIntSet(@NotNull String name, @NotNull TIntHashSet value) {
    try {
      beginArguments();
      writer.name(name);
      writer.beginArray();
      value.forEach(new TIntProcedure() {
        @Override
        public boolean execute(int value) {
          try {
            writer.value(value);
          }
          catch (IOException e) {
            throw new RuntimeException(e);
          }
          return true;
        }
      });
      writer.endArray();
    }
    catch (IOException e) {
      throw new RuntimeException(e);
    }

  }

  public final void writeIntList(@NotNull String name, @NotNull TIntArrayList value) {
    try {
      beginArguments();
      writer.name(name);
      writer.beginArray();
      for (int i = 0; i < value.size(); i++) {
        writer.value(value.getQuick(i));
      }
      writer.endArray();
    }
    catch (IOException e) {
      throw new RuntimeException(e);
    }
  }

  public final void writeSingletonIntArray(@NotNull String name, int value) {
    try {
      beginArguments();
      writer.name(name);
      writer.beginArray();
      writer.value(value);
      writer.endArray();
    }
    catch (IOException e) {
      throw new RuntimeException(e);
    }
  }

  public final <E extends OutMessage> void writeList(String name, List<E> value) {
    if (value == null || value.isEmpty()) {
      return;
    }

    try {
      beginArguments();
      writer.name(name);
      writer.beginArray();
      boolean isNotFirst = false;
      for (OutMessage item : value) {
        if (isNotFirst) {
          buffer.writeByte(',').writeByte(' ');
        }
        else {
          isNotFirst = true;
        }

        if (!item.finalized) {
          item.finalized = true;
          try {
            item.writer.endObject();
          }
          catch (IllegalStateException e) {
            if ("Nesting problem.".equals(e.getMessage())) {
              throw new RuntimeException(item.buffer.toString(CharsetToolkit.UTF8_CHARSET) + "\nparent:\n" + buffer.toString(CharsetToolkit.UTF8_CHARSET), e);
            }
            else {
              throw e;
            }
          }
        }

        buffer.writeBytes(item.buffer);
      }
      writer.endArray();
    }
    catch (IOException e) {
      throw new RuntimeException(e);
    }
  }

  public final void writeStringList(@NotNull String name, @NotNull Collection<String> value) {
    try {
      beginArguments();
      JsonWriters.writeStringList(writer, name, value);
    }
    catch (IOException e) {
      throw new RuntimeException(e);
    }
  }

  public final void writeEnumList(@NotNull String name, @NotNull Collection<? extends Enum<?>> values) {
    try {
      beginArguments();
      writer.name(name).beginArray();
      for (Enum<?> item : values) {
        writer.value(item.toString());
      }
      writer.endArray();
    }
    catch (IOException e) {
      throw new RuntimeException(e);
    }
  }

  public static void prepareWriteRaw(@NotNull OutMessage message, @NotNull String name) throws IOException {
    message.writer.name(name).nullValue();
    ByteBuf itemBuffer = message.buffer;
    itemBuffer.writerIndex(itemBuffer.writerIndex() - "null".length());
  }

  public static void doWriteRaw(@NotNull OutMessage message, @NotNull String rawValue) {
    ByteBufUtilEx.writeUtf8(message.buffer, rawValue);
  }

  public final void writeMessage(@NotNull String name, @NotNull OutMessage value) {
    try {
      beginArguments();
      prepareWriteRaw(this, name);

      if (!value.finalized) {
        value.close();
      }
      buffer.writeBytes(value.buffer);
    }
    catch (IOException e) {
      throw new RuntimeException(e);
    }
  }

  public void close() throws IOException {
    assert !finalized;
    finalized = true;
    writer.endObject();
    writer.close();
  }

  protected final void writeLong(String name, long value) {
    try {
      beginArguments();
      writer.name(name).value(value);
    }
    catch (IOException e) {
      throw new RuntimeException(e);
    }
  }

  public final void writeDouble(String name, double value) {
    try {
      beginArguments();
      writer.name(name).value(value);
    }
    catch (IOException e) {
      throw new RuntimeException(e);
    }
  }

  public final void writeBoolean(String name, boolean value) {
    try {
      beginArguments();
      writer.name(name).value(value);
    }
    catch (IOException e) {
      throw new RuntimeException(e);
    }
  }

  public final void writeString(@NotNull String name, @Nullable String value) {
    if (value != null) {
      writeNullableString(name, value);
    }
  }

  public final void writeString(@NotNull String name, CharSequence value) {
    if (value != null) {
      try {
        prepareWriteRaw(this, name);
        JsonUtil.escape(value, buffer);
      }
      catch (IOException e) {
        throw new RuntimeException(e);
      }
    }
  }

  public final void writeNullableString(@NotNull String name, @Nullable CharSequence value) {
    try {
      beginArguments();
      writer.name(name).value(value.toString());
    }
    catch (IOException e) {
      throw new RuntimeException(e);
    }
  }

  @NotNull
  @SuppressWarnings("UnusedDeclaration")
  public final ByteBuf getBuffer() {
    return buffer;
  }
}