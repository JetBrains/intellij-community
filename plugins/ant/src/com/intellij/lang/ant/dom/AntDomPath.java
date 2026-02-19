// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
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
  protected @NotNull List<File> getFiles(AntDomPattern pattern, Set<AntFilesProvider> processed) {
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
