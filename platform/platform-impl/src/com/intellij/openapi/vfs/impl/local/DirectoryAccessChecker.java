// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.vfs.impl.local;

import com.intellij.openapi.util.SystemInfo;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.function.Predicate;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

public class DirectoryAccessChecker {
  private static final DirectoryStream.Filter<Path> ACCEPTING_FILTER = p -> true;

  private static final Path HOME_DIR = Paths.get(System.getProperty("user.home"));
  private static final Path HOME_ROOT = HOME_DIR.getParent();

  private DirectoryAccessChecker() {}

  public static DirectoryStream.Filter<Path> create() {
    return SystemInfo.isLinux ? new Checker().add(new NFS()) : ACCEPTING_FILTER;
  }

  public static Stream<Path> getCheckedStream(Path root) throws IOException {
    return StreamSupport.stream(Files.newDirectoryStream(root, create()).spliterator(), false);
  }

  /***********************************************************************************
   * Compound checker
   ***********************************************************************************/
  private static class Checker implements DirectoryStream.Filter<Path> {
    private final List<Predicate<Path>> checkers = new ArrayList<>();

    @Override
    public boolean accept(Path entry) {
      return entry.startsWith(HOME_DIR) || // allow user home directory anyways
             !(entry.getParent().equals(HOME_ROOT) && !entry.equals(HOME_DIR)) && // don't allow any other directory in the home root
             checkers.stream().allMatch(p -> p.test(entry)); // Make sure all checkers allow access to the directory
    }

    private Checker add(Predicate<Path> checker) {
      checkers.add(checker);
      return this;
    }
  }

  /***********************************************************************************
   * NFS implementation
   ***********************************************************************************/
  private static class NFS implements Predicate<Path> {
    private static final String RPCINFO = "rpcinfo"; // command
    private static final int EXEC_DELAY = 1000; // mS

    private static final Pattern NFS_ENTRY = Pattern.compile("(.+?):(.+?) (.+?) nfs(\\d)");
    private static final Pattern RPC_ENTRY = Pattern.compile("\\s+(\\d+)\\s+(\\d)\\s+(tcp|udp)\\s+(\\d+)\\s+nfs");

    private final Set<Path> notAccessiblePaths;

    private NFS() {
      notAccessiblePaths = getMountedDirectories().
        stream().
        filter(p -> !isAccessible(p)).
        map(p -> p.path).
        collect(Collectors.toSet());
    }

    @Override
    public boolean test(Path entry) {
      return !notAccessiblePaths.contains(entry);  // allow all accessible paths
    }

    private static Collection<NFSInfo> getMountedDirectories() {
      List<NFSInfo> result = new ArrayList<>();

      try (BufferedReader br = Files.newBufferedReader(Paths.get("/proc/mounts"))) {
        String line;
        while ((line = br.readLine()) != null) {
          Matcher m = NFS_ENTRY.matcher(line);
          if (m.find()) {
            result.add(new NFSInfo(Paths.get(m.group(3)), m.group(1), Integer.parseInt(m.group(4))));
          }
        }
      }
      catch (IOException ignored) {}

      return result;
    }

    private static boolean isAccessible(NFSInfo pathInfo) {
      try {
        if (isNFSServiceAvailable(pathInfo)) {
          ProcessBuilder pb = new ProcessBuilder("ls", "-d", pathInfo.path.toString());
          Process p = pb.start();
          if (p.waitFor(EXEC_DELAY, TimeUnit.MILLISECONDS)) {
            return true;
          }
          p.destroyForcibly().waitFor();
        }

        return false;
      } catch (InterruptedException | IOException ioex) {
        return false;
      }
    }

    private static boolean isNFSServiceAvailable(NFSInfo pathInfo) throws IOException, InterruptedException {
      ProcessBuilder pb = new ProcessBuilder(RPCINFO, "-p", pathInfo.host);
      Process p = pb.start();
      boolean inTime  = p.waitFor(EXEC_DELAY, TimeUnit.MILLISECONDS);
      if (inTime && p.exitValue() == 0) {
        String protocol = null;
        String programID = null;

        try (BufferedReader ir = new BufferedReader(new InputStreamReader(p.getInputStream(), StandardCharsets.UTF_8))) {
          String line;
          while ((line = ir.readLine()) != null) {
            Matcher m = RPC_ENTRY.matcher(line);
            if (m.matches() && pathInfo.nfsVersion == Integer.parseInt(m.group(2))) {
              protocol = m.group(3);
              programID = m.group(1);
              break;
            }
          }
        }

        if (protocol != null && programID != null) {
          pb = new ProcessBuilder(RPCINFO, "-T", protocol, pathInfo.host, programID, Integer.toString(pathInfo.nfsVersion));
          p = pb.start();
          return p.waitFor(EXEC_DELAY, TimeUnit.MILLISECONDS) && p.exitValue() == 0;
        }
      }
      else if (inTime && p.exitValue() == 127) { // command rpcinfo is not found (man bash)
        return true; // Go further and check directly with ls -d as the last resort because we can't suppose that the path is not accessible.
      }

      return false;
    }

    private static class NFSInfo {
      private final Path   path;
      private final String host;
      private final int nfsVersion;

      private NFSInfo(Path path, String host, int nfsVersion) {
        this.path = path;
        this.host = host;
        this.nfsVersion = nfsVersion;
      }
    }
  }
}