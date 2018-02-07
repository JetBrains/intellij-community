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
import com.intellij.util.PathUtil;
import com.intellij.util.xml.Attribute;
import com.intellij.util.xml.GenericAttributeValue;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.io.IOException;
import java.util.Collections;
import java.util.List;
import java.util.Set;

/**
 * @author Eugene Zhuravlev
 */
public abstract class AntDomFilesProviderImpl extends AntDomElement implements AntFilesProvider{
  private volatile List<File> myCachedFiles;

  @Attribute("defaultexcludes")
  public abstract GenericAttributeValue<String> getDefaultExcludes();
  @Attribute("casesensitive")
  public abstract GenericAttributeValue<String> getCaseSensitive();


  @NotNull
  public final List<File> getFiles(Set<AntFilesProvider> processed) {
    if (processed.contains(this)) {
      return Collections.emptyList();
    }
    List<File> result = myCachedFiles;
    if (result == null) {
      myCachedFiles = result = getFilesImpl(processed);
    }
    return result;
  }

  private List<File> getFilesImpl(final Set<AntFilesProvider> processed) {
    processed.add(this);
    try {
      final AntDomElement referenced = getRefId().getValue();
      if (referenced instanceof AntFilesProvider) {
        return ((AntFilesProvider)referenced).getFiles(processed);
      }
      return getFiles(getAntPattern(), processed);
    }
    finally {
      processed.remove(this);
    }
  }

  @Nullable
  protected AntDomPattern getAntPattern() {
    return AntDomPattern.create(this, shouldHonorDefaultExcludes(), matchPatternsCaseSensitive());
  }

  @NotNull
  protected List<File> getFiles(@Nullable AntDomPattern pattern, final Set<AntFilesProvider> processed) {
    return Collections.emptyList();
  }


  private boolean shouldHonorDefaultExcludes() {
    @NonNls final String value = getDefaultExcludes().getRawText();
    return value == null || value.equalsIgnoreCase("yes") || value.equalsIgnoreCase("true");
  }

  private boolean matchPatternsCaseSensitive() {
    @NonNls final String value = getCaseSensitive().getRawText();
    return value == null || value.equalsIgnoreCase("yes") || value.equalsIgnoreCase("true");
  }

  @Nullable
  protected File getCanonicalFile(final String path) {
    if (path == null) {
      return null;
    }
    try {
      final File file = new File(path);
      if (file.isAbsolute()) {
        return file.getCanonicalFile();
      }
      final String baseDir = getContextAntProject().getProjectBasedirPath();
      if (baseDir == null) {
        return null;
      }
      return new File(PathUtil.getCanonicalPath(new File(baseDir, path).getPath()));
    }
    catch (IOException e) {
      return null;
    }
  }

}
