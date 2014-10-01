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
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.projectImport.ProjectSetProcessor;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * @author Dmitry Avdeev
 */
public class VcsProjectSetProcessor extends ProjectSetProcessor {

  @Override
  public String getId() {
    return "vcs";
  }

  @Override
  public boolean processEntries(@NotNull List<Pair<String, String>> entries, @NotNull final Context context) {

    final VirtualFile directory;
    if (context.directory != null) {
      directory = context.directory;
    }
    else {
      FileChooserDescriptor descriptor = new FileChooserDescriptor(false, true, false, false, false, false);
      descriptor.setTitle("Select Destination Folder");
      descriptor.setDescription("");
      VirtualFile[] files = FileChooser.chooseFiles(descriptor, null, null);
      if (files.length == 0) return false;
      directory = files[0];
    }
    for (Pair<String, String> pair: entries) {
      String vcs = pair.getFirst();
      VcsCheckoutProcessor processor = VcsCheckoutProcessor.getProcessor(vcs);
      if (processor == null) {
        LOG.error("Checkout processor not found for " + vcs);
        continue;
      }

      String url = pair.getSecond();
      String[] split = splitUrl(url);
      synchronized (myLock) {
        final AtomicBoolean done = new AtomicBoolean();
        try {
          processor.checkout(split[0], split[1], directory, new CheckoutProvider.Listener() {
            @Override
            public void directoryCheckedOut(File directory, VcsKey vcs) {

            }

            @Override
            public void checkoutCompleted() {
              done.set(true);
              myLock.notifyAll();
            }
          });
          if (!done.get()) {
            myLock.wait();
          }
        }
        catch (InterruptedException e) {
          return false;
        }
      }
    }
    return true;
  }

  private final Object myLock = new Object();

  private static final Logger LOG = Logger.getInstance(VcsProjectSetProcessor.class);

  public static String[] splitUrl(String s) {
    String[] split = s.split(" ");
    if (split.length == 2) return split;
    int i = s.lastIndexOf('/');
    String url = s.substring(0, i);
    String path = s.substring(i + 1);
    return new String[] { url, path };
  }
}
