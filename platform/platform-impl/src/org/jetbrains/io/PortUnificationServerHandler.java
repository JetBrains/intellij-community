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
import io.netty.handler.codec.http.HttpResponse;
import io.netty.handler.ssl.SslHandler;
import io.netty.handler.stream.ChunkedWriteHandler;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.ide.BinaryRequestHandler;

import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLEngine;
import java.security.KeyStore;
import java.security.Security;
import java.util.UUID;

@ChannelHandler.Sharable
class PortUnificationServerHandler extends Decoder {
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
  protected void messageReceived(ChannelHandlerContext context, ByteBuf message) throws Exception {
    ByteBuf buffer = getBufferIfSufficient(message, 5, context);
    if (buffer == null) {
      message.release();
    }
    else {
      decode(context, buffer);
    }
  }

  protected void decode(ChannelHandlerContext context, ByteBuf buffer) throws Exception {
    ChannelPipeline pipeline = context.pipeline();
    if (detectSsl && SslHandler.isEncrypted(buffer)) {
      SSLEngine engine = SSL_SERVER_CONTEXT.getValue().createSSLEngine();
      engine.setUseClientMode(false);
      pipeline.addLast(new SslHandler(engine), new ChunkedWriteHandler(),
                       new PortUnificationServerHandler(delegatingHttpRequestHandler, false, detectGzip));
    }
    else {
      int magic1 = buffer.getUnsignedByte(buffer.readerIndex());
      int magic2 = buffer.getUnsignedByte(buffer.readerIndex() + 1);
      if (detectGzip && magic1 == 31 && magic2 == 139) {
        pipeline.addLast(new JZlibEncoder(ZlibWrapper.GZIP), new JdkZlibDecoder(ZlibWrapper.GZIP),
                         new PortUnificationServerHandler(delegatingHttpRequestHandler, detectSsl, false));
      }
      else if (isHttp(magic1, magic2)) {
        NettyUtil.initHttpHandlers(pipeline);
        pipeline.addLast(delegatingHttpRequestHandler);
        if (BuiltInServer.LOG.isDebugEnabled()) {
          pipeline.addLast(new ChannelHandlerAdapter() {
            @Override
            public void write(ChannelHandlerContext context, Object message, ChannelPromise promise) throws Exception {
              if (message instanceof HttpResponse) {
//                BuiltInServer.LOG.debug("OUT HTTP:\n" + message);
                HttpResponse response = (HttpResponse)message;
                BuiltInServer.LOG.debug("OUT HTTP: " + response.getStatus().code() + " " + response.headers().get("Content-type"));
              }
              super.write(context, message, promise);
            }
          });
        }
      }
      else if (magic1 == 'C' && magic2 == 'H') {
        buffer.skipBytes(2);
        pipeline.addLast(new CustomHandlerDelegator());
      }
      else {
        BuiltInServer.LOG.warn("unknown request, first two bytes " + magic1 + " " + magic2);
        context.close();
      }
    }
    // must be after new channels handlers addition (netty bug?)
    ensureThatExceptionHandlerIsLast(pipeline);
    pipeline.remove(this);
    context.fireChannelRead(buffer);
  }

  private static void ensureThatExceptionHandlerIsLast(ChannelPipeline pipeline) {
    ChannelHandler exceptionHandler = ChannelExceptionHandler.getInstance();
    if (pipeline.last() != exceptionHandler || pipeline.context(exceptionHandler) == null) {
      return;
    }

    pipeline.remove(exceptionHandler);
    pipeline.addLast(exceptionHandler);
  }

  private static boolean isHttp(int magic1, int magic2) {
    return
      magic1 == 'G' && magic2 == 'E' || // GET
      magic1 == 'P' && magic2 == 'O' || // POST
      magic1 == 'P' && magic2 == 'U' || // PUT
      magic1 == 'H' && magic2 == 'E' || // HEAD
      magic1 == 'O' && magic2 == 'P' || // OPTIONS
      magic1 == 'P' && magic2 == 'A' || // PATCH
      magic1 == 'D' && magic2 == 'E' || // DELETE
      magic1 == 'T' && magic2 == 'R' || // TRACE
      magic1 == 'C' && magic2 == 'O';   // CONNECT
  }

  private static class CustomHandlerDelegator extends Decoder {
    private static final int UUID_LENGTH = 16;

    @Override
    protected void messageReceived(ChannelHandlerContext context, ByteBuf message) throws Exception {
      ByteBuf buffer = getBufferIfSufficient(message, UUID_LENGTH, context);
      if (buffer == null) {
        message.release();
      }
      else {
        UUID uuid = new UUID(buffer.readLong(), buffer.readLong());
        for (BinaryRequestHandler customHandler : BinaryRequestHandler.EP_NAME.getExtensions()) {
          if (uuid.equals(customHandler.getId())) {
            ChannelPipeline pipeline = context.pipeline();
            pipeline.addLast(customHandler.getInboundHandler());
            ensureThatExceptionHandlerIsLast(pipeline);
            pipeline.remove(this);
            context.fireChannelRead(buffer);
            break;
          }
        }
      }
    }
  }
}