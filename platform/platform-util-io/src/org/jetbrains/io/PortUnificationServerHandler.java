// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.io;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.AtomicNotNullLazyValue;
import io.netty.buffer.ByteBuf;
import io.netty.channel.*;
import io.netty.handler.codec.compression.ZlibCodecFactory;
import io.netty.handler.codec.compression.ZlibWrapper;
import io.netty.handler.codec.http.HttpResponse;
import io.netty.handler.ssl.SslContext;
import io.netty.handler.ssl.SslContextBuilder;
import io.netty.handler.ssl.SslHandler;
import io.netty.handler.stream.ChunkedWriteHandler;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.ide.BinaryRequestHandler;

import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLEngine;
import java.io.InputStream;
import java.security.KeyStore;
import java.security.Security;
import java.util.UUID;

@ChannelHandler.Sharable
final class PortUnificationServerHandler extends Decoder {
  // https://stackoverflow.com/questions/33827789/self-signed-certificate-dnsname-components-must-begin-with-a-letter
  // https://github.com/kaikramer/keystore-explorer (use cert.cet as cert ext template)
  // keytool -genkey -keyalg EC -keysize 256 -alias selfsigned -keystore cert.jks -storepass jetbrains -validity 10000 -ext 'san=dns:localhost,dns:*.localhost,dns:*.dev,dns:*.local'
  @SuppressWarnings("SpellCheckingInspection")
  private static final AtomicNotNullLazyValue<SslContext> SSL_SERVER_CONTEXT = new AtomicNotNullLazyValue<SslContext>() {
    @NotNull
    @Override
    protected SslContext compute() {
      String algorithm = Security.getProperty("ssl.KeyManagerFactory.algorithm");
      if (algorithm == null) {
        algorithm = "SunX509";
      }

      try {
        KeyStore ks = KeyStore.getInstance("JCEKS");
        char[] password = "jb".toCharArray();
        String keyStoreResourceName = "cert.jceks";
        InputStream keyStoreData = getClass().getResourceAsStream(keyStoreResourceName);
        if (keyStoreData == null) {
          throw new RuntimeException("Cannot find " + keyStoreResourceName);
        }

        ks.load(keyStoreData, password);
        KeyManagerFactory keyManagerFactory = KeyManagerFactory.getInstance(algorithm);
        keyManagerFactory.init(ks, password);
        return SslContextBuilder.forServer(keyManagerFactory)
          .build();
      }
      catch (Exception e) {
        throw new RuntimeException(e);
      }
    }
  };

  private final boolean detectSsl;
  private final boolean detectGzip;

  private final DelegatingHttpRequestHandler delegatingHttpRequestHandler;

  PortUnificationServerHandler() {
    this(new DelegatingHttpRequestHandler(), true, true);
  }

  private PortUnificationServerHandler(@NotNull DelegatingHttpRequestHandler delegatingHttpRequestHandler, boolean detectSsl, boolean detectGzip) {
    this.delegatingHttpRequestHandler = delegatingHttpRequestHandler;
    this.detectSsl = detectSsl;
    this.detectGzip = detectGzip;
  }

  @Override
  protected void messageReceived(@NotNull ChannelHandlerContext context, @NotNull ByteBuf input) {
    ByteBuf buffer = getBufferIfSufficient(input, 5, context);
    if (buffer != null) {
      decode(context, buffer);
    }
  }

  private void decode(@NotNull ChannelHandlerContext context, @NotNull ByteBuf buffer) {
    ChannelPipeline pipeline = context.pipeline();
    if (detectSsl && SslHandler.isEncrypted(buffer)) {
      SSLEngine engine = SSL_SERVER_CONTEXT.getValue().newEngine(context.alloc());
      engine.setUseClientMode(false);
      pipeline.addLast(new SslHandler(engine), new ChunkedWriteHandler(),
                       new PortUnificationServerHandler(delegatingHttpRequestHandler, false, detectGzip));
    }
    else {
      int magic1 = buffer.getUnsignedByte(buffer.readerIndex());
      int magic2 = buffer.getUnsignedByte(buffer.readerIndex() + 1);
      if (detectGzip && magic1 == 31 && magic2 == 139) {
        pipeline.addLast(ZlibCodecFactory.newZlibEncoder(ZlibWrapper.GZIP), ZlibCodecFactory.newZlibDecoder(ZlibWrapper.GZIP),
                         new PortUnificationServerHandler(delegatingHttpRequestHandler, detectSsl, false));
      }
      else if (isHttp(magic1, magic2)) {
        NettyUtil.addHttpServerCodec(pipeline);
        pipeline.addLast("delegatingHttpHandler", delegatingHttpRequestHandler);
        Logger logger = Logger.getInstance(BuiltInServer.class);
        if (logger.isDebugEnabled()) {
          pipeline.addLast(new ChannelOutboundHandlerAdapter() {
            @Override
            public void write(ChannelHandlerContext context, Object message, ChannelPromise promise) throws Exception {
              if (message instanceof HttpResponse) {
                HttpResponse response = (HttpResponse)message;
                logger.debug("OUT HTTP: " + response.toString());
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
        Logger.getInstance(BuiltInServer.class).warn("unknown request, first two bytes " + magic1 + " " + magic2);
        context.close();
      }
    }

    // must be after new channels handlers addition (netty bug?)
    pipeline.remove(this);
    // Buffer will be automatically released after messageReceived, but we pass it to next handler, and next handler will also release, so, we must retain.
    // We can introduce Decoder.isAutoRelease, but in this case, if error will be thrown while we are executing, buffer will not be released.
    // So, it is robust solution just always release (Decoder does) and just retain (we - client) if autorelease behavior is not suitable.
    buffer.retain();
    // we must fire channel read - new added handler must read buffer
    context.fireChannelRead(buffer);
  }

  @Override
  public void exceptionCaught(ChannelHandlerContext context, Throwable cause) {
    NettyUtil.logAndClose(cause, Logger.getInstance(BuiltInServer.class), context.channel());
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
    protected void messageReceived(@NotNull ChannelHandlerContext context, @NotNull ByteBuf input) {
      ByteBuf buffer = getBufferIfSufficient(input, UUID_LENGTH, context);
      if (buffer == null) {
        return;
      }

      UUID uuid = new UUID(buffer.readLong(), buffer.readLong());
      for (BinaryRequestHandler customHandler : BinaryRequestHandler.EP_NAME.getExtensions()) {
        if (uuid.equals(customHandler.getId())) {
          ChannelPipeline pipeline = context.pipeline();
          pipeline.addLast(customHandler.getInboundHandler(context));
          pipeline.addLast(ChannelExceptionHandler.getInstance());
          pipeline.remove(this);

          context.fireChannelRead(buffer);
          break;
        }
      }
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext context, Throwable cause) {
      NettyUtil.logAndClose(cause, Logger.getInstance(BuiltInServer.class), context.channel());
    }
  }
}