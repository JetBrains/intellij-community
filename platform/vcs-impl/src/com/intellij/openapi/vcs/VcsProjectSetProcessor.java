// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.vcs;

import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import com.google.gson.JsonPrimitive;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.fileChooser.FileChooser;
import com.intellij.openapi.fileChooser.FileChooserDescriptor;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.progress.Task;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.projectImport.ProjectSetProcessor;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * @author Dmitry Avdeev
 */
@ApiStatus.Internal
public final class VcsProjectSetProcessor extends ProjectSetProcessor {

  @Override
  public String getId() {
    return "vcs";
  }

  @Override
  public void processEntries(final @NotNull List<? extends Pair<String, String>> entries,
                             final @NotNull Context context,
                             final @NotNull Runnable runNext) {

    if (!getDirectory(context)) return;
    if (!getDirectoryName(context)) return;

    ProgressManager.getInstance().run(new Task.Backgroundable(null, VcsBundle.message("progress.title.hey"), true) {
      @Override
      public void run(@NotNull ProgressIndicator indicator) {
        for (Pair<String, String> pair : entries) {
          String vcs = pair.getFirst();
          final VcsCheckoutProcessor processor = VcsCheckoutProcessor.getProcessor(vcs);
          if (processor == null) {
            LOG.error("Checkout processor not found for " + vcs);
            return;
          }

          JsonElement element = new JsonParser().parse(pair.getSecond());

          HashMap<String, String> parameters = new HashMap<>();
          for (Map.Entry<String, JsonElement> entry : element.getAsJsonObject().entrySet()) {
            JsonElement value = entry.getValue();
            parameters.put(entry.getKey(), value instanceof JsonPrimitive ? value.getAsString() : value.toString());
          }
          String directoryName = context.directoryName;
          if (parameters.get("targetDir") != null) {
            directoryName += "/" + parameters.get("targetDir");
          }
          if (!processor.checkout(parameters, context.directory, directoryName)) return;
        }
        runNext.run();
      }
    });
  }

  private static boolean getDirectoryName(@NotNull Context context) {

    if (ApplicationManager.getApplication().isUnitTestMode()) {
      context.directoryName = "test";
      return true;
    }
    context.directoryName = Messages.showInputDialog((Project)null,
                                                     VcsBundle.message("dialog.message.enter.directory.name", context.directory.getName()),
                                                     VcsBundle.message("dialog.title.project.directory.name"), null, context.directoryName, null);
    return context.directoryName != null;
  }

  private static boolean getDirectory(@NotNull Context context) {
    if (context.directory != null) return true;
    FileChooserDescriptor descriptor = new FileChooserDescriptor(false, true, false, false, false, false);
    descriptor.setTitle(VcsBundle.message("dialog.title.select.destination.folder"));
    descriptor.setDescription("");
    VirtualFile[] files = FileChooser.chooseFiles(descriptor, null, null);
    context.directory = files.length == 0 ? null : files[0];
    return context.directory != null;
  }

  private static final Logger LOG = Logger.getInstance(VcsProjectSetProcessor.class);
}
