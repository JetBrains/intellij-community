/*
 * Copyright 2000-2015 JetBrains s.r.o.
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
package com.intellij.idea;

import com.intellij.CommonBundle;
import com.intellij.openapi.application.ApplicationNamesInfo;
import com.intellij.openapi.application.PathManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.io.FileUtilRt;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.CharsetToolkit;
import com.intellij.util.ArrayUtil;
import com.intellij.util.Consumer;
import com.intellij.util.NotNullProducer;
import com.intellij.util.containers.MultiMap;
import com.intellij.util.net.NetUtils;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufOutputStream;
import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelHandlerContext;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.io.BuiltInServer;
import org.jetbrains.io.MessageDecoder;

import javax.swing.*;
import java.io.*;
import java.net.Socket;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

/**
 * @author mike
 */
public final class SocketLock {
  private static final Logger LOG = Logger.getInstance(SocketLock.class);

  @NonNls private static final String ACTIVATE_COMMAND = "activate ";

  private final String configPath;
  private final String systemPath;

  public enum ActivateStatus {ACTIVATED, NO_INSTANCE, CANNOT_ACTIVATE}

  private final AtomicReference<Consumer<List<String>>> activateListener = new AtomicReference<Consumer<List<String>>>();

  private BuiltInServer server;

  public SocketLock(@NotNull String configPath, @NotNull String systemPath) {
    this.configPath = configPath;
    this.systemPath = systemPath;
  }

  public void setExternalInstanceListener(@Nullable Consumer<List<String>> consumer) {
    activateListener.set(consumer);
  }

  public void dispose() {
    if (LOG.isDebugEnabled()) {
      LOG.debug("enter: destroyProcess()");
    }

    BuiltInServer server = this.server;
    boolean doRemovePortMarker = server != null;
    try {
      if (server != null) {
        Disposer.dispose(server);
      }
    }
    finally {
      if (doRemovePortMarker) {
        try {
          executeAndClose(new Executor<Void>() {
            @Override
            public Void execute(@NotNull List<Closeable> closeables) throws IOException {
              File config = new File(configPath);
              File system = new File(systemPath);
              lockPortMarker(config, closeables);
              lockPortMarker(system, closeables);
              FileUtil.delete(new File(config, "port"));
              FileUtil.delete(new File(system, "port"));
              return null;
            }
          });
        }
        catch (Throwable e) {
          LOG.error(e);
        }
      }
    }
  }

  @Nullable
  public BuiltInServer getServer() {
    return server;
  }

  private static void lockPortMarker(@NotNull File parent, @NotNull List<Closeable> list) throws IOException {
    FileUtilRt.createDirectory(parent);
    FileOutputStream stream = new FileOutputStream(new File(parent, "port.lock"), true);
    list.add(stream);
  }

  private static void addExistingPort(@NotNull File portMarker, @NotNull String path, @NotNull MultiMap<Integer, String> portToPath) {
    if (portMarker.exists()) {
      try {
        portToPath.putValue(Integer.parseInt(FileUtilRt.loadFile(portMarker)), path);
      }
      catch (Throwable e) {
        LOG.debug(e);
        // don't delete - we overwrite it on write in any case
      }
    }
  }

  @Nullable
  public ActivateStatus lock() {
    return lock(ArrayUtil.EMPTY_STRING_ARRAY);
  }

  @Nullable
  public ActivateStatus lock(@NotNull final String[] args) {
    if (LOG.isDebugEnabled()) {
      LOG.debug("enter: lock(configPath='" + configPath + "', systemPath='" + systemPath + "')");
    }

    try {
      final File config = new File(configPath);
      final File system = new File(systemPath);
      final File portMarkerC = new File(config, "port");
      final File portMarkerS = new File(system, "port");
      return executeAndClose(new Executor<ActivateStatus>() {
        @Override
        public ActivateStatus execute(@NotNull List<Closeable> closeables) throws Throwable {
          lockPortMarker(config, closeables);
          lockPortMarker(system, closeables);
          MultiMap<Integer, String> portToPath = MultiMap.createSmart();
          addExistingPort(portMarkerC, configPath, portToPath);
          addExistingPort(portMarkerS, systemPath, portToPath);
          if (!portToPath.isEmpty()) {
            for (Map.Entry<Integer, Collection<String>> entry : portToPath.entrySet()) {
              ActivateStatus status = tryActivate(entry.getKey(), entry.getValue(), args);
              if (status != ActivateStatus.NO_INSTANCE) {
                return status;
              }
            }
          }

          final String[] lockedPaths = {configPath, systemPath};
          server = BuiltInServer.start(1, 6942, 50, false, new NotNullProducer<ChannelHandler>() {
            @NotNull
            @Override
            public ChannelHandler produce() {
              return new MyChannelInboundHandler(lockedPaths, activateListener);
            }
          });

          byte[] portBytes = Integer.toString(server.getPort()).getBytes(CharsetToolkit.UTF8_CHARSET);
          FileUtil.writeToFile(portMarkerC, portBytes);
          FileUtil.writeToFile(portMarkerS, portBytes);
          return ActivateStatus.NO_INSTANCE;
        }
      });
    }
    catch (Throwable e) {
      LOG.error(e);

      if (Main.isHeadless()) {
        Main.showMessage("Cannot lock system folders", e);
      }
      else {
        String pathToLogFile = PathManager.getLogPath() + "/idea.log file".replace('/', File.separatorChar);
        JOptionPane.showMessageDialog(
          JOptionPane.getRootFrame(),
          CommonBundle.message("cannot.start.other.instance.is.running.error.message", ApplicationNamesInfo.getInstance().getProductName(),
                               pathToLogFile),
          CommonBundle.message("title.warning"),
          JOptionPane.WARNING_MESSAGE
        );
      }
      return null;
    }
  }

