// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.internal.cachedValueProfiler;

import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonWriter;
import com.intellij.execution.process.ProcessIOExecutorService;
import com.intellij.ide.actions.RevealFileAction;
import com.intellij.notification.Notification;
import com.intellij.notification.NotificationGroupManager;
import com.intellij.notification.NotificationListener;
import com.intellij.notification.NotificationType;
import com.intellij.openapi.application.PathManager;
import com.intellij.openapi.fileEditor.OpenFileDescriptor;
import com.intellij.openapi.fileTypes.FileTypeRegistry;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VfsUtilCore;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.util.CachedValueProfiler;
import com.intellij.util.containers.FactoryMap;
import com.intellij.util.io.URLUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.event.HyperlinkEvent;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.text.MessageFormat;
import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.concurrent.*;
import java.util.function.Supplier;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

public final class CachedValueProfilerDumpHelper implements CachedValueProfiler.EventConsumer {

  private static final int VERSION = 1;

  private final Project myProject;
  private final File myFileTmp;
  private final MyWriter myWriter;
  private final MyQueue myQueue;


  static void toggleProfiling(@NotNull Project project) {
    CachedValueProfiler.EventConsumer prev = CachedValueProfiler.setEventConsumer(null);
    if (prev == null) {
      try {
        CachedValueProfiler.setEventConsumer(new CachedValueProfilerDumpHelper(project));
      }
      catch (IOException ex) {
        notifyFailure(project, ex);
      }
    }
    else if (prev instanceof CachedValueProfilerDumpHelper) {
      ((CachedValueProfilerDumpHelper)prev).close();
    }
  }

  private CachedValueProfilerDumpHelper(@NotNull Project project) throws IOException {
    myProject = project;
    myFileTmp = newFile(true);
    myWriter = new MyWriter(new GZIPOutputStream(new BufferedOutputStream(new FileOutputStream(myFileTmp))));
    myQueue = new MyQueue();
  }

  void close() {
    Throwable error = null;
    for (Closeable c : Arrays.asList(myQueue, myWriter)) {
      try {
        c.close();
      }
      catch (IOException ex) {
        if (error == null) error = ex;
      }
    }
    if (error == null) {
      error = myWriter.getError();
    }
    if (error != null) {
      notifyFailure(myProject, error);
      FileUtil.delete(myFileTmp);
    }
    else {
      File file = newFile(false);
      try {
        FileUtil.rename(myFileTmp, file);
        notifySuccess(myProject, file);
      }
      catch (IOException e) {
        notifyFailure(myProject, e);
      }
    }
  }

  @NotNull
  private static File newFile(boolean tmp) {
    String fileName = "caches-" + new SimpleDateFormat("yyyyMMdd-HHmmss").format(new Date(System.currentTimeMillis())) +
                      ".cvperf" + (tmp ? ".tmp" : "");
    return new File(new File(PathManager.getLogPath()), fileName);
  }

  @Override
  public void onFrameEnter(long frameId, Supplier<StackTraceElement> place, long parentId, long time) {
    myQueue.offer(() -> myWriter.onFrameEnter(frameId, place, parentId, time));
  }

  @Override
  public void onFrameExit(long frameId, long start, long computed, long time) {
    myQueue.offer(() -> myWriter.onFrameExit(frameId, start, computed, time));
  }

  @Override
  public void onValueComputed(long frameId, Supplier<StackTraceElement> place, long start, long time) {
    myQueue.offer(() -> myWriter.onValueComputed(frameId, place, start, time));
  }

  @Override
  public void onValueUsed(long frameId, Supplier<StackTraceElement> place, long computed, long time) {
    myQueue.offer(() -> myWriter.onValueUsed(frameId, place, computed, time));
  }

  @Override
  public void onValueInvalidated(long frameId, Supplier<StackTraceElement> place, long used, long time) {
    myQueue.offer(() -> myWriter.onValueInvalidated(frameId, place, used, time));
  }

  @Override
  public void onValueRejected(long frameId, Supplier<StackTraceElement> place, long start, long computed, long time) {
    myQueue.offer(() -> myWriter.onValueRejected(frameId, place, start, computed, time));
  }

  private static String placeToString(StackTraceElement place) {
    return place.getClassName() + "|" + place.getMethodName() + "|" + place.getFileName() + "|" + place.getLineNumber();
  }

  private static StackTraceElement placeFromString(String place) {
    List<String> split = StringUtil.split(place, "|", true, false);
    return new StackTraceElement(split.get(0), split.get(1), split.get(2), Integer.parseInt(split.get(3)));
  }

