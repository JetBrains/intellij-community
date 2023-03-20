// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.util.net;

import com.google.common.net.HostAndPort;
import com.google.common.net.InetAddresses;
import com.google.common.net.InternetDomainName;
import com.intellij.UtilBundle;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.util.NlsSafe;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.openapi.util.io.StreamUtil;
import com.intellij.openapi.util.text.StringUtilRt;
import com.intellij.util.SystemProperties;
import com.intellij.util.io.CountingGZIPInputStream;
import com.intellij.util.io.IdeUtilIoBundle;
import com.intellij.util.io.IoService;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.*;
import java.util.Calendar;

public final class NetUtils {
  private static final Logger LOG = Logger.getInstance(NetUtils.class);

  private NetUtils() { }

  public static boolean canConnectToSocket(String host, int port) {
    if (isLocalhost(host)) {
      return !canBindToLocalSocket(host, port);
    }
    else {
      return canConnectToRemoteSocket(host, port);
    }
  }

  /** @deprecated use {@link InetAddress#getLoopbackAddress()} */
  @Deprecated(forRemoval = true)
  public static InetAddress getLoopbackAddress() {
    return InetAddress.getLoopbackAddress();
  }

  public static boolean isLocalhost(@NotNull @NlsSafe String hostName) {
    return hostName.equalsIgnoreCase("localhost") || hostName.equals("127.0.0.1") || hostName.equals("::1");
  }

  private static boolean canBindToLocalSocket(String host, int port) {
    try (ServerSocket socket = new ServerSocket()) {
      //it looks like this flag should be set but it leads to incorrect results for NodeJS under Windows
      //socket.setReuseAddress(true);
      socket.bind(new InetSocketAddress(host, port));
      return true;
    }
    catch (IOException e) {
      LOG.debug(e);
      return false;
    }
  }

  public static boolean canConnectToRemoteSocket(String host, int port) {
    try {
      Socket socket = new Socket(host, port);
      socket.close();
      return true;
    }
    catch (IOException ignored) {
      return false;
    }
  }

  public static int findAvailableSocketPort() throws IOException {
    try (ServerSocket serverSocket = new ServerSocket(0)) {
      int port = serverSocket.getLocalPort();
      // workaround for linux : calling close() immediately after opening socket
      // may result that socket is not closed
      //noinspection SynchronizationOnLocalVariableOrMethodParameter
      synchronized (serverSocket) {
        try {
          //noinspection WaitNotInLoop
          serverSocket.wait(1);
        }
        catch (InterruptedException e) {
          LOG.error(e);
        }
      }
      return port;
    }
  }

  public static int tryToFindAvailableSocketPort(int defaultPort) {
    try {
      return findAvailableSocketPort();
    }
    catch (IOException ignored) {
      return defaultPort;
    }
  }

  public static int tryToFindAvailableSocketPort() {
    return tryToFindAvailableSocketPort(-1);
  }

  public static int[] findAvailableSocketPorts(int capacity) throws IOException {
    final int[] ports = new int[capacity];
    final ServerSocket[] sockets = new ServerSocket[capacity];

    for (int i = 0; i < capacity; i++) {
      @SuppressWarnings({"IOResourceOpenedButNotSafelyClosed", "SocketOpenedButNotSafelyClosed"})
      ServerSocket serverSocket = new ServerSocket(0);
      sockets[i] = serverSocket;
      ports[i] = serverSocket.getLocalPort();
    }
    //workaround for linux : calling close() immediately after opening socket
    //may result that socket is not closed
    //noinspection SynchronizationOnLocalVariableOrMethodParameter
    synchronized (sockets) {
      try {
        //noinspection WaitNotInLoop
        sockets.wait(1);
      }
      catch (InterruptedException e) {
        LOG.error(e);
      }
    }

    for (ServerSocket socket : sockets) {
      socket.close();
    }
    return ports;
  }

  public static String getLocalHostString() {
    // HACK for Windows with ipv6
    @NlsSafe String localHostString = "localhost";
    try {
      final InetAddress localHost = InetAddress.getByName(localHostString);
      if ((localHost.getAddress().length != 4 && SystemInfo.isWindows) ||
          (localHost.getAddress().length == 4 && SystemInfo.isMac)) {
        localHostString = "127.0.0.1";
      }
    }
    catch (UnknownHostException ignored) {
    }
    return localHostString;
  }

  private static void updateIndicator(ProgressIndicator indicator, long downloadSpeed, long bytesDownloaded, long contentLength, boolean progressDescription) {
    double fraction = (double)bytesDownloaded / contentLength;
    if (progressDescription) {
      int rankForContentLength = StringUtilRt.rankForFileSize(contentLength);
      String formattedDownloadSpeed =
        StringUtilRt.formatFileSize(downloadSpeed) + "⧸s";
      String formattedContentLength =
        StringUtilRt.formatFileSize(contentLength, " ", rankForContentLength);
      String formattedTotalProgress =
        StringUtilRt.formatFileSize(bytesDownloaded, " ", rankForContentLength);

      @NlsSafe
      String indicatorText = String.format("<html><code>%.0f%% · %s⧸%s · %s</code></html>", fraction * 100,
                                           formattedTotalProgress,
                                           formattedContentLength,
                                           formattedDownloadSpeed);
      indicator.setText2(indicatorText);
    }
    indicator.setFraction(fraction);
  }

