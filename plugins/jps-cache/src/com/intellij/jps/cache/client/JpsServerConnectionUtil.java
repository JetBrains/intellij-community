package com.intellij.jps.cache.client;

import com.intellij.execution.ExecutionException;
import com.intellij.execution.configurations.GeneralCommandLine;
import com.intellij.execution.util.ExecUtil;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.util.ProgressWindow;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.util.io.HttpRequests;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.io.IOException;
import java.net.InetAddress;
import java.net.URLConnection;
import java.net.UnknownHostException;
import java.nio.charset.StandardCharsets;
import java.text.DecimalFormat;
import java.util.Base64;
import java.util.Map;

import static com.intellij.execution.process.ProcessIOExecutorService.INSTANCE;

public class JpsServerConnectionUtil {
  private static final Logger LOG = Logger.getInstance(JpsServerConnectionUtil.class);
  private static final String CDN_CACHE_HEADER = "X-Cache";

  public static void measureConnectionSpeed(@NotNull Project project) {
    INSTANCE.execute(() -> {
      ProgressIndicator indicator = new ProgressWindow(true, project);
      indicator.setIndeterminate(false);
      indicator.setFraction(0.01);
      try {
        Map<String, String> headers = JpsServerAuthUtil.getRequestHeaders();
        long start = System.currentTimeMillis();
        HttpRequests.request(calculateAddress())
          .tuner(tuner -> headers.forEach((k, v) -> tuner.addRequestProperty(k, v)))
          .connect(new HttpRequests.RequestProcessor<>() {
            @Override
            public File process(@NotNull HttpRequests.Request request) throws IOException {
              URLConnection connection = request.getConnection();
              int fileSize = connection.getContentLength();
              String header = connection.getHeaderField(CDN_CACHE_HEADER);
              File downloadedFile = request.saveToFile(FileUtil.createTempFile("download.", ".tmp"), indicator);
              long downloadTime = System.currentTimeMillis() - start;
              long bytesPerSecond = fileSize / downloadTime * 1000;
              if (header != null && header.startsWith("Hit")) {
                LOG.info("Speed of connection to CDN: " + StringUtil.formatFileSize(bytesPerSecond) + "/s; " + formatInternetSpeed(bytesPerSecond * 8));
              } else {
                LOG.info("Speed of connection to S3: " + StringUtil.formatFileSize(bytesPerSecond) + "/s; " + formatInternetSpeed(bytesPerSecond * 8));
              }
              FileUtil.delete(downloadedFile);
              return downloadedFile;
            }
          });
      }
      catch (ProcessCanceledException | IOException e) {
        LOG.warn("Failed to download file for measurement connection speed", e);
      }
    });
  }

  public static void checkDomainIsReachable(@NotNull String domain) {
    try {
      GeneralCommandLine pingCommand = new GeneralCommandLine("ping");
      if (SystemInfo.isWindows) {
        pingCommand.addParameter("-n 2");
      } else {
        pingCommand.addParameter("-c 2");
      }
      pingCommand.addParameter(domain);
      int code = ExecUtil.execAndGetOutput(pingCommand).getExitCode();
      if (code == 0) {
        LOG.info("Domain " + domain + " is reachable");
      } else {
        LOG.info("Domain " + domain + " isn't reachable");
      }
    }
    catch (ExecutionException e) {
      LOG.warn("Failed to check if internet connection is available", e);
    }
  }

  private static @NotNull String formatInternetSpeed(long fileSize) {
    int rank = (int)((Math.log10(fileSize) + 0.0000021714778384307465) / 3);  // (3 - Math.log10(999.995))
    double value = fileSize / Math.pow(1000, rank);
    String[] units = {"Bit", "Kbit", "Mbit", "Gbit"};
    return new DecimalFormat("0.##").format(value) + units[rank] + "/s";
  }

  private static @NotNull String calculateAddress() {
    byte[] decodedBytes = Base64.getDecoder().decode("aHR0cHM6Ly9kMWxjNWs5bGVyZzZrbS5jbG91ZGZyb250Lm5ldC9FWEFNUExFLnR4dA==");
    return new String(decodedBytes, StandardCharsets.UTF_8);
  }
}