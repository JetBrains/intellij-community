// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.lang.ant.dom;

import com.intellij.lang.ant.AntFilesProvider;
import com.intellij.psi.PsiFileSystemItem;
import com.intellij.util.xml.Attribute;
import com.intellij.util.xml.Convert;
import com.intellij.util.xml.GenericAttributeValue;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * @author Eugene Zhuravlev
 */
public abstract class AntDomPathElement extends AntDomFilesProviderImpl{

  @Attribute("location")
  @Convert(value = AntPathConverter.class)
  public abstract GenericAttributeValue<PsiFileSystemItem> getLocation();

  @Attribute("path")
  @Convert(value = AntMultiPathStringConverter.class)
  public abstract GenericAttributeValue<List<File>> getPath();


  @Override
  protected @Nullable AntDomPattern getAntPattern() {
    return null; // not available
  }

  @Override
  protected @NotNull List<File> getFiles(AntDomPattern pattern, Set<AntFilesProvider> processed) {
    final List<File> files = new ArrayList<>();
    final File baseDir = getCanonicalFile(".");

    addLocation(baseDir, files, getLocation().getStringValue());

    final String pathString = getPath().getStringValue();
    if (pathString != null) {
      final PathTokenizer tokenizer = new PathTokenizer(pathString);
      while (tokenizer.hasMoreTokens()) {
        addLocation(baseDir, files, tokenizer.nextToken());
      }
    }

    return files;
  }

  private static void addLocation(final File baseDir, final List<? super File> files, final String locationPath) {
    if (locationPath != null) {
      File file = new File(locationPath);
      if (file.isAbsolute()) {
        files.add(file);
      }
      else {
        files.add(new File(baseDir, locationPath));
      }
    }
  }
}
