package com.intellij.jps.cache.client;

import com.intellij.ide.IdeBundle;
import com.intellij.jps.cache.ui.SegmentedProgressIndicatorManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.util.concurrency.AppExecutorUtil;
import com.intellij.util.download.DownloadableFileDescription;
import com.intellij.util.io.HttpRequests;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicLong;

class JpsOutputsDownloader {
  private static final Logger LOG = Logger.getInstance("com.intellij.jps.cache.client.JpsOutputsDownloader");
  private final List<DownloadableFileDescription> myFilesDescriptions;
  private final SegmentedProgressIndicatorManager myProgressIndicatorManager;

  JpsOutputsDownloader(@NotNull List<DownloadableFileDescription> filesDescriptions, @NotNull SegmentedProgressIndicatorManager indicatorManager) {
    myFilesDescriptions = filesDescriptions;
    myProgressIndicatorManager = indicatorManager;
    myProgressIndicatorManager.setTasksCount(filesDescriptions.size());
  }

  @NotNull
  List<Pair<File, DownloadableFileDescription>> download(@NotNull File targetDir) throws IOException {
    List<Pair<File, DownloadableFileDescription>> downloadedFiles = Collections.synchronizedList(new ArrayList<>());
    List<Pair<File, DownloadableFileDescription>> existingFiles = Collections.synchronizedList(new ArrayList<>());

    try {
      myProgressIndicatorManager.setText(this, IdeBundle.message("progress.downloading.0.files.text", myFilesDescriptions.size()));
      int maxParallelDownloads = Runtime.getRuntime().availableProcessors();
      LOG.debug("Downloading " + myFilesDescriptions.size() + " files using " + maxParallelDownloads + " threads");
      long start = System.currentTimeMillis();
      ExecutorService executor = AppExecutorUtil.createBoundedApplicationPoolExecutor("FileDownloaderImpl Pool", maxParallelDownloads);
      List<Future<Void>> results = new ArrayList<>();
      final AtomicLong totalSize = new AtomicLong();
      for (final DownloadableFileDescription description : myFilesDescriptions) {
        results.add(executor.submit(() -> {
          SegmentedProgressIndicatorManager.SubTaskProgressIndicator indicator = myProgressIndicatorManager.createSubTaskIndicator();
          indicator.checkCanceled();

          final File existing = new File(targetDir, description.getDefaultFileName());
          File downloaded;
          try {
            downloaded = downloadFile(description, existing, indicator);
          } catch (IOException e) {
            if (e  instanceof HttpRequests.HttpStatusException && ((HttpRequests.HttpStatusException)e).getStatusCode() == 404) {
              LOG.info("File not found to download " + description.getDownloadUrl());
              indicator.finished();
              return null;
            }
            throw new IOException(IdeBundle.message("error.file.download.failed", description.getDownloadUrl(), e.getMessage()), e);
          }
          if (FileUtil.filesEqual(downloaded, existing)) {
            existingFiles.add(Pair.create(existing, description));
          }
          else {
            totalSize.addAndGet(downloaded.length());
            downloadedFiles.add(Pair.create(downloaded, description));
          }
          indicator.finished();
          return null;
        }));
      }

      for (Future<Void> result : results) {
        try {
          result.get();
        }
        catch (InterruptedException e) {
          throw new ProcessCanceledException();
        }
        catch (ExecutionException e) {
          if (e.getCause() instanceof IOException) {
            throw ((IOException)e.getCause());
          }
          if (e.getCause() instanceof ProcessCanceledException) {
            throw ((ProcessCanceledException)e.getCause());
          }
          LOG.error(e);
        }
      }
      long duration = System.currentTimeMillis() - start;
      LOG.debug(
        "Downloaded " + StringUtil.formatFileSize(totalSize.get()) + " in " + StringUtil.formatDuration(duration) + "(" + duration + "ms)");

      List<Pair<File, DownloadableFileDescription>> localFiles = new ArrayList<>();
      localFiles.addAll(moveToDir(downloadedFiles, targetDir));
      localFiles.addAll(existingFiles);
      myProgressIndicatorManager.finished(this);
      return localFiles;
    }
    catch (ProcessCanceledException | IOException e) {
      for (Pair<File, DownloadableFileDescription> pair : downloadedFiles) {
        FileUtil.delete(pair.getFirst());
      }
      throw e;
    }
  }

  @NotNull
  private static File downloadFile(@NotNull final DownloadableFileDescription description, @NotNull final File existingFile,
                                   @NotNull final ProgressIndicator indicator) throws IOException {
    final String presentableUrl = description.getPresentableDownloadUrl();
    indicator.setText2(IdeBundle.message("progress.connecting.to.download.file.text", presentableUrl));
    indicator.setIndeterminate(true);

    return HttpRequests.request(description.getDownloadUrl()).connect(new HttpRequests.RequestProcessor<File>() {
      @Override
      public File process(@NotNull HttpRequests.Request request) throws IOException {
        int size = request.getConnection().getContentLength();
        if (existingFile.exists() && size == existingFile.length()) {
          return existingFile;
        }

        indicator.setText2(IdeBundle.message("progress.download.file.text", description.getPresentableFileName(), presentableUrl));
        return request.saveToFile(FileUtil.createTempFile("download.", ".tmp"), indicator);
      }
    });
  }

  private static List<Pair<File, DownloadableFileDescription>> moveToDir(List<Pair<File, DownloadableFileDescription>> downloadedFiles,
                                                                         final File targetDir) throws IOException {
    FileUtil.createDirectory(targetDir);
    List<Pair<File, DownloadableFileDescription>> result = new ArrayList<>();
    for (Pair<File, DownloadableFileDescription> pair : downloadedFiles) {
      final DownloadableFileDescription description = pair.getSecond();
      final String fileName = description.generateFileName(s -> !new File(targetDir, s).exists());
      final File toFile = new File(targetDir, fileName);
      FileUtil.rename(pair.getFirst(), toFile);
      result.add(Pair.create(toFile, description));
    }
    return result;
  }
}
