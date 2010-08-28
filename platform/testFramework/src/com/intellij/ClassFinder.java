/*
 * Copyright 2000-2009 JetBrains s.r.o.
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

/*
 * Created by IntelliJ IDEA.
 * User: mike
 * Date: Jun 7, 2002
 * Time: 8:27:57 PM
 * To change template for new class use
 * Code Style | Class Templates options (Tools | IDE Options).
 */
package com.intellij;

import com.intellij.openapi.util.text.StringUtil;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import static com.intellij.util.containers.ContainerUtil.addIfNotNull;

public class ClassFinder {
  private final List<String> classNameList = new ArrayList<String>();
  private final int startPackageName;

  public ClassFinder(final File classPathRoot, final String packageRoot) throws IOException {
    startPackageName = classPathRoot.getAbsolutePath().length() + 1;
    String directoryOffset = packageRoot.replace('.', File.separatorChar);
    findAndStoreTestClasses(new File(classPathRoot, directoryOffset));
  }

  @Nullable
  private String computeClassName(final File file) {
    String absPath = file.getAbsolutePath();
    if (absPath.endsWith("Test.class")) {
      return StringUtil.trimEnd(absPath.substring(startPackageName), ".class").replace(File.separatorChar, '.');
    }
    return null;
  }

  private void findAndStoreTestClasses(final File current) throws IOException {
    if (current.isDirectory()) {
      for (File file : current.listFiles()) {
        findAndStoreTestClasses(file);
      }
    }
    else {
      addIfNotNull(classNameList, computeClassName(current));
    }
  }

  public Collection<String> getClasses() {
    return classNameList;
  }
}
