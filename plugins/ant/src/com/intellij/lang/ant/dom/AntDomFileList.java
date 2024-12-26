// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.lang.ant.dom;

import com.intellij.lang.ant.AntFilesProvider;
import com.intellij.psi.PsiFileSystemItem;
import com.intellij.util.text.StringTokenizer;
import com.intellij.util.xml.Attribute;
import com.intellij.util.xml.Convert;
import com.intellij.util.xml.GenericAttributeValue;
import com.intellij.util.xml.SubTagList;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;

/**
 * @author Eugene Zhuravlev
 */
public abstract class AntDomFileList extends AntDomFilesProviderImpl{

  @Attribute("dir")
  @Convert(value = AntPathConverter.class)
  public abstract GenericAttributeValue<PsiFileSystemItem> getDir();

  @Attribute("files")
  public abstract GenericAttributeValue<String> getFilesString();

  @SubTagList("file")
  public abstract List<AntDomNamedElement> getFiles(); // todo: add filename completion relative to the filelist's basedir


  @Override
  protected @Nullable AntDomPattern getAntPattern() {
    return null; // not available for this data type
  }

  @Override
  protected @NotNull List<File> getFiles(@Nullable AntDomPattern pattern, Set<AntFilesProvider> processed) {
    final File root = getCanonicalFile(getDir().getStringValue());
    if (root == null) {
      return Collections.emptyList();
    }

    final ArrayList<File> files = new ArrayList<>();

    final String filenames = getFilesString().getStringValue();
    if (filenames != null) {
      final StringTokenizer tokenizer = new StringTokenizer(filenames, ", \t\n\r\f", false);
      while (tokenizer.hasMoreTokens()) {
        files.add(new File(root, tokenizer.nextToken()));
      }
    }

    for (AntDomNamedElement child : getFiles()) {
      final String fileName = child.getName().getStringValue();
      if (fileName != null) {
        files.add(new File(root, fileName));
      }
    }
    return files;
  }
}
