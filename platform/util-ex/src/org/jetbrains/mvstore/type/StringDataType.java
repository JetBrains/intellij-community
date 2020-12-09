/*
 * Copyright 2004-2020 H2 Group. Multiple-Licensed under the MPL 2.0,
 * and the EPL 1.0 (https://h2database.com/html/license.html).
 * Initial Developer: H2 Group
 */
package org.jetbrains.mvstore.type;

import io.netty.buffer.ByteBuf;
import org.jetbrains.integratedBinaryPacking.IntBitPacker;
import org.jetbrains.mvstore.DataUtil;

import java.nio.charset.StandardCharsets;

/**
 * A string type.
 */
public class StringDataType implements KeyableDataType<String> {
    public static final KeyableDataType<String> INSTANCE = new StringDataType();

    private StringDataType() {
    }

    @Override
    public int getFixedMemory() {
        return -1;
    }

    public static final class AsciiStringDataType extends StringDataType {
        public static final KeyableDataType<String> INSTANCE = new AsciiStringDataType();

        private AsciiStringDataType() {
        }

        @Override
        public String read(ByteBuf buf) {
            int length = IntBitPacker.readVar(buf);
            int readerIndex = buf.readerIndex();
            String result = buf.toString(readerIndex, length, StandardCharsets.US_ASCII);
            buf.readerIndex(readerIndex + length);
            return result;
        }

        @Override
        public void write(ByteBuf buf, String s) {
            int length = s.length();
          IntBitPacker.writeVar(buf, length);
          buf.writeCharSequence(s, StandardCharsets.US_ASCII);
        }
    }

    @Override
    public final String[] createStorage(int size) {
        return new String[size];
    }

    @Override
    public final int compare(String a, String b) {
        return a.compareTo(b);
    }

    @Override
    public int getMemory(String obj) {
        return 24 + 2 * obj.length();
    }

    @Override
    public String read(ByteBuf buf) {
        return DataUtil.readString(buf);
    }

    @Override
    public void write(ByteBuf buf, String s) {
        DataUtil.writeString(buf, s);
    }
}

