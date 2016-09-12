/*
 * Copyright (C) 2016 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.errorreport.crash;

import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.*;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.handler.codec.http.*;
import org.jetbrains.annotations.NotNull;

import java.util.function.Function;

/**
 * A simple test webserver: It listens for request at the given port, and for each request, delegates the job of obtaining the appropriate
 * response to the function set via {@link #setResponseSupplier(Function)}.
 */
public class LocalTestServer {
  private final int myPort;
  private Function<FullHttpRequest, FullHttpResponse> myResponseSupplier;
  private NioEventLoopGroup myEventLoopGroup;
  private Channel myChannel;

  public LocalTestServer(int port) {
    myPort = port;
  }

  public void setResponseSupplier(@NotNull Function<FullHttpRequest, FullHttpResponse> responseSupplier) {
    myResponseSupplier = responseSupplier;
  }

  public void start() throws Exception {
    ServerBootstrap b = new ServerBootstrap();
    myEventLoopGroup = new NioEventLoopGroup();
    b.group(myEventLoopGroup)
      .channel(NioServerSocketChannel.class)
      .childHandler(new ChannelInitializer<SocketChannel>() {
        @Override
        protected void initChannel(SocketChannel ch) throws Exception {
          ChannelPipeline p = ch.pipeline();
          p.addLast(new HttpServerCodec());
          p.addLast(new HttpContentDecompressor());
          p.addLast(new HttpObjectAggregator(8192));
          p.addLast(new ChannelInboundHandlerAdapter() {
            @Override
            public void channelReadComplete(ChannelHandlerContext ctx) throws Exception {
              ctx.flush();
            }

            @Override
            public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
              if (!(msg instanceof FullHttpRequest)) {
                return;
              }

              FullHttpResponse response = myResponseSupplier.apply((FullHttpRequest)msg);
              response.headers().set(HttpHeaderNames.CONTENT_TYPE, "text/plain");
              response.headers().set(HttpHeaderNames.CONTENT_LENGTH, response.content().readableBytes());
              ctx.write(response).addListener(ChannelFutureListener.CLOSE);
            }
          });
        }
      });

    myChannel = b.bind(myPort).sync().channel();
  }

  public void stop() {
    myChannel.close();
    myEventLoopGroup.shutdownGracefully();
  }
}
