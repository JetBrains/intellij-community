// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.net;

import com.google.common.net.HostAndPort;
import com.google.common.net.InetAddresses;
import com.google.common.net.InternetDomainName;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.util.NlsSafe;
import com.intellij.openapi.util.io.StreamUtil;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.util.SystemProperties;
import com.intellij.util.io.CountingGZIPInputStream;
import com.intellij.util.io.IoService;
import com.intellij.util.system.OS;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.ProxySelector;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.UnknownHostException;

public final class NetUtils {
  private static final Logger LOG = Logger.getInstance(NetUtils.class);

  private NetUtils() { }

  /**
   * Use this function cautiously:
   * <ul>
   *   <li>
   *     It checks the possibility to connect by actually connecting.
   *     It may be undesired in many situations.
   *   </li>
   *   <li>
   *     If {@code NetUtils.isLocalhost(host)} is true, it tries to listen on the socket instead of connecting.
   *   </li>
   * </ul>
   */
  @ApiStatus.Internal
  @ApiStatus.Obsolete
  public static boolean canConnectToSocket(String host, int port) {
    if (isLocalhost(host)) {
      return !canBindToLocalSocket(host, port);
    }
    else {
      return canConnectToRemoteSocket(host, port);
    }
  }

  /**
   * The function is left to let the already existing code work as is.
   * Please think twice before using this function in the new code.
   * <p>
   * This function tries to distinguish if {@code hostName} points to the loopback address, but there are many limitations:
   * <ul>
   *   <li>
   *     {@code 127.0.0.2} is a valid loopback address, and {@code 127.1.2.3} too, and so on.
   *     However, the function detects only {@code 127.0.0.1}
   *   </li>
   *   <li>
   *     Although {@code localhost} resolves to {@code ::1} on macOS,
   *     it's not true for Ubuntu, which defines a separate host alias {@code ip6-localhost}.
   *     Nevertheless, the function always returns true for "localhost".
   *   </li>
   *   <li>
   *     {@code localhost.} with the dot at the end and {@code fgsfds.localhost} are a valid loopback addresses,
   *     but the function would return false.
   *   </li>
   *   <li>
   *     {@code 0:0:0:0:0:0:0:1}, {@code 0::0:1}, {@code 0:0::0:1} are valid IPv6 loopback addresses, but the function would return false.
   *   </li>
   *   <li>
   *     The user may assign any domain name for the loopback address in {@code /etc/hosts} / {@code C:\Windows\System32\drivers\etc\hosts},
   *     but the function won't detect it.
   *   </li>
   *   <li>
   *     There are public domain names that resolves to the loopback address. For example, {@code fbi.com}.
   *     However, the function won't detect it.
   *   </li>
   * </ul>
   * </p>
   * <p>
   * The only reliable way to check if {@code hostName} is a loopback address is to resolve it:
   * <pre>
   *   InetAddress.getByName(hostName).isLoopbackAddress()  // Invokes DNS lookups.
   *
   * </pre>
   * </p>
   */
  @ApiStatus.Internal
  @ApiStatus.Obsolete
  public static boolean isLocalhost(@NotNull @NlsSafe String hostName) {
    return hostName.equalsIgnoreCase("localhost") || hostName.equals("127.0.0.1") || hostName.equals("::1");
  }

  private static boolean canBindToLocalSocket(String host, int port) {
    try (ServerSocket socket = new ServerSocket()) {
      // it looks like this flag should be set, but it leads to incorrect results for Node.js under Windows
      //socket.setReuseAddress(true);
      socket.bind(new InetSocketAddress(host, port));
      return true;
    }
    catch (IOException e) {
      LOG.debug(e);
      return false;
    }
  }

  /**
   * Use this function cautiously, because it checks the possibility to connect by actually connecting.
   * It may be undesired in many situations.
   */
  @ApiStatus.Internal
  @ApiStatus.Obsolete
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
    int[] ports = new int[capacity];
    ServerSocket[] sockets = new ServerSocket[capacity];

    for (int i = 0; i < capacity; i++) {
      @SuppressWarnings({"IOResourceOpenedButNotSafelyClosed", "SocketOpenedButNotSafelyClosed", "resource"})
      ServerSocket serverSocket = new ServerSocket(0);
      sockets[i] = serverSocket;
      ports[i] = serverSocket.getLocalPort();
    }
    // workaround for Linux: calling close() immediately after opening  may result in socket not being closed
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

    for (ServerSocket socket : sockets) socket.close();

    return ports;
  }

  public static String getLocalHostString() {
    @NlsSafe String localHostString = "localhost";  // a hack for Windows with IPv6
    try {
      InetAddress localHost = InetAddress.getByName(localHostString);
      if ((localHost.getAddress().length != 4 && OS.CURRENT == OS.Windows) || (localHost.getAddress().length == 4 && OS.CURRENT == OS.macOS)) {
        localHostString = "127.0.0.1";
      }
    }
    catch (UnknownHostException ignored) { }
    return localHostString;
  }

  private static void updateIndicator(ProgressIndicator indicator, long downloadSpeed, long bytesDownloaded, long contentLength, boolean progressDescription) {
    double fraction = (double)bytesDownloaded / contentLength;
    if (progressDescription) {
      int rankForContentLength = StringUtil.rankForFileSize(contentLength);
      String formattedDownloadSpeed = StringUtil.formatFileSize(downloadSpeed) + "⧸s";
      String formattedContentLength = StringUtil.formatFileSize(contentLength, " ", rankForContentLength);
      String formattedTotalProgress = StringUtil.formatFileSize(bytesDownloaded, " ", rankForContentLength);
      @NlsSafe String indicatorText = String.format(
        "<html><code>%.0f%% · %s⧸%s · %s</code></html>", fraction * 100,
        formattedTotalProgress, formattedContentLength, formattedDownloadSpeed);
      indicator.setText2(indicatorText);
    }
    indicator.setFraction(fraction);
  }

  /** @deprecated use {@link #copyStreamContent(ProgressIndicator, InputStream, OutputStream, long)} instead */
  @Deprecated(forRemoval = true)
  public static int copyStreamContent(
    @Nullable ProgressIndicator indicator,
    @NotNull InputStream inputStream,
    @NotNull OutputStream outputStream,
    int expectedContentLength
  ) throws IOException, ProcessCanceledException {
    return (int)copyStreamContent(indicator, inputStream, outputStream, (long)expectedContentLength);
  }

  /** @see #copyStreamContent(ProgressIndicator, InputStream, OutputStream, long, boolean) */
  public static long copyStreamContent(
    @Nullable ProgressIndicator indicator,
    @NotNull InputStream inputStream,
    @NotNull OutputStream outputStream,
    long expectedContentLength
  ) throws IOException, ProcessCanceledException {
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
  public static long copyStreamContent(
    @Nullable ProgressIndicator indicator,
    @NotNull InputStream inputStream,
    @NotNull OutputStream outputStream,
    long expectedContentLength,
    boolean progressDescription
  ) throws IOException, ProcessCanceledException {
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

  @ApiStatus.Internal
  public static boolean isSniEnabled() {
    return SystemProperties.getBooleanProperty("jsse.enableSNIExtension", true);
  }

  @ApiStatus.Internal
  public static ProxySelector getProxySelector(@Nullable String pacUrlForUse) {
    return IoService.getInstance().getProxySelector(pacUrlForUse);
  }

  @ApiStatus.Internal
  public enum ValidHostInfo {
    INVALID, VALID, VALID_PROXY
  }

  @ApiStatus.Internal
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