  private static void notifySuccess(Project project, File file) {
    String url = FileUtil.getUrl(file);
    boolean fileTypeAvailable = "CVP".equals(FileTypeRegistry.getInstance().getFileTypeByFileName(file.getName()).getName());
    String message = MessageFormat.format("Cached values snapshot is captured to<br>" +
                                          "{0}.<br>" +
                                          (fileTypeAvailable ? "<a href=\"editor:{1}\">Open in Editor</a><br/>" : "") +
                                          "<a href=\"{1}\">{2}</a>",
                                          file.getPath(), url, RevealFileAction.getActionName());
    NotificationGroupManager.getInstance().getNotificationGroup("Cached value profiling")
      .createNotification("", message, NotificationType.INFORMATION, new NotificationListener.Adapter() {
        @Override
        protected void hyperlinkActivated(@NotNull Notification notification,
                                          @NotNull HyperlinkEvent e) {
          if (e.getDescription().startsWith("editor:")) {
            VirtualFile virtualFile = project.isDisposed() ? null : LocalFileSystem.getInstance().findFileByPath(
              VfsUtilCore.urlToPath(VfsUtilCore.fixURLforIDEA(URLUtil.unescapePercentSequences(e.getDescription().substring(7)))));
            if (virtualFile != null) {
              new OpenFileDescriptor(project, virtualFile).navigate(true);
            }
          }
          else {
            RevealFileAction.FILE_SELECTING_LISTENER.hyperlinkUpdate(notification, e);
          }
        }
      }).notify(project);
  }

  private static void notifyFailure(@NotNull Project project, @NotNull Throwable exception) {
    NotificationGroupManager.getInstance().getNotificationGroup("Cached value profiling")
      .createNotification("Failed to capture snapshot: " + exception.getMessage(), NotificationType.ERROR).notify(project);
  }

  static class MyQueue extends ConcurrentLinkedQueue<Runnable> implements Runnable, Closeable {

    volatile boolean closed;
    final Future<?> future;

    MyQueue() {
      future = ProcessIOExecutorService.INSTANCE.submit(this);
    }

    private void drainQueue() {
      Runnable r;
      while ((r = poll()) != null) {
        r.run();
      }
    }

    @Override
    public boolean offer(Runnable runnable) {
      if (closed) return false;
      return super.offer(runnable);
    }

    @Override
    public void run() {
      while (!closed) {
        drainQueue();
        synchronized (future) {
          try {
            future.wait(100);
          }
          catch (InterruptedException ignore) { }
        }
      }
    }

    @Override
    public void close() throws IOException {
      closed = true;
      try {
        synchronized (future) {
          future.notifyAll();
        }
        future.get(500, TimeUnit.MILLISECONDS);
        drainQueue();
      }
      catch (ExecutionException ex) {
        throw new IOException(ex);
      }
      catch (InterruptedException | TimeoutException ignore) { }
    }
  }

  static class MyWriter implements CachedValueProfiler.EventConsumer, Closeable {

    private final JsonWriter myWriter;
    private IOException myError;

    MyWriter(OutputStream out) throws IOException {
      myWriter = new JsonWriter(new OutputStreamWriter(out, StandardCharsets.UTF_8));
      myWriter.beginObject();
      myWriter.name("version").value(VERSION);
      myWriter.name("data");
      myWriter.beginArray();
    }

    @Override
    public void close() throws IOException {
      myWriter.endArray();
      myWriter.endObject();
      myWriter.close();
    }

    @Nullable
    public IOException getError() {
      return myError;
    }

    @Override
    public void onFrameEnter(long frameId, Supplier<StackTraceElement> place, long parentId, long time) {
      try {
        myWriter.beginObject();
        myWriter.name("type").value("frame-enter");
        myWriter.name("frame").value(frameId);
        myWriter.name("place").value(placeToString(place.get()));
        myWriter.name("parent").value(parentId);
        myWriter.name("time").value(time);
        myWriter.endObject();
      }
      catch (IOException e) {
        if (myError != null) myError = e;
      }
    }

    @Override
    public void onFrameExit(long frameId, long start, long computed, long time) {
      try {
        myWriter.beginObject();
        myWriter.name("type").value("frame-exit");
        myWriter.name("frame").value(frameId);
        myWriter.name("start").value(start);
        myWriter.name("computed").value(computed);
        myWriter.name("time").value(time);
        myWriter.endObject();
      }
      catch (IOException e) {
        if (myError != null) myError = e;
      }
    }

