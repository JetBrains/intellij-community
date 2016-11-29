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
 *         Date: Jun 22, 2010
 */
public abstract class AntDomFileList extends AntDomFilesProviderImpl{

  @Attribute("dir")
  @Convert(value = AntPathConverter.class)
  public abstract GenericAttributeValue<PsiFileSystemItem> getDir();

  @Attribute("files")
  public abstract GenericAttributeValue<String> getFilesString();

  @SubTagList("file")
  public abstract List<AntDomNamedElement> getFiles(); // todo: add filename completion relative to the filelist's basedir

  
  @Nullable
  protected AntDomPattern getAntPattern() {
    return null; // not available for this data type
  }

  @NotNull
  protected List<File> getFiles(@Nullable AntDomPattern pattern, Set<AntFilesProvider> processed) {
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