  /**
   * @deprecated use {@link #copyStreamContent(ProgressIndicator, InputStream, OutputStream, long)} instead
   */
  @Deprecated
  public static int copyStreamContent(@Nullable ProgressIndicator indicator,
                                      @NotNull InputStream inputStream,
                                      @NotNull OutputStream outputStream,
                                      int expectedContentLength) throws IOException, ProcessCanceledException {
    return (int)copyStreamContent(indicator, inputStream, outputStream, (long)expectedContentLength);
  }

  public static long copyStreamContent(@Nullable ProgressIndicator indicator,
                                       @NotNull InputStream inputStream,
                                       @NotNull OutputStream outputStream,
                                       long expectedContentLength) throws IOException, ProcessCanceledException {
    return copyStreamContent(indicator, inputStream, outputStream, expectedContentLength, false);
  }

  /**
   * @param indicator             progress indicator
   * @param inputStream           source stream
   * @param outputStream          destination stream
   * @param expectedContentLength expected content length in bytes, used in progress indicator (negative means unknown length).
   *                              For gzipped content, it's an expected length of gzipped/compressed content.
   *                              E.g. for HTTP, it means how many bytes should be sent over the network.
   * @param progressDescription   when enabled, additional information like download speed and downloaded part size is shown
   *                              in indicator's text2 property
   * @return the total number of bytes written to the destination stream (may exceed expectedContentLength for gzipped content)
   * @throws IOException              if IO error occur
   * @throws ProcessCanceledException if process was canceled.
   */
  public static long copyStreamContent(@Nullable ProgressIndicator indicator,
                                       @NotNull InputStream inputStream,
                                       @NotNull OutputStream outputStream,
                                       long expectedContentLength,
                                       boolean progressDescription) throws IOException, ProcessCanceledException {
    if (indicator != null) {
      indicator.checkCanceled();
      indicator.setIndeterminate(expectedContentLength <= 0);
    }
    CountingGZIPInputStream gzipStream = inputStream instanceof CountingGZIPInputStream ? (CountingGZIPInputStream)inputStream : null;
    byte[] buffer = new byte[StreamUtil.BUFFER_SIZE];
    int count;
    long bytesWritten = 0;
    long bytesRead = 0;
    long startTime = System.currentTimeMillis();
    while ((count = inputStream.read(buffer)) > 0) {
      outputStream.write(buffer, 0, count);
      bytesWritten += count;
      bytesRead = gzipStream != null ? gzipStream.getCompressedBytesRead() : bytesWritten;

      if (indicator != null) {
        indicator.checkCanceled();
        if (expectedContentLength > 0) {
          long currentTime = System.currentTimeMillis();
          if (currentTime > startTime) { // To not divide by zero
            long downloadSpeed = (bytesRead * 1000) / (currentTime - startTime); // B/s
            updateIndicator(indicator, downloadSpeed, bytesRead, expectedContentLength, progressDescription);
          }
        }
      }
    }
    if (gzipStream != null) {
      // Amount of read bytes may have changed when 'inputStream.read(buffer)' returns -1
      // E.g. reading GZIP trailer doesn't produce inflated stream data.
      bytesRead = gzipStream.getCompressedBytesRead();
      if (indicator != null && expectedContentLength > 0) {
        long currentTime = System.currentTimeMillis();
        if (currentTime > startTime) { // To not divide by zero
          long downloadSpeed = (bytesRead * 1000) / (currentTime - startTime); // B/s
          updateIndicator(indicator, downloadSpeed, bytesRead, expectedContentLength, progressDescription);
        }
      }
    }

    if (indicator != null) {
      indicator.checkCanceled();
    }

    if (bytesRead < expectedContentLength) {
      throw new IOException("Connection closed at byte " + bytesRead + ". Expected " + expectedContentLength + " bytes.");
    }

    return bytesWritten;
  }

  public static boolean isSniEnabled() {
    return SystemProperties.getBooleanProperty("jsse.enableSNIExtension", true);
  }

  static ProxySelector getProxySelector(@Nullable String pacUrlForUse) {
    return IoService.getInstance().getProxySelector(pacUrlForUse);
  }

  public enum ValidHostInfo {
    INVALID, VALID, VALID_PROXY
  }
  public static @NotNull ValidHostInfo isValidHost(@NotNull String host) {
    try {
      HostAndPort parsedHost = HostAndPort.fromString(host);
      if (parsedHost.hasPort()) {
        return ValidHostInfo.INVALID;
      }
      host = parsedHost.getHost();

      try {
        InetAddresses.forString(host);
        return ValidHostInfo.VALID;
      }
      catch (IllegalArgumentException e) {
        // it is not an IPv4 or IPv6 literal
      }

      InternetDomainName.from(host);
    }
    catch (IllegalArgumentException e) {
      return ValidHostInfo.INVALID;
    }

    return ValidHostInfo.VALID_PROXY;
  }
}
