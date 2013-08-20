/*
 * Copyright 2000-2013 JetBrains s.r.o.
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

import com.intellij.openapi.util.AtomicNotNullLazyValue;
import io.netty.buffer.ByteBuf;
import io.netty.channel.*;
import io.netty.handler.codec.compression.JZlibEncoder;
import io.netty.handler.codec.compression.JdkZlibDecoder;
import io.netty.handler.codec.compression.ZlibWrapper;
import io.netty.handler.codec.http.HttpMessage;
import io.netty.handler.ssl.SslHandler;
import io.netty.handler.stream.ChunkedWriteHandler;
import org.jetbrains.annotations.NotNull;

import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLEngine;
import java.security.KeyStore;
import java.security.Security;

@ChannelHandler.Sharable
final class PortUnificationServerHandler extends Decoder<ByteBuf> {
  private static final AtomicNotNullLazyValue<SSLContext> SSL_SERVER_CONTEXT = new AtomicNotNullLazyValue<SSLContext>() {
    @NotNull
    @Override
    protected SSLContext compute() {
      String algorithm = Security.getProperty("ssl.KeyManagerFactory.algorithm");
      if (algorithm == null) {
        algorithm = "SunX509";
      }

      try {
        KeyStore ks = KeyStore.getInstance("JKS");
        char[] password = "jetbrains".toCharArray();
        //noinspection IOResourceOpenedButNotSafelyClosed
        ks.load(getClass().getResourceAsStream("cert.jks"), password);
        KeyManagerFactory kmf = KeyManagerFactory.getInstance(algorithm);
        kmf.init(ks, password);
        SSLContext serverContext = SSLContext.getInstance("TLS");
        serverContext.init(kmf.getKeyManagers(), null, null);
        return serverContext;
      }
      catch (Exception e) {
        throw new RuntimeException(e);
      }
    }
  };

  private final boolean detectSsl;
  private final boolean detectGzip;

  private final DelegatingHttpRequestHandler delegatingHttpRequestHandler;

  public PortUnificationServerHandler() {
    this(new DelegatingHttpRequestHandler(), true, true);
  }

  private PortUnificationServerHandler(DelegatingHttpRequestHandler delegatingHttpRequestHandler, boolean detectSsl, boolean detectGzip) {
    this.delegatingHttpRequestHandler = delegatingHttpRequestHandler;
    this.detectSsl = detectSsl;
    this.detectGzip = detectGzip;
  }

  @Override
  protected void channelRead0(ChannelHandlerContext context, ByteBuf message) throws Exception {
    ByteBuf buffer = getBufferIfSufficient(message, 5, context);
    if (buffer == null) {
      message.release();
    }
    else {
      decode(context, buffer);
    }
  }

  private void decode(ChannelHandlerContext context, ByteBuf buffer) throws Exception {
    ChannelPipeline pipeline = context.pipeline();
    if (detectSsl && SslHandler.isEncrypted(buffer)) {
      SSLEngine engine = SSL_SERVER_CONTEXT.getValue().createSSLEngine();
      engine.setUseClientMode(false);
      pipeline.addLast(new SslHandler(engine), new ChunkedWriteHandler());
      pipeline.addLast(new PortUnificationServerHandler(delegatingHttpRequestHandler, false, detectGzip));
    }
    else {
      int magic1 = buffer.getUnsignedByte(buffer.readerIndex());
      int magic2 = buffer.getUnsignedByte(buffer.readerIndex() + 1);
      if (detectGzip && magic1 == 31 && magic2 == 139) {
        pipeline.addLast(new JZlibEncoder(ZlibWrapper.GZIP), new JdkZlibDecoder(ZlibWrapper.GZIP));
        pipeline.addLast(new PortUnificationServerHandler(delegatingHttpRequestHandler, detectSsl, false));
      }
      else {
        NettyUtil.initHttpHandlers(pipeline);
        pipeline.addLast(delegatingHttpRequestHandler);
        if (BuiltInServer.LOG.isDebugEnabled()) {
          pipeline.addLast(new ChannelOutboundHandlerAdapter() {
            @Override
            public void write(ChannelHandlerContext context, Object message, ChannelPromise promise) throws Exception {
              if (message instanceof HttpMessage) {
                BuiltInServer.LOG.debug("OUT HTTP:\n" + message);
              }
              super.write(context, message, promise);
            }
          });
        }
      }
    }
    // must be after new channels handlers addition (netty bug?)
    pipeline.remove(this);
    context.fireChannelRead(buffer);
  }

  @Override
  public void exceptionCaught(ChannelHandlerContext context, Throwable cause) throws Exception {
    NettyUtil.log(cause, BuiltInServer.LOG);
  }
}