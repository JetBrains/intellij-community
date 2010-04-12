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
package com.intellij.lang.ant;

import com.intellij.lang.ant.config.AntBuildFile;
import com.intellij.lang.ant.psi.impl.AntFileImpl;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.xml.XmlDocument;
import com.intellij.psi.xml.XmlFile;
import com.intellij.psi.xml.XmlFileNSInfoProvider;
import com.intellij.psi.xml.XmlTag;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

/**
 * @author Maxim.Mossienko, lvo
 */
public class AntDefaultNSProvider implements XmlFileNSInfoProvider {
  @NonNls
  private static final String ANT_URI = "http://ant.apache.org/schema.xsd"; // XmlUtil.ANT_URI
  private static final String[][] myNamespaces = new String[][]{new String[]{"", ANT_URI}};

  // nsPrefix, namespaceId
  public String[][] getDefaultNamespaces(@NotNull XmlFile xmlFile) {
    if (xmlFile.getCopyableUserData(AntBuildFile.ANT_BUILD_FILE_KEY) != null) return myNamespaces;
    final XmlDocument document = xmlFile.getDocument();
    if (document != null) {
      final XmlTag tag = document.getRootTag();
      if (tag != null && AntFileImpl.PROJECT_TAG.equals(tag.getName()) && tag.getContext() instanceof XmlDocument) {
        if (tag.getAttributeValue(AntFileImpl.DEFAULT_ATTR) != null) {
          return myNamespaces;
        }
        final VirtualFile file = xmlFile.getVirtualFile();
        if (file != null && ForcedAntFileAttribute.isAntFile(file)) {
          return myNamespaces;
        }
      }
    }
    return null;
  }
}
