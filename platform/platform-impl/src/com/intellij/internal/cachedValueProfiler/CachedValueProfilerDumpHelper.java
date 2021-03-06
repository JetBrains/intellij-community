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
      error = myWriter.myError;
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
  public void onFrameEnter(long frameId, CachedValueProfiler.EventPlace place, long parentId, long time) {
    myQueue.offer(() -> myWriter.onFrameEnter(frameId, place, parentId, time));
  }

  @Override
  public void onFrameExit(long frameId, long start, long computed, long time) {
    myQueue.offer(() -> myWriter.onFrameExit(frameId, start, computed, time));
  }

  @Override
  public void onValueComputed(long frameId, CachedValueProfiler.EventPlace place, long start, long time) {
    myQueue.offer(() -> myWriter.onValueComputed(frameId, place, start, time));
  }

  @Override
  public void onValueUsed(long frameId, CachedValueProfiler.EventPlace place, long computed, long time) {
    myQueue.offer(() -> myWriter.onValueUsed(frameId, place, computed, time));
  }

  @Override
  public void onValueInvalidated(long frameId, CachedValueProfiler.EventPlace place, long used, long time) {
    myQueue.offer(() -> myWriter.onValueInvalidated(frameId, place, used, time));
  }

  @Override
  public void onValueRejected(long frameId, CachedValueProfiler.EventPlace place, long start, long computed, long time) {
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

  private static final String _VERSION = "version",_DATA = "data";
  private static final String _FRAME_ENTER = "enter", _FRAME_EXIT = "exit",
    _VALUE_COMPUTED = "computed", _VALUE_USED = "used", _VALUE_INVALIDATED = "invalidated", _VALUE_REJECTED = "rejected";
  private static final String _TYPE = "e", _FRAME_ID = "fid", _FRAME_PID = "fpid",
    _PLACE = "p", _T1 = "t1", _T2 = "t2", _T3 = "t3";

  private static class MyWriter implements CachedValueProfiler.EventConsumer, Closeable {

    final JsonWriter myWriter;
    IOException myError;

    MyWriter(OutputStream out) throws IOException {
      myWriter = new JsonWriter(new OutputStreamWriter(out, StandardCharsets.UTF_8));
      myWriter.beginObject();
      myWriter.name(_VERSION).value(VERSION);
      myWriter.name(_DATA);
      myWriter.beginArray();
    }

    @Override
    public void close() throws IOException {
      myWriter.endArray();
      myWriter.endObject();
      myWriter.close();
    }

    @Override
    public void onFrameEnter(long frameId, CachedValueProfiler.EventPlace place, long parentId, long time) {
      writeImpl(_FRAME_ENTER, frameId, place, 1, time, parentId, -1);
    }

    @Override
    public void onFrameExit(long frameId, long start, long computed, long time) {
      writeImpl(_FRAME_EXIT, frameId, null, 3, start, computed, time);
    }

    @Override
    public void onValueComputed(long frameId, CachedValueProfiler.EventPlace place, long start, long time) {
      writeImpl(_VALUE_COMPUTED, frameId, place, 2, start, time, -1);
    }

    @Override
    public void onValueUsed(long frameId, CachedValueProfiler.EventPlace place, long computed, long time) {
      writeImpl(_VALUE_USED, frameId, place, 2, computed, time, -1);
    }

    @Override
    public void onValueInvalidated(long frameId, CachedValueProfiler.EventPlace place, long used, long time) {
      writeImpl(_VALUE_INVALIDATED, frameId, place, 2, used, time, -1);
    }

    @Override
    public void onValueRejected(long frameId, CachedValueProfiler.EventPlace place, long start, long computed, long time) {
      writeImpl(_VALUE_REJECTED, frameId, place, 3, start, computed, time);
    }

    private void writeImpl(String type, long frameId, CachedValueProfiler.EventPlace place, int t_num, long t1, long t2, long t3) {
      try {
        myWriter.beginObject();
        myWriter.name(_TYPE).value(type);
        myWriter.name(_FRAME_ID).value(frameId);
        if (type == _FRAME_ENTER) {
          myWriter.name(_FRAME_PID).value(t2); // t2 holds parent id
        }
        if (place != null) {
          StackTraceElement frame = place.getStackFrame();
          myWriter.name(_PLACE).value(frame == null ? null : placeToString(frame));
        }
        if (t_num > 0) myWriter.name(_T1).value(t1);
        if (t_num > 1) myWriter.name(_T2).value(t2);
        if (t_num > 2) myWriter.name(_T3).value(t3);
        myWriter.endObject();
      }
      catch (IOException e) {
        if (myError != null) myError = e;
      }
    }
  }

  @NotNull
  static CachedValueProfiler.EventPlace eventPlace(@Nullable StackTraceElement place) {
    return new CachedValueProfiler.EventPlace() {
      @Override public StackTraceElement getStackFrame() { return place; }

      @Override public StackTraceElement @Nullable [] getStackTrace() { return null; }
    };
  }

  public static void loadDump(@NotNull File file, @NotNull CachedValueProfiler.EventConsumer consumer) throws IOException {
    try (JsonReader reader = new JsonReader(new InputStreamReader(new GZIPInputStream(
      new BufferedInputStream(new FileInputStream(file))), StandardCharsets.UTF_8))) {
      reader.beginObject();
      int version = 0;
      while (reader.hasNext()) {
        String name = reader.nextName();
        if (_VERSION.equals(name)) version = reader.nextInt();
        if (_DATA.equals(name)) break;
      }
      if (version != VERSION) {
        throw new IOException("Unsupported version: " + version + " (" + VERSION + " required)");
      }

      reader.beginArray();
      String type = "";
      Map<String, StackTraceElement> places = FactoryMap.create(o -> placeFromString(o));
      while (reader.hasNext()) {
        StackTraceElement place = null;
        long frame = 0, parent = 0, t1 = 0, t2 = 0, t3 = 0;
        reader.beginObject();
        while (reader.hasNext()) {
          String name = reader.nextName();
          if (_TYPE.equals(name)) type = reader.nextString();
          else if (_PLACE.equals(name)) place = places.get(reader.nextString());
          else if (_FRAME_ID.equals(name)) frame = reader.nextLong();
          else if (_FRAME_PID.equals(name)) parent = reader.nextLong();
          else if (_T1.equals(name)) t1 = reader.nextLong();
          else if (_T2.equals(name)) t2 = reader.nextLong();
          else if (_T3.equals(name)) t3 = reader.nextLong();
          else throw new IOException("unexpected: " + name);
        }
        reader.endObject();
        if (_FRAME_ENTER.equals(type)) consumer.onFrameEnter(frame, eventPlace(place), parent, t1);
        else if (_FRAME_EXIT.equals(type)) consumer.onFrameExit(frame, t1, t2, t3);
        else if (_VALUE_COMPUTED.equals(type)) consumer.onValueComputed(frame, eventPlace(place), t1, t2);
        else if (_VALUE_USED.equals(type)) consumer.onValueUsed(frame, eventPlace(place), t1, t2);
        else if (_VALUE_INVALIDATED.equals(type)) consumer.onValueInvalidated(frame, eventPlace(place), t1, t2);
        else if (_VALUE_REJECTED.equals(type)) consumer.onValueRejected(frame, eventPlace(place), t1, t2, t3);
      }
      reader.endArray();

      // allow extra content at the end
      //reader.endObject();
    }
  }

}
