// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.vfs.impl.local;

import com.intellij.openapi.util.SystemInfo;
import com.intellij.openapi.util.text.StringUtil;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.UncheckedIOException;
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
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

@ApiStatus.Experimental
public class DirectoryAccessChecker {
  private static final FilterChain ACCEPTING_FILTER = p -> true;

  private static final Path HOME_DIR = Paths.get(System.getProperty("user.home"));
  private static final Path HOME_ROOT = HOME_DIR.getParent();

  private DirectoryAccessChecker() {}

  private static class InstanceHolder {
    private final static FilterChain chainInstance =
      SystemInfo.isLinux ? new LinuxFilterChain().add(new NFS()).add(new CIFS()).refresh() : ACCEPTING_FILTER;
  }

  public static void refresh() {
    InstanceHolder.chainInstance.refresh();
  }

  @NotNull
  public static Stream<Path> getCheckedStream(@NotNull Path root) {
    try {
      DirectoryStream<Path> ds = Files.newDirectoryStream(root, InstanceHolder.chainInstance);
      return StreamSupport.stream(ds.spliterator(), false).
        onClose(() -> {
          try {
            ds.close();
          }
          catch (IOException ioex) {
            throw new UncheckedIOException(ioex);
          }
        });
    }
    catch (IOException ignored) {
      return Stream.empty();
    }
  }

  /***********************************************************************************
   * Compound checker
   ***********************************************************************************/
  interface FilterChain extends DirectoryStream.Filter<Path> {
    default FilterChain refresh() {
      return this;
    }
  }

  private static class LinuxFilterChain implements FilterChain {
    private final List<FSFilter> checkers = new ArrayList<>();
    private Set<Path> notAccessiblePaths;

    private LinuxFilterChain add(FSFilter checker) {
      checkers.add(checker);
      return this;
    }

    @Override
    public FilterChain refresh() {
      notAccessiblePaths = getMountedDirectories().
        stream().
        filter(p -> !p.isAccessible()).
        map(p -> p.getPath()).
        collect(Collectors.toSet());

      return this;
    }

    private List<FSInfo> getMountedDirectories() {
      List<FSInfo> result = new ArrayList<>();

      try (BufferedReader br = Files.newBufferedReader(Paths.get("/proc/mounts"))) {
        String line;
        while ((line = br.readLine()) != null) {
          String ll = line;
          checkers.stream().anyMatch(f -> f.parse(ll, result));
        }
      }
      catch (IOException ignored) {}

      return result;
    }

    @Override
    public boolean accept(Path path) {
      return path.startsWith(HOME_DIR) || // allow user home directory anyways
             !(path.getParent().equals(HOME_ROOT) && !path.equals(HOME_DIR)) && // don't allow any other directory in the home root
             !notAccessiblePaths.contains(path); // Make sure all checkers allow access to the directory
    }
  }

  /***********************************************************************************
   * FS support base class
   ***********************************************************************************/
  private static final int EXEC_DELAY = 1000; // mS

  private interface FSFilter {
    boolean parse(@NotNull String line, @NotNull Collection<FSInfo> accumulator);
  }

  private static abstract class FSInfo {
    private final Path path;

    private FSInfo(Path path) {
      this.path = path;
    }

    Path getPath() {
      return path;
    }

    abstract boolean isAccessible();
  }
  /***********************************************************************************
   * NFS implementation
   ***********************************************************************************/
  private static class NFS implements FSFilter {
    private static final String RPCINFO = "rpcinfo"; // command
    private static final Pattern NFS_ENTRY = Pattern.compile("(.+?):(.+?) (.+?) nfs(\\d)");
    private static final Pattern RPC_ENTRY = Pattern.compile("\\s+(\\d+)\\s+(\\d)\\s+(tcp|udp)\\s+(\\d+)\\s+nfs");

    @Override
    public boolean parse(@NotNull String line, @NotNull Collection<FSInfo> accumulator) {
      Matcher m = NFS_ENTRY.matcher(line);
      return m.find() && accumulator.add(new NFSInfo(Paths.get(m.group(3)), m.group(1), Integer.parseInt(m.group(4))));
    }

    private static class NFSInfo extends FSInfo {
      private final String host;
      private final int nfsVersion;

      private NFSInfo(Path path, String host, int nfsVersion) {
        super(path);
        this.host = host;
        this.nfsVersion = nfsVersion;
      }

