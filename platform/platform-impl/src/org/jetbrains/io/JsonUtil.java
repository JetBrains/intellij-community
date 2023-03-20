// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.io;

import com.intellij.util.SmartList;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufUtil;
import io.netty.buffer.ByteBufUtilEx;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public final class JsonUtil {
  private static final String[] REPLACEMENT_CHARS;

  static {
    REPLACEMENT_CHARS = new String[128];
    for (int i = 0; i <= 31; i++) {
      REPLACEMENT_CHARS[i] = String.format("\\u%04x", i);
    }
    REPLACEMENT_CHARS['"'] = "\\\"";
    REPLACEMENT_CHARS['\\'] = "\\\\";
    REPLACEMENT_CHARS['\t'] = "\\t";
    REPLACEMENT_CHARS['\b'] = "\\b";
    REPLACEMENT_CHARS['\n'] = "\\n";
    REPLACEMENT_CHARS['\r'] = "\\r";
    REPLACEMENT_CHARS['\f'] = "\\f";
  }

  public static void escape(@NotNull CharSequence value, @NotNull StringBuilder sb) {
    int length = value.length();
    sb.ensureCapacity(sb.length() + length + 2);
    sb.append('"');
    int last = 0;
    for (int i = 0; i < length; i++) {
      char c = value.charAt(i);
      String replacement;
      if (c < 128) {
        replacement = REPLACEMENT_CHARS[c];
        if (replacement == null) {
          continue;
        }
      }
      else if (c == '\u2028') {
        replacement = "\\u2028";
      }
      else if (c == '\u2029') {
        replacement = "\\u2029";
      }
      else {
        continue;
      }
      if (last < i) {
        sb.append(value, last, i);
      }
      sb.append(replacement);
      last = i + 1;
    }
    if (last < length) {
      sb.append(value, last, length);
    }
    sb.append('"');
  }

  public static void escape(@NotNull CharSequence value, @NotNull ByteBuf buffer) {
    int length = value.length();
    buffer.ensureWritable(length * 2);
    buffer.writeByte('"');
    int last = 0;
    for (int i = 0; i < length; i++) {
      char c = value.charAt(i);
      String replacement;
      if (c < 128) {
        replacement = REPLACEMENT_CHARS[c];
        if (replacement == null) {
          continue;
        }
      }
      else if (c == '\u2028') {
        replacement = "\\u2028";
      }
      else if (c == '\u2029') {
        replacement = "\\u2029";
      }
      else {
        continue;
      }
      if (last < i) {
        ByteBufUtilEx.writeUtf8(buffer, value, last, i);
      }
      ByteBufUtil.writeAscii(buffer, replacement);
      last = i + 1;
    }
    if (last < length) {
      ByteBufUtilEx.writeUtf8(buffer, value, last, length);
    }
    buffer.writeByte('"');
  }

  @NotNull
  public static <T> List<T> nextList(@NotNull JsonReaderEx reader) {
    reader.beginArray();
    if (!reader.hasNext()) {
      reader.endArray();
      return Collections.emptyList();
    }

    List<T> list = new SmartList<>();
    readListBody(reader, list);
    reader.endArray();
    return list;
  }

  @NotNull
  public static Map<String, Object> nextObject(@NotNull JsonReaderEx reader) {
    Map<String, Object> map = new HashMap<>();
    reader.beginObject();
    while (reader.hasNext()) {
      map.put(reader.nextName(), nextAny(reader));
    }
    reader.endObject();
    return map;
  }

  @Nullable
  public static Object nextAny(JsonReaderEx reader)  {
    return switch (reader.peek()) {
      case BEGIN_ARRAY -> nextList(reader);
      case BEGIN_OBJECT -> nextObject(reader);
      case STRING -> reader.nextString();
      case NUMBER -> reader.nextDouble();
      case BOOLEAN -> reader.nextBoolean();
      case NULL -> {
        reader.nextNull();
        yield null;
      }
      default -> throw new IllegalStateException();
    };
  }

  public static <T> void readListBody(JsonReaderEx reader, List<T> list)  {
    do {
      //noinspection unchecked
      list.add((T)nextAny(reader));
    }
    while (reader.hasNext());
  }
}
