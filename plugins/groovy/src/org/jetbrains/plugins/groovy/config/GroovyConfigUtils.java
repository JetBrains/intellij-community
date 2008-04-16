/*
 * Copyright 2000-2008 JetBrains s.r.o.
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

package org.jetbrains.plugins.groovy.config;

import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.util.Ref;
import com.intellij.util.Processor;

import java.util.Arrays;

/**
 * @author ilyas
 */
public class GroovyConfigUtils {
  private static final String GROOVY_STARTER_FILE_NAME = "groovy";

  public static boolean isGroovySdkHome(final VirtualFile file) {
    final Ref<Boolean> result = Ref.create(false);
    processFilesUnderGDKRoot(file, new Processor<VirtualFile>() {
      public boolean process(final VirtualFile virtualFile) {
        result.set(true);
        return false;
      }
    });
    return result.get();
  }

  public static void processFilesUnderGDKRoot(VirtualFile file, final Processor<VirtualFile> processor) {
    if (file != null && file.isDirectory()) {
      final VirtualFile child = file.findChild("bin");

      if (child != null && child.isDirectory()) {
        for (VirtualFile grandChild : child.getChildren()) {
          if (GROOVY_STARTER_FILE_NAME.equals(grandChild.getNameWithoutExtension())) {
            if (!processor.process(grandChild)) return;
          }
        }
      }
    }
  }
}
