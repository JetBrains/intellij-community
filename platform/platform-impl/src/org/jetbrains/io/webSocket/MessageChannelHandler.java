package org.jetbrains.io.webSocket;

import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.http.websocketx.*;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.io.ChannelBufferToString;
import org.jetbrains.io.SimpleChannelInboundHandlerAdapter;

import java.io.IOException;

@ChannelHandler.Sharable
final class MessageChannelHandler extends SimpleChannelInboundHandlerAdapter<WebSocketFrame> {
  private final WebSocketServer server;
  private final MessageServer messageServer;

  MessageChannelHandler(@NotNull WebSocketServer server, @NotNull MessageServer messageServer) {
    this.server = server;
    this.messageServer = messageServer;
  }

  @Override
  protected void messageReceived(ChannelHandlerContext context, WebSocketFrame message) throws Exception {
    WebSocketClient client = (WebSocketClient)context.attr(WebSocketHandshakeHandler.CLIENT).get();
    if (message instanceof CloseWebSocketFrame) {
      if (client != null) {
        try {
          server.disconnectClient(context, client, false);
        }
        finally {
          message.retain();
          client.disconnect((CloseWebSocketFrame)message);
        }
      }
    }
    else if (message instanceof PingWebSocketFrame) {
      context.channel().writeAndFlush(new PongWebSocketFrame(message.content()));
    }
    else if (message instanceof TextWebSocketFrame) {
      String text = ChannelBufferToString.readString(message.content());
      try {
        messageServer.message(client, text);
      }
      catch (Throwable e) {
        server.exceptionHandler.exceptionCaught(new IOException("Exception while handle message: " + text, e));
      }
    }
    else if (!(message instanceof PongWebSocketFrame)) {
      throw new UnsupportedOperationException(message.getClass().getName() + " frame types not supported");
    }
  }

  @Override
  public void channelInactive(ChannelHandlerContext context) throws Exception {
    Client client = context.attr(WebSocketHandshakeHandler.CLIENT).get();
    // if null, so, has already been explicitly removed
    if (client != null) {
      server.disconnectClient(context, client, false);
    }
  }

  @Override
  public void exceptionCaught(ChannelHandlerContext context, Throwable cause) throws Exception {
    try {
      server.exceptionHandler.exceptionCaught(cause);
    }
    finally {
      context.channel().close();
    }
  }
}
