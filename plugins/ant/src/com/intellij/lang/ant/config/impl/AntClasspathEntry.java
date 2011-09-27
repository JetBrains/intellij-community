/*
 * Copyright 2000-2011 JetBrains s.r.o.
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
package com.intellij.lang.ant.config.impl;

import com.intellij.openapi.roots.ui.CellAppearanceEx;
import com.intellij.openapi.util.InvalidDataException;
import com.intellij.openapi.util.WriteExternalException;
import com.intellij.util.PathUtil;
import com.intellij.util.config.Externalizer;
import org.jdom.Element;

import java.io.File;
import java.util.List;

public interface AntClasspathEntry {
  Externalizer<AntClasspathEntry> EXTERNALIZER = new Externalizer<AntClasspathEntry>() {
    public AntClasspathEntry readValue(Element dataElement) throws InvalidDataException {
      String pathUrl = dataElement.getAttributeValue(SinglePathEntry.PATH);
      if (pathUrl != null)
        return new SinglePathEntry(PathUtil.toPresentableUrl(pathUrl));
      String dirUrl = dataElement.getAttributeValue(AllJarsUnderDirEntry.DIR);
      if (dirUrl != null)
        return new AllJarsUnderDirEntry(PathUtil.toPresentableUrl(dirUrl));
      throw new InvalidDataException();
    }

    public void writeValue(Element dataElement, AntClasspathEntry entry) throws WriteExternalException {
      entry.writeExternal(dataElement);
    }
  };

  String getPresentablePath();

  void writeExternal(Element dataElement) throws WriteExternalException;

  void addFilesTo(List<File> files);

  CellAppearanceEx getAppearance();
}
