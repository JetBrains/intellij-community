// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package git4idea.gpg;

import externalApp.ExternalApp;
import externalApp.ExternalAppEntry;
import externalApp.ExternalCli;

import java.io.*;
import java.net.Socket;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.security.PublicKey;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

public final class PinentryApp implements ExternalApp, ExternalCli {

  public static void main(String[] args) throws IOException, URISyntaxException {
    var exitCode = new PinentryApp().entryPointInternal(ExternalAppEntry.fromMain(args, PinentryApp.class));
    System.exit(exitCode);
  }

  @Override
  public int entryPoint(ExternalAppEntry entry) {
    try {
      return entryPointInternal(entry);
    } catch (IOException | URISyntaxException e) {
      throw new RuntimeException(e);
    }
  }

  public static final String PREFIX = "IJ_PINENTRY=";
  public static final String EEL_ENTRYPOINT_PREFIX = "IJ_PINENTRY_ENTRYPOINT=";

  public int entryPointInternal(ExternalAppEntry entry) throws IOException, URISyntaxException {
    boolean shouldLog = isLogEnabled(entry.getArgs());
    File logFile = entry.getExecutablePath().getParent().resolve("pinentry-app.log").toFile();
    File exceptionsLogFile = entry.getExecutablePath().getParent().resolve("pinentry-app-exceptions.log").toFile();

    try (FileWriter exceptionsWriter = shouldLog ? new FileWriter(exceptionsLogFile, StandardCharsets.UTF_8) : null) {
      try (FileWriter logWriter = shouldLog ? new FileWriter(logFile, StandardCharsets.UTF_8) : null;
           BufferedReader reader = new BufferedReader(new InputStreamReader(entry.getStdin(), StandardCharsets.UTF_8));
           BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(entry.getStdout(), StandardCharsets.UTF_8))) {

        writer.write("OK Pleased to meet you\n");
        writer.flush();
        String keyDescription = null;

        while (true) {
          String line = reader.readLine();

          if (shouldLog) {
            logWriter.write(line + "\n");
          }

          if (line.startsWith("SETDESC")) {
            keyDescription = line;
            writer.write("OK\n");
          }
          else if (line.startsWith("OPTION")
                   || line.startsWith("GETINFO")
                   || line.startsWith("SET")) {
            writer.write("OK\n");
          }
          else if (line.startsWith("GETPIN")) {
            try {
              String pinentryUserData = entry.getEnvironment().get("PINENTRY_USER_DATA");
              List<String> parsedPinentryData;
              if (pinentryUserData == null) {
                parsedPinentryData = Collections.emptyList();
              }
              else if (pinentryUserData.startsWith(EEL_ENTRYPOINT_PREFIX)) {
                String withoutPrefix = pinentryUserData.replace(EEL_ENTRYPOINT_PREFIX, "");
                parsedPinentryData = Arrays.asList(withoutPrefix.split(":")).subList(1, 4);
              }
              else {
                String withoutPrefix = pinentryUserData.replace(PREFIX, "");
                parsedPinentryData = Arrays.asList(withoutPrefix.split(":"));
              }
              if (parsedPinentryData.size() != 3) {
                if (shouldLog) {
                  exceptionsWriter
                    .write("Cannot locate address (<public-key>:<host>:<port>) from env variable PINENTRY_USER_DATA. Got " + pinentryUserData + "\n");
                }
                throw new Exception();
              }

              PublicKey publicKey;
              String host;
              int port;
              try {
                String publicKeyStr = parsedPinentryData.get(0);
                publicKey = CryptoUtils.stringToPublicKey(publicKeyStr);
                host = parsedPinentryData.get(1);
                port = Integer.parseInt(parsedPinentryData.get(2));
              }
              catch (Exception e) {
                if (shouldLog) {
                  exceptionsWriter.write("Cannot parse env variable PINENTRY_USER_DATA. Got " + pinentryUserData + "\n");
                  exceptionsWriter.write(getStackTrace(e) + "\n");
                }
                throw e;
              }

              try (Socket clientSocket = new Socket(host, port);
                   BufferedWriter socketWriter =
                     new BufferedWriter(new OutputStreamWriter(clientSocket.getOutputStream(), StandardCharsets.UTF_8));
                   BufferedReader socketReader =
                     new BufferedReader(new InputStreamReader(clientSocket.getInputStream(), StandardCharsets.UTF_8))) {
                String request = keyDescription != null ? "GETPIN " + keyDescription + "\n" : "GETPIN\n";
                socketWriter.write(request);
                socketWriter.flush();
                String response = socketReader.readLine();

                if (response.startsWith("D ")) {
                  String passphrase = CryptoUtils.decrypt(response.replace("D ", ""), publicKey);
                  writer.write("D " + passphrase + "\n");
                  writer.write("OK\n");
                }
                else {
                  writer.write("ERR 83886179 unknown command<" + response + ">\n");
                }
              }
            }
            catch (Exception e) {
              if (shouldLog) {
                exceptionsWriter.write("Exception occurred: \n");
                exceptionsWriter.write(getStackTrace(e));
                exceptionsWriter.flush();
              }
              writer.write("ERR 83886180 exception\n");
            }
          }
          else if (line.startsWith("BYE")) {
            writer.write("OK closing connection\n");
            writer.flush();
            break;
          }
          else {
            writer.write("ERR 83886181 unknown command <" + line + ">\n");
          }

          writer.flush();
          if (shouldLog) {
            logWriter.flush();
            exceptionsWriter.flush();
          }
        }
      }
      catch (IOException e) {
        if (shouldLog) {
          exceptionsWriter.write("Exception occurred: \n");
          exceptionsWriter.write(getStackTrace(e));
          exceptionsWriter.flush();
        }
      }
    }
    return 0;
  }

  private static boolean isLogEnabled(String[] args) {
    for (String arg : args) {
      if (arg.equals("--log")) {
        return true;
      }
    }

    return false;
  }

  private static String getStackTrace(Exception e) {
    StringBuilder sb = new StringBuilder(1000);
    StackTraceElement[] st = e.getStackTrace();
    sb.append(e.getClass().getName()).append(": ").append(e.getMessage()).append("\n");

    for (StackTraceElement element : st) {
      sb.append("\t at ").append(element.toString()).append("\n");
    }

    return sb.toString();
  }
}
