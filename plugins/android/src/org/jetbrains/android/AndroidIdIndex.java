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
package org.jetbrains.android;

import com.android.AndroidConstants;
import com.android.sdklib.SdkConstants;
import com.intellij.openapi.fileTypes.StdFileTypes;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.ex.temp.TempFileSystem;
import com.intellij.psi.PsiFile;
import com.intellij.psi.XmlRecursiveElementVisitor;
import com.intellij.psi.xml.XmlAttributeValue;
import com.intellij.psi.xml.XmlFile;
import com.intellij.util.ArrayUtil;
import com.intellij.util.containers.HashMap;
import com.intellij.util.indexing.*;
import com.intellij.util.io.EnumeratorStringDescriptor;
import com.intellij.util.io.KeyDescriptor;
import org.jetbrains.android.resourceManagers.ResourceManager;
import org.jetbrains.android.util.AndroidResourceUtil;
import org.jetbrains.annotations.NotNull;

import java.util.Collections;
import java.util.Map;

/**
 * @author Eugene.Kudelevsky
 */
public class AndroidIdIndex extends ScalarIndexExtension<String> {
  public static final String[] RES_TYPES_CONTAINING_ID_DECLARATIONS = {AndroidConstants.FD_RES_LAYOUT, AndroidConstants.FD_RES_MENU};
  public static final ID<String, Void> INDEX_ID = ID.create("android.id.index");
  public static final String MARKER = "$";

  private static final FileBasedIndex.InputFilter INPUT_FILTER = new FileBasedIndex.InputFilter() {
    public boolean acceptInput(final VirtualFile file) {
      if ((file.getFileSystem() == LocalFileSystem.getInstance() || file.getFileSystem() instanceof TempFileSystem) &&
          file.getFileType() == StdFileTypes.XML) {
        VirtualFile parent = file.getParent();
        if (parent == null || !parent.isDirectory()) {
          return false;
        }
        final String resourceType = ResourceManager.getResourceTypeByDirName(parent.getName());
        if (resourceType == null || !canContainIdDeclaration(resourceType)) {
          return false;
        }
        parent = parent.getParent();
        return parent != null && SdkConstants.FD_RES.equals(parent.getName());
      }
      return false;
    }
  };

  private static final DataIndexer<String, Void, FileContent> INDEXER = new DataIndexer<String, Void, FileContent>() {
    @NotNull
    public Map<String, Void> map(FileContent inputData) {
      PsiFile file = inputData.getPsiFile();
      if (file instanceof XmlFile) {
        final HashMap<String, Void> ids = new HashMap<String, Void>();
        file.accept(new XmlRecursiveElementVisitor() {
          @Override
          public void visitXmlAttributeValue(XmlAttributeValue attributeValue) {
            if (AndroidResourceUtil.isIdDeclaration(attributeValue)) {
              String id = AndroidResourceUtil.getResourceNameByReferenceText(attributeValue.getValue());
              if (id != null) {
                if (ids.isEmpty()) {
                  ids.put(MARKER, null);
                }
                ids.put(id, null);
              }
            }
          }
        });
        return ids;
      }
      return Collections.emptyMap();
    }
  };

  private static boolean canContainIdDeclaration(@NotNull String resType) {
    return ArrayUtil.find(RES_TYPES_CONTAINING_ID_DECLARATIONS, resType) >= 0;
  }

  @Override
  public ID<String, Void> getName() {
    return INDEX_ID;
  }

  @Override
  public DataIndexer<String, Void, FileContent> getIndexer() {
    return INDEXER;
  }

  @Override
  public KeyDescriptor<String> getKeyDescriptor() {
    return new EnumeratorStringDescriptor();
  }

  @Override
  public FileBasedIndex.InputFilter getInputFilter() {
    return INPUT_FILTER;
  }

  @Override
  public boolean dependsOnFileContent() {
    return true;
  }

  @Override
  public int getVersion() {
    return 1;
  }
}
