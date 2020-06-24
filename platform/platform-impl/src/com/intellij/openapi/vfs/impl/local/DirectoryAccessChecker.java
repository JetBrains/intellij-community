// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.vfs.impl.local;

import com.intellij.execution.configurations.PathEnvironmentVariableUtil;
import com.intellij.execution.process.ProcessIOExecutorService;
import com.intellij.openapi.application.Application;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.util.ArrayUtil;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

import java.io.*;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.attribute.PosixFileAttributes;
import java.util.*;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import static com.intellij.openapi.util.Pair.pair;

/** An experiment; please do not use (unless you're certain about what you're doing). */
@ApiStatus.Experimental
public final class DirectoryAccessChecker {
  private DirectoryAccessChecker() { }

  private static final Logger LOG = Logger.getInstance(DirectoryAccessChecker.class);
  private static final boolean IS_ENABLED = Registry.is("directory.access.checker.enabled");
  private static final Path USER_HOME_DIR = Paths.get(System.getProperty("user.home"));
  private static final long REFRESH_RATE_MS = 60_000;

  private static volatile DirectoryFilter instance;
  private static volatile long instanceEOL;
  static {
    instance = DirectoryFilter.ACCEPTING_FILTER;
    instanceEOL = 0;
    refresh();
  }

  public static @NotNull FilenameFilter getFileFilter(File directory) {
    return !IS_ENABLED || directory.toPath().startsWith(USER_HOME_DIR) ? DirectoryFilter.ACCEPTING_FILTER : instance;
  }

  public static void refresh() {
    if (IS_ENABLED) {
      Application app = ApplicationManager.getApplication();
      if (app.isDispatchThread()) {
        app.executeOnPooledThread(() -> doRefresh());
      }
      else {
        doRefresh();
      }
    }
  }

  private static void doRefresh() {
    if (System.currentTimeMillis() > instanceEOL) {
      instance = getChain();
      instanceEOL = System.currentTimeMillis() + REFRESH_RATE_MS;
    }
  }

  private static DirectoryFilter getChain() {
    return SystemInfo.isLinux ? LinuxDirectoryFilter.create() : DirectoryFilter.ACCEPTING_FILTER;
  }

  private interface DirectoryFilter extends FilenameFilter {
    DirectoryFilter ACCEPTING_FILTER = (dir, name) -> true;
  }

  private static final class LinuxDirectoryFilter implements DirectoryFilter {
    private static final FileSystem NFS = new NFS();
    private static final FileSystem CIFS = new CIFS();

    private static final Path USER_HOME_PARENT = USER_HOME_DIR.getParent();
    private static final String USER_HOME_NAME =
      USER_HOME_PARENT != null && USER_HOME_PARENT.getParent() != null ? USER_HOME_DIR.getFileName().toString() : null;

    static @NotNull DirectoryFilter create() {
      Collection<Path> inaccessible = new HashSet<>();
      boolean userHomeMounted = false;

      try (BufferedReader br = Files.newBufferedReader(Paths.get("/proc/mounts"))) {
        String line;
        while ((line = br.readLine()) != null) {
          if (LOG.isTraceEnabled()) LOG.trace("mount: " + line);
          List<String> fields = split(line);  // 0:fs-spec 1:mount-point 2:fs-type 3:fs-options ...
          if (fields.size() < 4) continue;

          String type = fields.get(2);
          FileSystem fs = type.equals("nfs") || type.equals("nfs4") ? NFS : type.equals("cifs") ? CIFS : null;
          if (fs != null) {
            Path path = Paths.get(fields.get(1));
            if (USER_HOME_DIR.equals(path)) userHomeMounted = true;
            if (!fs.isAccessible(fields.get(0), path, fields.get(3))) inaccessible.add(path);
          }
        }
      }
      catch (Exception e) {
        LOG.warn(e);
      }

      return !inaccessible.isEmpty() || userHomeMounted ? new LinuxDirectoryFilter(inaccessible, userHomeMounted) : ACCEPTING_FILTER;
    }

    private final Collection<Path> inaccessiblePaths;
    private final boolean isUserHomeMounted;

    private LinuxDirectoryFilter(Collection<Path> inaccessible, boolean mountedHome) {
      inaccessiblePaths = inaccessible;
      isUserHomeMounted = mountedHome;
    }

    @Override
    public boolean accept(File dir, String name) {
      boolean allowed;
      Path parentPath = dir.toPath();
      if (isUserHomeMounted && USER_HOME_NAME != null && USER_HOME_PARENT.equals(parentPath)) {
        allowed = USER_HOME_NAME.equals(name);  // if a user home is mounted and is at least 2 levels below root, exclude any neighbours
      }
      else {
        allowed = !inaccessiblePaths.contains(parentPath.resolve(name));
      }
      if (!allowed && LOG.isTraceEnabled()) {
        LOG.trace("hidden: " + dir + '/' + name);
      }
      return allowed;
    }
  }

  private interface FileSystem {
    boolean isAccessible(String fsSpec, Path fsPath, String fsOptions);
  }

  //<editor-fold desc="NFS implementation">
  private static class NFS implements FileSystem {
    @SuppressWarnings("SpellCheckingInspection") private final File client = PathEnvironmentVariableUtil.findInPath("rpcinfo");

