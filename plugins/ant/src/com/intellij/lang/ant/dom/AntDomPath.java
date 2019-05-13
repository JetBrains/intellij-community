/*
 * Copyright 2000-2010 JetBrains s.r.o.
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
package com.intellij.lang.ant.dom;

import com.intellij.lang.ant.AntFilesProvider;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.util.Iterator;
import java.util.List;
import java.util.Set;

/**
 * @author Eugene Zhuravlev
 */
public abstract class AntDomPath extends AntDomPathElement{

  @Override
  @NotNull
  protected List<File> getFiles(AntDomPattern pattern, Set<AntFilesProvider> processed) {
    final List<File> files = super.getFiles(pattern, processed);

    for (Iterator<AntDomElement> iterator = getAntChildrenIterator(); iterator.hasNext();) {
      AntDomElement child = iterator.next();
      if (child instanceof AntFilesProvider) {
        files.addAll(((AntFilesProvider)child).getFiles(processed));
      }
    }
    return files;
  }

}
