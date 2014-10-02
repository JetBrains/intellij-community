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

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.fileChooser.FileChooser;
import com.intellij.openapi.fileChooser.FileChooserDescriptor;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.progress.Task;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.projectImport.ProjectSetProcessor;
import org.jetbrains.annotations.NotNull;

import java.util.List;

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

    final VirtualFile directory;
    if (context.directory != null) {
      directory = context.directory;
    }
    else {
      directory = getDirectory();
      if (directory == null) return;
    }

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

          String url = pair.getSecond();
          final String[] split = splitUrl(url);
          if (!processor.checkout(split[0], directory, split[1])) return;
        }
        runNext.run();
      }
    });
  }

  public VirtualFile getDirectory() {
    FileChooserDescriptor descriptor = new FileChooserDescriptor(false, true, false, false, false, false);
    descriptor.setTitle("Select Destination Folder");
    descriptor.setDescription("");
    VirtualFile[] files = FileChooser.chooseFiles(descriptor, null, null);
    return files.length == 0 ? null : files[0];
  }

  private static final Logger LOG = Logger.getInstance(VcsProjectSetProcessor.class);

  public static String[] splitUrl(String url) {
    String[] split = url.split(" ");
    if (split.length == 2) return split;
    int i = url.lastIndexOf('/');
    String path = i < 0 ? "" : FileUtil.getNameWithoutExtension(url.substring(i + 1));
    return new String[] { url, path };
  }
}
