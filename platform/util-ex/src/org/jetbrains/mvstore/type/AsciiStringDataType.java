/*
 * Copyright 2004-2020 H2 Group. Multiple-Licensed under the MPL 2.0,
 * and the EPL 1.0 (https://h2database.com/html/license.html).
 * Initial Developer: H2 Group
 */
package org.jetbrains.mvstore.type;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufUtil;
import io.netty.util.AsciiString;
import org.jetbrains.integratedBinaryPacking.IntBitPacker;
import org.jetbrains.mvstore.DataUtil;

public final class AsciiStringDataType implements KeyableDataType<AsciiString> {
    public static final KeyableDataType<AsciiString> INSTANCE = new AsciiStringDataType();

    private AsciiStringDataType() {
    }

    @Override
    public final AsciiString[] createStorage(int size) {
        return new AsciiString[size];
    }

    @Override
    public final int compare(AsciiString a, AsciiString b) {
        return a.compareTo(b);
    }

    @Override
    public int getMemory(AsciiString obj) {
        return DataUtil.VAR_INT_MAX_SIZE + obj.length();
    }

    @Override
    public int getFixedMemory() {
        return -1;
    }

    @Override
    public AsciiString read(ByteBuf buf) {
        int length = IntBitPacker.readVar(buf);
        int readerIndex = buf.readerIndex();
        AsciiString result = new AsciiString(ByteBufUtil.getBytes(buf, readerIndex, length, true), false);
        buf.readerIndex(readerIndex + length);
        return result;
    }

    @Override
    public void write(ByteBuf buf, AsciiString s) {
        int length = s.length();
      IntBitPacker.writeVar(buf, length);
      buf.writeBytes(s.array(), s.arrayOffset(), length);
    }
}

