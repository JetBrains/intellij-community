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
package org.jetbrains.android.resourceManagers;

import com.android.sdklib.IAndroidTarget;
import com.android.sdklib.SdkConstants;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.xml.*;
import com.intellij.util.xml.ConvertContext;
import org.jetbrains.android.AndroidIdIndex;
import org.jetbrains.android.dom.attrs.AttributeDefinitions;
import org.jetbrains.android.facet.AndroidFacet;
import org.jetbrains.android.sdk.AndroidPlatform;
import org.jetbrains.android.sdk.AndroidTargetData;
import org.jetbrains.android.util.AndroidResourceUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;

/**
 * @author coyote
 */
public class SystemResourceManager extends ResourceManager {
  private volatile Map<String, List<PsiElement>> myIdMap;

  private final AndroidPlatform myPlatform;

  public SystemResourceManager(@NotNull Module module, @NotNull AndroidPlatform androidPlatform) {
    super(module);
    myPlatform = androidPlatform;
  }

  @NotNull
  public VirtualFile[] getAllResourceDirs() {
    VirtualFile resDir = getResourceDir();
    return resDir != null ? new VirtualFile[]{resDir} : VirtualFile.EMPTY_ARRAY;
  }

  @Nullable
  public VirtualFile getResourceDir() {
    String resPath = myPlatform.getTarget().getPath(IAndroidTarget.RESOURCES);
    return LocalFileSystem.getInstance().findFileByPath(resPath);
  }

  @Nullable
  public static SystemResourceManager getInstance(@NotNull ConvertContext context) {
    AndroidFacet facet = AndroidFacet.getInstance(context);
    return facet != null ? facet.getSystemResourceManager() : null;
  }

  @Nullable
  public synchronized AttributeDefinitions getAttributeDefinitions() {
    final AndroidTargetData targetData = myPlatform.getSdk().getTargetData(myPlatform.getTarget());
    return targetData.getAttrDefs(myModule.getProject());
  }

  @Nullable
  public List<PsiElement> findIdDeclarations(@NotNull String id) {
    if (myIdMap == null) {
      myIdMap = createIdMap();
    }
    return myIdMap.get(id);
  }

  @NotNull
  public Collection<String> getIds() {
    if (myIdMap == null) {
      myIdMap = createIdMap();
    }
    return myIdMap.keySet();
  }

  @NotNull
  public Map<String, List<PsiElement>> createIdMap() {
    Map<String, List<PsiElement>> result = new HashMap<String, List<PsiElement>>();
    fillIdMap(result);
    return result;
  }

  protected void fillIdMap(@NotNull Map<String, List<PsiElement>> result) {
    for (String resType : AndroidIdIndex.RES_TYPES_CONTAINING_ID_DECLARATIONS) {
      List<PsiFile> resFiles = findResourceFiles(resType);
      for (PsiFile resFile : resFiles) {
        collectIdDeclarations(resFile, result);
      }
    }
  }

  protected static void collectIdDeclarations(PsiFile psiFile, Map<String, List<PsiElement>> result) {
    if (psiFile instanceof XmlFile) {
      XmlDocument document = ((XmlFile)psiFile).getDocument();
      if (document != null) {
        XmlTag rootTag = document.getRootTag();
        if (rootTag != null) {
          fillMapRecursively(rootTag, result);
        }
      }
    }
  }

  private static void fillMapRecursively(@NotNull XmlTag tag, Map<String, List<PsiElement>> result) {
    XmlAttribute idAttr = tag.getAttribute("id", SdkConstants.NS_RESOURCES);
    if (idAttr != null) {
      XmlAttributeValue idAttrValue = idAttr.getValueElement();
      if (idAttrValue != null) {
        if (AndroidResourceUtil.isIdDeclaration(idAttrValue)) {
          String id = AndroidResourceUtil.getResourceNameByReferenceText(idAttrValue.getValue());
          if (id != null) {
            List<PsiElement> list = result.get(id);
            if (list == null) {
              list = new ArrayList<PsiElement>();
              result.put(id, list);
            }
            list.add(idAttrValue);
          }
        }
      }
    }
    for (XmlTag subtag : tag.getSubTags()) {
      fillMapRecursively(subtag, result);
    }
  }
}