  private interface Executor<T> {
    T execute(@NotNull List<Closeable> closeables) throws Throwable;
  }

  private static <T> T executeAndClose(@NotNull Executor<T> executor) throws Throwable {
    List<Closeable> closeables = new ArrayList<Closeable>();
    try {
      return executor.execute(closeables);
    }
    finally {
      for (Closeable closeable : closeables) {
        try {
          closeable.close();
        }
        catch (Throwable e) {
          LOG.error(e);
        }
      }
    }
  }

  @SuppressWarnings({"SocketOpenedButNotSafelyClosed", "IOResourceOpenedButNotSafelyClosed"})
  @NotNull
  private static ActivateStatus tryActivate(int portNumber, @NotNull Collection<String> paths, @NotNull String[] args) {
    Socket socket = null;
    try {
      socket = new Socket(NetUtils.getLoopbackAddress(), portNumber);
      socket.setSoTimeout(300);

      boolean result = false;
      DataInputStream in = new DataInputStream(socket.getInputStream());
      while (true) {
        try {
          String path = in.readUTF();
          if (paths.contains(path)) {
            result = true;
            // don't break - read all input
          }
        }
        catch (IOException ignored) {
          break;
        }
      }

      if (result) {
        try {
          DataOutputStream out = new DataOutputStream(socket.getOutputStream());
          out.writeUTF(ACTIVATE_COMMAND + new File(".").getAbsolutePath() + "\0" + StringUtil.join(args, "\0"));
          out.flush();
          String response = in.readUTF();
          if (response.equals("ok")) {
            return ActivateStatus.ACTIVATED;
          }
        }
        catch (IOException e) {
          LOG.info(e);
        }
        return ActivateStatus.CANNOT_ACTIVATE;
      }
    }
    catch (IOException e) {
      LOG.debug(e);
    }
    finally {
      if (socket != null) {
        try {
          socket.close();
        }
        catch (IOException e) {
          LOG.debug(e);
        }
      }
    }

    return ActivateStatus.NO_INSTANCE;
  }

  private static class MyChannelInboundHandler extends MessageDecoder {
    private final String[] lockedPaths;
    private State state = State.HEADER;
    private final AtomicReference<Consumer<List<String>>> activateListener;

    public MyChannelInboundHandler(@NotNull String[] lockedPaths, @NotNull AtomicReference<Consumer<List<String>>> activateListener) {
      this.lockedPaths = lockedPaths;
      this.activateListener = activateListener;
    }

    private enum State {HEADER, CONTENT}

    @SuppressWarnings("IOResourceOpenedButNotSafelyClosed")
    @Override
    public void channelActive(ChannelHandlerContext context) throws Exception {
      ByteBuf buffer = context.alloc().ioBuffer(1024);
      boolean success = false;
      try {
        ByteBufOutputStream out = new ByteBufOutputStream(buffer);
        for (String path : lockedPaths) {
          if (path != null) {
            out.writeUTF(path);
          }
        }
        out.close();
        success = true;
      }
      finally {
        if (!success) {
          buffer.release();
        }
      }
      context.writeAndFlush(buffer);
    }

    @SuppressWarnings("IOResourceOpenedButNotSafelyClosed")
    @Override
    protected void messageReceived(@NotNull ChannelHandlerContext context, @NotNull ByteBuf input) throws Exception {
      while (true) {
        switch (state) {
          case HEADER: {
            ByteBuf buffer = getBufferIfSufficient(input, 2, context);
            if (buffer == null) {
              return;
            }

            contentLength = buffer.readUnsignedShort();
            state = State.CONTENT;
          }
          break;

          case CONTENT: {
            CharSequence command = readChars(input);
            if (command == null) {
              return;
            }

            if (StringUtil.startsWith(command, ACTIVATE_COMMAND)) {
              List<String> args = StringUtil.split(command.subSequence(ACTIVATE_COMMAND.length(), command.length()).toString(), "\0");
              Consumer<List<String>> listener = activateListener.get();
              if (listener != null) {
                listener.consume(args);
              }

              ByteBuf buffer = context.alloc().ioBuffer(4);
              ByteBufOutputStream out = new ByteBufOutputStream(buffer);
              out.writeUTF("ok");
              out.close();
              context.writeAndFlush(buffer);
            }
            context.close();
          }
          break;
        }
      }
    }
  }
}