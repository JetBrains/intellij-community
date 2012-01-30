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

import com.android.resources.ResourceType;
import com.android.sdklib.IAndroidTarget;
import com.android.sdklib.SdkConstants;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.SmartPointerManager;
import com.intellij.psi.SmartPsiElementPointer;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.xml.*;
import com.intellij.util.containers.HashMap;
import com.intellij.util.containers.HashSet;
import com.intellij.util.indexing.FileBasedIndex;
import com.intellij.util.xml.ConvertContext;
import org.jetbrains.android.AndroidIdIndex;
import org.jetbrains.android.AndroidValueResourcesIndex;
import org.jetbrains.android.dom.attrs.AttributeDefinitions;
import org.jetbrains.android.dom.resources.ResourceElement;
import org.jetbrains.android.facet.AndroidFacet;
import org.jetbrains.android.sdk.AndroidPlatform;
import org.jetbrains.android.sdk.AndroidTargetData;
import org.jetbrains.android.util.AndroidResourceUtil;
import org.jetbrains.android.util.ResourceEntry;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;

/**
 * @author coyote
 */
public class SystemResourceManager extends ResourceManager {
  private static final Logger LOG = Logger.getInstance("#org.jetbrains.android.resourceManagers.SystemResourceManager");
  
  private volatile Map<String, List<SmartPsiElementPointer<? extends PsiElement>>> myIdMap;
  
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

  @NotNull
  @Override
  public Collection<String> getValueResourceNames(@NotNull String resourceType) {
    final ResourceType type = ResourceType.getEnum(resourceType);
    
    if (type == null) {
      LOG.error("Unknown resource type " + resourceType);
      return Collections.emptyList();
    }
    final Set<String> result = new HashSet<String>();
    final ResourceEntry typeMarkerEntry = AndroidValueResourcesIndex.createTypeMarkerEntry(resourceType);
    final List<Set<ResourceEntry>> values = FileBasedIndex.getInstance()
      .getValues(AndroidValueResourcesIndex.INDEX_ID, typeMarkerEntry, GlobalSearchScope.moduleWithLibrariesScope(myModule));

    for (Set<ResourceEntry> entrySet : values) {
      for (ResourceEntry entry : entrySet) {
        result.add(entry.getName());
      }
    }
    return result;
  }

  @Nullable
  public static SystemResourceManager getInstance(@NotNull ConvertContext context) {
    AndroidFacet facet = AndroidFacet.getInstance(context);
    return facet != null ? facet.getSystemResourceManager() : null;
  }

  @Nullable
  public synchronized AttributeDefinitions getAttributeDefinitions() {
    final AndroidTargetData targetData = myPlatform.getSdk().getTargetData(myPlatform.getTarget());
    return targetData != null ? targetData.getAttrDefs(myModule.getProject()) : null;
  }

  @Nullable
  public List<PsiElement> findIdDeclarations(@NotNull String id) {
    if (myIdMap == null) {
      myIdMap = createIdMap();
      return doFindIdDeclarations(id, false);
    }
    return doFindIdDeclarations(id, true);
  }

  private List<PsiElement> doFindIdDeclarations(@NotNull String id, boolean recreateMapIfCannotResolve) {
    final List<SmartPsiElementPointer<? extends PsiElement>> pointers = myIdMap.get(id);

    if (pointers == null || pointers.size() == 0) {
      return Collections.emptyList();
    }
    final List<PsiElement> result = new ArrayList<PsiElement>();

    for (SmartPsiElementPointer<? extends PsiElement> pointer : pointers) {
      final PsiElement element = pointer.getElement();
      
      if (element != null) {
        result.add(element);
      }
      else if (recreateMapIfCannotResolve) {
        myIdMap = createIdMap();
        return doFindIdDeclarations(id, false);
      }
    }
    return result;
  }

  @NotNull
  public Collection<String> getIds() {
    if (myIdMap == null) {
      myIdMap = createIdMap();
    }
    return myIdMap.keySet();
  }

  @NotNull
  @Override
  public List<ResourceElement> findValueResources(@NotNull String resourceType,
                                                  @NotNull String resourceName,
                                                  boolean distinguishDelimetersInName) {
    final ResourceType type = ResourceType.getEnum(resourceType);
    if (type == null) {
      LOG.error("Unknown resource type " + resourceType);
      return Collections.emptyList();
    }

    final Collection<VirtualFile> files = FileBasedIndex.getInstance()
      .getContainingFiles(AndroidValueResourcesIndex.INDEX_ID, new ResourceEntry(resourceType, resourceName),
                          GlobalSearchScope.moduleWithLibrariesScope(
                            myModule));

    if (files.size() == 0) {
      return Collections.emptyList();
    }
    final Set<VirtualFile> fileSet = new HashSet<VirtualFile>(files);
    final List<ResourceElement> result = new ArrayList<ResourceElement>();
    
    for (ResourceElement element : getValueResources(resourceType, fileSet)) {
      final String name = element.getName().getValue();

      if (equal(resourceName, name, distinguishDelimetersInName)) {
        result.add(element);
      }
    }
    return result;
  }

  @NotNull
  public Map<String, List<SmartPsiElementPointer<? extends PsiElement>>> createIdMap() {
    Map<String, List<SmartPsiElementPointer<? extends PsiElement>>> result = new HashMap<String, List<SmartPsiElementPointer<? extends PsiElement>>>();
    fillIdMap(result);
    return result;
  }

  protected void fillIdMap(@NotNull Map<String, List<SmartPsiElementPointer<? extends PsiElement>>> result) {
    for (String resType : AndroidIdIndex.RES_TYPES_CONTAINING_ID_DECLARATIONS) {
      List<PsiFile> resFiles = findResourceFiles(resType);
      for (PsiFile resFile : resFiles) {
        collectIdDeclarations(resFile, result);
      }
    }
  }

  protected static void collectIdDeclarations(PsiFile psiFile, Map<String, List<SmartPsiElementPointer<? extends PsiElement>>> result) {
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

  private static void fillMapRecursively(@NotNull XmlTag tag, @NotNull Map<String, List<SmartPsiElementPointer<? extends PsiElement>>> result) {
    XmlAttribute idAttr = tag.getAttribute("id", SdkConstants.NS_RESOURCES);
    if (idAttr != null) {
      XmlAttributeValue idAttrValue = idAttr.getValueElement();
      if (idAttrValue != null) {
        if (AndroidResourceUtil.isIdDeclaration(idAttrValue)) {
          String id = AndroidResourceUtil.getResourceNameByReferenceText(idAttrValue.getValue());
          if (id != null) {
            List<SmartPsiElementPointer<? extends PsiElement>> list = result.get(id);
            
            if (list == null) {
              list = new ArrayList<SmartPsiElementPointer<? extends PsiElement>>();
              result.put(id, list);
            }
            final SmartPointerManager manager = SmartPointerManager.getInstance(tag.getProject());
            list.add(manager.createSmartPsiElementPointer(idAttr));
          }
        }
      }
    }
    for (XmlTag subtag : tag.getSubTags()) {
      fillMapRecursively(subtag, result);
    }
  }
}
