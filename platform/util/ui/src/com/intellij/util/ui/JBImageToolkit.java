// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.ui;

import com.intellij.openapi.util.text.StringUtil;
import com.intellij.ui.svg.SvgImageDecoder;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import sun.awt.image.*;

import java.awt.*;
import java.awt.image.ImageObserver;
import java.awt.image.ImageProducer;
import java.io.BufferedInputStream;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLConnection;
import java.net.URLStreamHandler;
import java.util.Base64;
import java.util.function.Function;

public final class JBImageToolkit {

  public static Image createImage(String filename) {
    return createImage(new FileImageSource(filename) {
      @Override
      public ImageDecoder getDecoder(InputStream is) {
        return getWithCustomDecoders(this, is, super::getDecoder);
      }
    });
  }

  public static Image createImage(URL url) {
    return createImage(new URLImageSource(url) {
      @Override
      public ImageDecoder getDecoder(InputStream is) {
        return getWithCustomDecoders(this, is, super::getDecoder);
      }
    });
  }

  public static Image createImage(byte[] imagedata) {
    return createImage(imagedata, 0, imagedata.length);
  }

  public static Image createImage(byte[] data, int offset, int length) {
    return createImage(new ByteArrayImageSource(data, offset, length) {
      @Override
      public ImageDecoder getDecoder(InputStream is) {
        return getWithCustomDecoders(this, is, super::getDecoder);
      }
    });
  }

  public static Image createImage(ImageProducer producer) {
    // TODO optimize image (especially SVG) rendering for particular target size
    return Toolkit.getDefaultToolkit().createImage(producer);
  }

  public static boolean prepareImage(@NotNull Image img, int w, int h, @Nullable ImageObserver o) {
    // TODO optimize image (especially SVG) rendering for particular target size
    return Toolkit.getDefaultToolkit().prepareImage(img, w, h, o);
  }

  @ApiStatus.Internal
  public static URL tryBuildBase64Url(@NotNull String url) throws MalformedURLException {
    if (url.startsWith("data:image") && url.contains("base64")) {
      return new URL(null, url, JBImageToolkit.dataImageStreamUrlHandler);
    }
    return null;
  }

  @ApiStatus.Internal
  public static ImageDecoder getWithCustomDecoders(InputStreamImageSource source,
                                                   InputStream stream,
                                                   Function<InputStream, ImageDecoder> originalGetDecoder) {
    var bufferedStream = !stream.markSupported() ? new BufferedInputStream(stream) : stream;

    // GIF, JPEG, XBM, PNG
    var result = originalGetDecoder.apply(bufferedStream);
    if (result != null) return result;

    // TODO add support for org.apache.commons.imaging.ImageFormats - e.g. BMP, TGA, ICO
    return SvgImageDecoder.Companion.detect(source, bufferedStream, -1, -1);
  }

  private JBImageToolkit() {}
  private static final URLStreamHandler dataImageStreamUrlHandler = new DataImageURLStreamHandler();
  private static class DataImageURLStreamHandler extends URLStreamHandler {

    @Override
    protected URLConnection openConnection(URL u) {
      return new URLConnection(u) {

        @Override
        public void connect() {
          connected = true;
        }

        @Override
        public InputStream getInputStream() throws IOException {
          connect(); // Mimics the semantics of an actual URL connection.
          var parts = StringUtil.split(u.toString(), ",");
          if (parts.size() == 2) {
            var encodedImage = parts.get(1);
            return new ByteArrayInputStream(Base64.getDecoder().decode(encodedImage));
          } else {
            throw new IOException("Malformed data url: " + u);
          }
        }
      };
    }
  }
}