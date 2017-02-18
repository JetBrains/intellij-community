/*
 * Copyright 2000-2014 JetBrains s.r.o.
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
import org.jetbrains.annotations.NotNull;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * @author Dmitry Avdeev
 */
public class VcsProjectSetProcessor extends ProjectSetProcessor {

  @Override
  public String getId() {
    return "vcs";
  }

  @Override
  public void processEntries(@NotNull final List<Pair<String, String>> entries,
                             @NotNull final Context context,
                             @NotNull final Runnable runNext) {

    if (!getDirectory(context)) return;
    if (!getDirectoryName(context)) return;

    ProgressManager.getInstance().run(new Task.Backgroundable(null, "Hey", true) {
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
                                        "Enter directory name for created project. Leave blank to checkout directly into \"" +
                                        context.directory.getName() + "\".",
                                        "Project Directory Name", null, context.directoryName, null);
    return context.directoryName != null;
  }

  private static boolean getDirectory(@NotNull Context context) {
    if (context.directory != null) return true;
    FileChooserDescriptor descriptor = new FileChooserDescriptor(false, true, false, false, false, false);
    descriptor.setTitle("Select Destination Folder");
    descriptor.setDescription("");
    VirtualFile[] files = FileChooser.chooseFiles(descriptor, null, null);
    context.directory = files.length == 0 ? null : files[0];
    return context.directory != null;
  }

  private static final Logger LOG = Logger.getInstance(VcsProjectSetProcessor.class);
}