    @Override
    public boolean isAccessible(String fsSpec, Path fsPath, String fsOptions) {
      try {
        if (client != null) {
          String host = fsSpec.substring(0, fsSpec.indexOf(':'));
          String[] command = {client.getPath(), "-p", host};
          Process p = new ProcessBuilder(command).start();
          if (!exec(p, command)) return false;

          Pair<String, String> pair = parseOptions(fsOptions);
          if (pair == null) {
            LOG.warn("unrecognized RPC mount: " + fsOptions);
          }
          else {
            String version = pair.first, transport = pair.second, programID = null, line;
            try (BufferedReader ir = new BufferedReader(new InputStreamReader(p.getInputStream(), Charset.defaultCharset()))) {
              while ((line = ir.readLine()) != null) {
                if (LOG.isTraceEnabled()) LOG.trace("rpc: " + line);
                List<String> columns = split(line);  // 0:program-id 1:version 2:proto 3:port 4:program-name
                if (columns.size() == 5 && "nfs".equals(columns.get(4)) && version.equals(columns.get(1)) && transport.equals(columns.get(2))) {
                  programID = columns.get(0);
                  break;
                }
              }
            }
            if (programID == null) {
              LOG.warn("unrecognized RPC info output");
            }
            else if (!exec(client.getPath(), "-T", transport, host, programID, version)) {
              return false;
            }
          }
        }

        return probe(fsPath);
      }
      catch (IOException e) {
        LOG.warn(e);
        return false;
      }
      catch (InterruptedException e) {
        throw new IllegalStateException(e);
      }
    }
  }

  @SuppressWarnings("SpellCheckingInspection")
  private static Pair<String, String> parseOptions(String fsOptions) {
    String version = null, transport = null;
    for (String o : StringUtil.tokenize(fsOptions, ",")) {
      if (o.startsWith("vers")) {
        version = getValue(o);
        int p = version.indexOf('.');
        if (p > 0) version = version.substring(0, p);
      }
      else if (o.startsWith("proto")) transport = getValue(o);
    }
    return version != null && transport != null ? pair(version, transport) : null;
  }
  //</editor-fold>

  //<editor-fold desc="CIFS implementation">
  private static class CIFS implements FileSystem {
    @SuppressWarnings("SpellCheckingInspection") private final File client = PathEnvironmentVariableUtil.findInPath("smbclient");

    @Override
    public boolean isAccessible(String fsSpec, Path fsPath, String fsOptions) {
      try {
        if (client != null) {
          String[] command = ArrayUtil.toStringArray(command(client.getPath(), fsSpec, fsOptions));
          if (!exec(command)) {
            return false;
          }
        }

        return probe(fsPath);
      }
      catch (IOException e) {
        LOG.warn(e);
        return false;
      }
      catch (InterruptedException e) {
        throw new IllegalStateException(e);
      }
    }

    private static List<String> command(String clientPath, String fsSpec, String fsOptions) {
      String userName = null, domain  = null, password = null, authFile = null, ip = null, port = null;
      for (String o : StringUtil.tokenize(fsOptions, ",")) {
        if (o.startsWith("username")) userName = getValue(o);
        else if (o.startsWith("dom") || o.startsWith("workgroup")) domain = getValue(o);
        else if (o.startsWith("password")) password = getValue(o);
        else if (o.startsWith("credentials")) authFile = getValue(o);
        else if (o.startsWith("ip") || o.startsWith("addr")) ip = getValue(o);
        else if (o.startsWith("port")) port = getValue(o);
      }

      List<String> command = new ArrayList<>();
      command.add(clientPath);
      command.add(fsSpec);
      if (StringUtil.isNotEmpty(authFile)) {
        command.add("-A");
        command.add(authFile);
      }
      else {
        command.add(StringUtil.isNotEmpty(password) ? password : "-N");
        if (StringUtil.isNotEmpty(userName)) {
          command.add("-U");
          command.add(userName);
        }
        if (StringUtil.isNotEmpty(domain)) {
          command.add("-W");
          command.add(domain);
        }
      }
      if (StringUtil.isNotEmpty(ip)) {
        command.add("-I");
        command.add(ip);
      }
      if (StringUtil.isNotEmpty(port)) {
        command.add("-p");
        command.add(port);
      }
      command.add("-c");
      command.add("ls");
      return command;
    }
  }
  //</editor-fold>

  //<editor-fold desc="Utils">
  private static List<String> split(String line) {
    return ContainerUtil.newArrayList(StringUtil.tokenize(line, " \t"));
  }

  private static String getValue(String entry) {
    return entry.substring(entry.indexOf("=") + 1);
  }

  private static final int TIMEOUT_MS = 1000;

  private static boolean exec(String... command) throws IOException, InterruptedException {
    return exec(new ProcessBuilder(command).start(), command);
  }

  private static boolean exec(Process p, String... command) throws InterruptedException {
    if (p.waitFor(TIMEOUT_MS, TimeUnit.MILLISECONDS)) {
      int rc = p.exitValue();
      if (LOG.isTraceEnabled()) LOG.trace(Arrays.toString(command) + ": " + rc);
      return rc == 0;
    }
    else {
      p.destroyForcibly();
      if (LOG.isTraceEnabled()) LOG.trace(Arrays.toString(command) + ": timeout");
      return false;
    }
  }

  private static boolean probe(Path directory) {
    if (LOG.isTraceEnabled()) LOG.trace("probing: " + directory);
    Future<?> future = ProcessIOExecutorService.INSTANCE.submit(() -> Files.readAttributes(directory, PosixFileAttributes.class));
    try {
      future.get(TIMEOUT_MS, TimeUnit.MILLISECONDS);
      return true;
    }
    catch (InterruptedException | ExecutionException | TimeoutException e) {
      if (LOG.isTraceEnabled()) LOG.trace(e);
      future.cancel(true);
      return false;
    }
  }
  //</editor-fold>
}