    @Override
    public void onValueComputed(long frameId, Supplier<StackTraceElement> place, long start, long time) {
      try {
        myWriter.beginObject();
        myWriter.name("type").value("value-computed");
        myWriter.name("frame").value(frameId);
        myWriter.name("place").value(placeToString(place.get()));
        myWriter.name("start").value(start);
        myWriter.name("time").value(time);
        myWriter.endObject();
      }
      catch (IOException e) {
        if (myError != null) myError = e;
      }
    }

    @Override
    public void onValueUsed(long frameId, Supplier<StackTraceElement> place, long computed, long time) {
      try {
        myWriter.beginObject();
        myWriter.name("type").value("value-used");
        myWriter.name("frame").value(frameId);
        myWriter.name("place").value(placeToString(place.get()));
        myWriter.name("computed").value(computed);
        myWriter.name("time").value(time);
        myWriter.endObject();
      }
      catch (IOException e) {
        if (myError != null) myError = e;
      }
    }

    @Override
    public void onValueInvalidated(long frameId, Supplier<StackTraceElement> place, long used, long time) {
      try {
        myWriter.beginObject();
        myWriter.name("type").value("value-invalidated");
        myWriter.name("frame").value(frameId);
        myWriter.name("place").value(placeToString(place.get()));
        myWriter.name("computed").value(used);
        myWriter.name("time").value(time);
        myWriter.endObject();
      }
      catch (IOException e) {
        if (myError != null) myError = e;
      }
    }

    @Override
    public void onValueRejected(long frameId, Supplier<StackTraceElement> place, long start, long computed, long time) {
      try {
        myWriter.beginObject();
        myWriter.name("type").value("value-rejected");
        myWriter.name("frame").value(frameId);
        myWriter.name("place").value(placeToString(place.get()));
        myWriter.name("start").value(start);
        myWriter.name("computed").value(computed);
        myWriter.name("time").value(time);
        myWriter.endObject();
      }
      catch (IOException e) {
        if (myError != null) myError = e;
      }
    }
  }

  public static void loadDump(@NotNull File file, @NotNull CachedValueProfiler.EventConsumer consumer) throws IOException {
    try (JsonReader reader = new JsonReader(new InputStreamReader(new GZIPInputStream(
      new BufferedInputStream(new FileInputStream(file))), StandardCharsets.UTF_8))) {
      reader.beginObject();
      int version = 0;
      while (reader.hasNext()) {
        String name = reader.nextName();
        if ("version".equals(name)) version = reader.nextInt();
        if ("data".equals(name)) break;
      }
      if (version != VERSION) {
        throw new IOException("Unsupported version: " + version + " (" + VERSION + " required)");
      }

      reader.beginArray();
      String type = "";
      Map<String, StackTraceElement> places = FactoryMap.create(o -> placeFromString(o));
      while (reader.hasNext()) {
        StackTraceElement place0 = null;
        long frame = 0, parent = 0, start = 0, computed = 0, time = 0;
        reader.beginObject();
        while (reader.hasNext()) {
          String name = reader.nextName();
          if ("type".equals(name)) type = reader.nextString();
          else if ("place".equals(name)) place0 = places.get(reader.nextString());
          else if ("frame".equals(name)) frame = reader.nextLong();
          else if ("parent".equals(name)) parent = reader.nextLong();
          else if ("start".equals(name)) start = reader.nextLong();
          else if ("time".equals(name)) time = reader.nextLong();
          else if ("computed".equals(name)) computed = reader.nextLong();
          else throw new IOException("unexpected: " + name);
        }
        reader.endObject();
        StackTraceElement place = place0;
        if ("frame-enter".equals(type)) consumer.onFrameEnter(frame, () -> place, parent, time);
        else if ("frame-exit".equals(type)) consumer.onFrameExit(frame, start, computed, time);
        else if ("value-computed".equals(type)) consumer.onValueComputed(frame, () -> place, start, time);
        else if ("value-used".equals(type)) consumer.onValueUsed(frame, () -> place, computed, time);
        else if ("value-invalidated".equals(type)) consumer.onValueInvalidated(frame, () -> place, computed, time);
        else if ("value-rejected".equals(type)) consumer.onValueRejected(frame, () -> place, start, computed, time);
      }
      reader.endArray();
      reader.endObject();
    }
  }

}
