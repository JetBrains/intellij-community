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
import com.intellij.util.Consumer;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.List;

/**
 * @author Dmitry Avdeev
 */
public class VcsProjectSetProcessor extends ProjectSetProcessor {

  private static final Logger LOG = Logger.getInstance(VcsProjectSetProcessor.class);

  public static String[] splitUrl(String s) {
    String[] split = s.split(" ");
    if (split.length == 2) return split;
    int i = s.lastIndexOf('/');
    String url = s.substring(0, i);
    String path = s.substring(i + 1);
    return new String[] { url, path };
  }

  @Override
  public String getId() {
    return "vcs";
  }

  @Override
  public void processEntries(@NotNull List<Pair<String, String>> entries, @Nullable Object param, @NotNull final Consumer<Object> onFinish) {

    final VirtualFile directory;
    if (param instanceof VirtualFile) {
      directory = (VirtualFile)param;
    }
    else {
      FileChooserDescriptor descriptor = new FileChooserDescriptor(false, true, false, false, false, false);
      descriptor.setTitle("Select Destination Folder");
      descriptor.setDescription("");
      VirtualFile[] files = FileChooser.chooseFiles(descriptor, null, null);
      if (files.length == 0) return;
      directory = files[0];
    }
    for (Pair<String, String> pair: entries) {
      if ("url".equals(pair.getFirst())) {
        try {
          String url = pair.getSecond();
          String[] split = splitUrl(url);
          String protocol = new URI(url).getScheme();
          VcsCheckoutProcessor processor = VcsCheckoutProcessor.getProcessor(protocol);
          if (processor == null) {
            LOG.error("Checkout processor not found for " + protocol);
          }
          else {
            processor.checkout(split[0], split[1], directory, new CheckoutProvider.Listener() {
              @Override
              public void directoryCheckedOut(File directory, VcsKey vcs) {

              }

              @Override
              public void checkoutCompleted() {
                onFinish.consume(directory);
              }
            });
          }
        }
        catch (URISyntaxException e) {
          LOG.error(e);
        }
      }
    }
  }
}
