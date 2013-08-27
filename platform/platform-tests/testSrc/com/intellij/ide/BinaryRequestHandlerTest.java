package com.intellij.ide;

import com.intellij.openapi.util.AsyncResult;
import com.intellij.testFramework.LightPlatformTestCase;
import com.intellij.util.Consumer;
import com.intellij.util.net.NetUtils;
import io.netty.bootstrap.Bootstrap;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.Channel;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandler;
import io.netty.channel.ChannelInitializer;
import io.netty.util.CharsetUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.ide.BinaryRequestHandler;
import org.jetbrains.ide.BuiltInServerManager;
import org.jetbrains.io.ChannelExceptionHandler;
import org.jetbrains.io.Decoder;
import org.jetbrains.io.NettyUtil;

import java.util.UUID;

public class BinaryRequestHandlerTest extends LightPlatformTestCase {
  public void test() throws InterruptedException {
    final String text = "Hello!";
    final AsyncResult<String> result = new AsyncResult<String>();

    Bootstrap bootstrap = NettyUtil.oioClientBootstrap().handler(new ChannelInitializer() {
      @Override
      protected void initChannel(Channel channel) throws Exception {
        channel.pipeline().addLast(new Decoder() {
          @Override
          protected void channelRead0(ChannelHandlerContext context, ByteBuf message) throws Exception {
            int requiredLength = 4 + text.length();
            ByteBuf buffer = getBufferIfSufficient(message, requiredLength, context);
            if (buffer == null) {
              message.release();
            }
            else {
              String response = buffer.toString(buffer.readerIndex(), requiredLength, CharsetUtil.UTF_8);
              buffer.skipBytes(requiredLength);
              buffer.release();
              result.setDone(response);
            }
          }
        }, ChannelExceptionHandler.getInstance());
      }
    });

    int port = BuiltInServerManager.getInstance().waitForStart().getPort();
    Channel channel = bootstrap.connect(NetUtils.getLoopbackAddress(), port).syncUninterruptibly().channel();
    ByteBuf buffer = channel.alloc().buffer();
    buffer.writeByte('C');
    buffer.writeByte('H');
    buffer.writeLong(MyBinaryRequestHandler.ID.getMostSignificantBits());
    buffer.writeLong(MyBinaryRequestHandler.ID.getLeastSignificantBits());

    ByteBuf message = Unpooled.copiedBuffer(text, CharsetUtil.UTF_8);
    buffer.writeShort(message.readableBytes());

    channel.write(buffer);
    channel.writeAndFlush(message).syncUninterruptibly();

    try {
      result.doWhenRejected(new Consumer<String>() {
        @Override
        public void consume(String error) {
          fail(error);
        }
      });

      assertEquals("got-" + text, result.getResultSync(5000));
    }
    finally {
      channel.close();
    }
  }

  static class MyBinaryRequestHandler extends BinaryRequestHandler {
    private static final UUID ID = UUID.fromString("E5068DD6-1DB7-437C-A3FC-3CA53B6E1AC9");

    @NotNull
    @Override
    public UUID getId() {
      return ID;
    }

    @Override
    public ChannelInboundHandler getInboundHandler() {
      return new MyDecoder();
    }

    private static class MyDecoder extends Decoder {
      private State state = State.HEADER;
      private int contentLength = -1;

      private enum State {
        HEADER, CONTENT
      }

      @Override
      protected void channelRead0(ChannelHandlerContext context, ByteBuf message) throws Exception {
        while (true) {
          switch (state) {
            case HEADER: {
              ByteBuf buffer = getBufferIfSufficient(message, 2, context);
              if (buffer == null) {
                message.release();
                return;
              }

              contentLength = buffer.readUnsignedShort();
              state = State.CONTENT;
            }

            case CONTENT: {
              ByteBuf buffer = getBufferIfSufficient(message, contentLength, context);
              if (buffer == null) {
                message.release();
                return;
              }

              String messageText = buffer.toString(buffer.readerIndex(), contentLength, CharsetUtil.UTF_8);
              buffer.skipBytes(contentLength);
              state = State.HEADER;
              context.writeAndFlush(Unpooled.copiedBuffer("got-" + messageText, CharsetUtil.UTF_8));
            }
          }
        }
      }
    }
  }
}