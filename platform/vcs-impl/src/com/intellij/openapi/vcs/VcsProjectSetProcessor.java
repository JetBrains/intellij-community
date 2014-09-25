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
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.projectImport.ProjectSetProcessor;

import java.net.URI;
import java.net.URISyntaxException;

/**
 * @author Dmitry Avdeev
 */
public class VcsProjectSetProcessor extends ProjectSetProcessor<VcsProjectSetProcessor.State> {

  private static final Logger LOG = Logger.getInstance(VcsProjectSetProcessor.class);

  @Override
  public String getId() {
    return "vcs";
  }

  @Override
  public State interactWithUser() {
    FileChooserDescriptor descriptor = new FileChooserDescriptor(false, true, false, false, false, false);
    descriptor.setTitle("Select Destination Folder");
    VirtualFile[] files = FileChooser.chooseFiles(descriptor, null, null);
    if (files.length == 0) return null;
    return new State(files[0]);
  }

  @Override
  public void processEntry(String key, String value, State state) {
    if ("url".equals(key)) {
      try {
        String protocol = new URI(value).getScheme();
        VcsCheckoutProcessor processor = VcsCheckoutProcessor.getProcessor(protocol);
        if (processor == null) {
          LOG.error("Checkout processor not found for " + protocol);
        }
        else {
          processor.checkout(value, "foo", state.targetDirectory);
        }
      }
      catch (URISyntaxException e) {
        LOG.error(e);
      }
    }
  }

  public static class State {
    public final VirtualFile targetDirectory;

    public State(VirtualFile targetDirectory) {

      this.targetDirectory = targetDirectory;
    }
  }
}