      @Override
      boolean isAccessible() {
        try {
          if (isNFSServiceAvailable()) {
            ProcessBuilder pb = new ProcessBuilder("ls", "-d", getPath().toString());
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

      private boolean isNFSServiceAvailable() throws IOException, InterruptedException {
        ProcessBuilder pb = new ProcessBuilder(RPCINFO, "-p", host);
        Process p = pb.start();
        boolean inTime  = p.waitFor(EXEC_DELAY, TimeUnit.MILLISECONDS);
        if (inTime && p.exitValue() == 0) {
          String protocol = null;
          String programID = null;

          try (BufferedReader ir = new BufferedReader(new InputStreamReader(p.getInputStream(), StandardCharsets.UTF_8))) {
            String line;
            while ((line = ir.readLine()) != null) {
              Matcher m = RPC_ENTRY.matcher(line);
              if (m.matches() && nfsVersion == Integer.parseInt(m.group(2))) {
                protocol = m.group(3);
                programID = m.group(1);
                break;
              }
            }
          }

          if (protocol != null && programID != null) {
            pb = new ProcessBuilder(RPCINFO, "-T", protocol, host, programID, Integer.toString(nfsVersion));
            p = pb.start();
            return p.waitFor(EXEC_DELAY, TimeUnit.MILLISECONDS) && p.exitValue() == 0;
          }
        }
        else if (inTime && p.exitValue() == 127) { // command rpcinfo is not found (man bash)
          return true; // Go further and check directly with ls -d as the last resort because we can't suppose that the path is not accessible.
        }

        return false;
      }
    }
  }

  /***********************************************************************************
   * CIFS implementation
   ***********************************************************************************/
  private static class CIFS implements FSFilter {
    private static final String SMBCLIENT = "smbclient"; // command
    private static final Pattern CIFS_ENTRY = Pattern.compile("(.+?) (.+?) cifs (.+)");

    @Override
    public boolean parse(@NotNull String line, @NotNull Collection<FSInfo> accumulator) {
      Matcher m = CIFS_ENTRY.matcher(line);
      return m.matches() && accumulator.add(new CifsInfo(Paths.get(m.group(2)), m.group(1), m.group(3)));
    }

    private static class CifsInfo extends FSInfo {
      private final CifsOptions options;

      private CifsInfo(Path path, String service, String options) {
        super(path);
        this.options = new CifsOptions(service, options);
      }

      @Override
      boolean isAccessible() {
        try {
          if (isCifsServiceAvailable()) {
            ProcessBuilder pb = new ProcessBuilder("ls", "-d", getPath().toString());
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

      private boolean isCifsServiceAvailable() throws IOException, InterruptedException {
        ProcessBuilder pb = new ProcessBuilder(options.args);
        Process p = pb.start();
        boolean inTime = p.waitFor(EXEC_DELAY, TimeUnit.MILLISECONDS);
        int exitValue = p.exitValue();
        return (inTime && (exitValue == 0 || exitValue == 127));
      }
    }

    private static class CifsOptions {
      private String userName;
      private String domain;
      private String password;
      private String authFile;
      private String ip;
      private String port;

      private final List<String> args = new ArrayList<>();

      private CifsOptions(String service, String optionString) {
        for (String o : optionString.split(",")) {
          if (o.startsWith("username")) {
            userName = getValue(o);
          }
          else if (o.startsWith("dom") || o.startsWith("workgroup")) {
            domain = getValue(o);
          }
          else if (o.startsWith("password")) {
            password = getValue(o);
          }
          else if (o.startsWith("credentials")) {
            authFile = getValue(o);
          }
          else if (o.startsWith("ip") || o.startsWith("addr")) {
            ip = getValue(o);
          }
          else if (o.startsWith("port")) {
            port = getValue(o);
          }
        }

        buildArgs(service);
      }

      private void buildArgs(String service) {
        args.add(SMBCLIENT);
        args.add(service);

        if (StringUtil.isNotEmpty(authFile)) {
          args.add("-A");
          args.add(authFile);
        }
        else {
          args.add(StringUtil.isNotEmpty(password) ? password : "-N");

          if (StringUtil.isNotEmpty(userName)) {
            args.add("-U");
            args.add(userName);
          }
          if (StringUtil.isNotEmpty(domain)) {
            args.add("-W");
            args.add(domain);
          }
        }

        if (StringUtil.isNotEmpty(ip)) {
          args.add("-I");
          args.add(ip);
        }

        if (StringUtil.isNotEmpty(port)) {
          args.add("-p");
          args.add(port);
        }

        args.add("-c");
        args.add("ls");
      }

      private static String getValue(String entry) {
        return entry.substring(entry.indexOf("=") + 1);
      }
    }
  }
}