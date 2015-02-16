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
package org.jetbrains.io;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.CompositeByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;

public abstract class Decoder extends ChannelInboundHandlerAdapter {
  // Netty MessageAggregator default value
  protected static final int DEFAULT_MAX_COMPOSITE_BUFFER_COMPONENTS = 1024;

  private ByteBuf cumulation;

  @Override
  public final void channelRead(ChannelHandlerContext context, Object message) throws Exception {
    if (message instanceof ByteBuf) {
      ByteBuf input = (ByteBuf)message;
      try {
        messageReceived(context, input);
      }
      finally {
        // client should release buffer as soon as possible, so, input could be released already
        if (input.refCnt() > 0) {
          input.release();
        }
      }
    }
    else {
      context.fireChannelRead(message);
    }
  }

  protected abstract void messageReceived(@NotNull ChannelHandlerContext context, @NotNull ByteBuf input) throws Exception;

  public interface FullMessageConsumer<T> {
    T contentReceived(@NotNull ByteBuf input, @NotNull ChannelHandlerContext context, boolean isCumulateBuffer) throws IOException;
  }

  @Nullable
  protected final <T> T readContent(@NotNull ByteBuf input, @NotNull ChannelHandlerContext context, int contentLength, @NotNull FullMessageConsumer<T> fullMessageConsumer) throws IOException {
    ByteBuf buffer = getBufferIfSufficient(input, contentLength, context);
    if (buffer == null) {
      return null;
    }

    boolean isCumulateBuffer = buffer != input;
    int oldReaderIndex = input.readerIndex();
    try {
      return fullMessageConsumer.contentReceived(buffer, context, isCumulateBuffer);
    }
    finally {
      if (isCumulateBuffer) {
        // cumulation buffer - release it
        buffer.release();
      }
      else {
        buffer.readerIndex(oldReaderIndex + contentLength);
      }
    }
  }

  @Nullable
  protected final ByteBuf getBufferIfSufficient(@NotNull ByteBuf input, int requiredLength, @NotNull ChannelHandlerContext context) {
    if (!input.isReadable()) {
      return null;
    }

    if (cumulation == null) {
      if (input.readableBytes() < requiredLength) {
        cumulation = input;
        input.retain();
        input.touch();
        return null;
      }
      else {
        return input;
      }
    }
    else {
      int currentAccumulatedByteCount = cumulation.readableBytes();
      if ((currentAccumulatedByteCount + input.readableBytes()) < requiredLength) {
        CompositeByteBuf compositeByteBuf;
        if ((cumulation instanceof CompositeByteBuf)) {
          compositeByteBuf = (CompositeByteBuf)cumulation;
        }
        else {
          compositeByteBuf = context.alloc().compositeBuffer(DEFAULT_MAX_COMPOSITE_BUFFER_COMPONENTS);
          compositeByteBuf.addComponent(cumulation);
          cumulation = compositeByteBuf;
        }

        compositeByteBuf.addComponent(input);
        input.retain();
        input.touch();
        return null;
      }
      else {
        CompositeByteBuf buffer;
        if (cumulation instanceof CompositeByteBuf) {
          buffer = (CompositeByteBuf)cumulation;
          buffer.addComponent(input);
        }
        else {
          // may be it will be used by client to cumulate something - don't set artificial restriction (2)
          buffer = context.alloc().compositeBuffer(DEFAULT_MAX_COMPOSITE_BUFFER_COMPONENTS);
          buffer.addComponents(cumulation, input);
        }

        // we don't set writerIndex on addComponent, it is clear to set it to requiredLength here
        buffer.writerIndex(requiredLength);

        input.skipBytes(requiredLength - currentAccumulatedByteCount);
        input.retain();
        input.touch();
        cumulation = null;
        return buffer;
      }
    }
  }

  @Override
  public void channelInactive(ChannelHandlerContext context) throws Exception {
    try {
      if (cumulation != null) {
        cumulation.release();
        cumulation = null;
      }
    }
    finally {
      super.channelInactive(context);
    }
  }
}