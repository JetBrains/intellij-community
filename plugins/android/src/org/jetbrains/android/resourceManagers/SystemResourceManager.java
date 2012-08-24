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
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.JavaPsiFacade;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiField;
import com.intellij.util.containers.HashMap;
import com.intellij.util.containers.HashSet;
import com.intellij.util.xml.ConvertContext;
import org.jetbrains.android.dom.attrs.AttributeDefinitions;
import org.jetbrains.android.facet.AndroidFacet;
import org.jetbrains.android.sdk.AndroidPlatform;
import org.jetbrains.android.sdk.AndroidTargetData;
import org.jetbrains.android.util.AndroidResourceUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Map;
import java.util.Set;

/**
 * @author coyote
 */
public class SystemResourceManager extends ResourceManager {
  private final AndroidPlatform myPlatform;
  private final Map<String, Set<String>> myPublicResourcesCache = new HashMap<String, Set<String>>();

  public SystemResourceManager(@NotNull AndroidFacet facet, @NotNull AndroidPlatform androidPlatform) {
    super(facet);
    myPlatform = androidPlatform;
    final Module module = facet.getModule();

    ApplicationManager.getApplication().runReadAction(new Runnable() {
      @Override
      public void run() {
        buildPublicResourceCache(module);
      }
    });
  }

  private void buildPublicResourceCache(@NotNull Module module) {
    final PsiClass rClass = JavaPsiFacade.getInstance(module.getProject())
      .findClass(SdkConstants.CLASS_R, module.getModuleWithDependenciesAndLibrariesScope(false));
    if (rClass == null) {
      return;
    }

    for (PsiClass resourceClass : rClass.getInnerClasses()) {
      final String resType = resourceClass.getName();

      if (resType != null) {
        Set<String> resNameSet = myPublicResourcesCache.get(resType);

        if (resNameSet == null) {
          resNameSet = new HashSet<String>();
          myPublicResourcesCache.put(resType, resNameSet);
        }

        for (PsiField resField : resourceClass.getFields()) {
          final String resName = resField.getName();

          if (resName != null) {
            resNameSet.add(resName);
          }
        }
      }
    }
  }

  protected boolean isResourcePublic(@NotNull String type, @NotNull String name) {
    final Set<String> fieldNames = myPublicResourcesCache.get(type);
    if (fieldNames == null || fieldNames.isEmpty()) {
      return false;
    }
    final String fieldName = AndroidResourceUtil.getFieldNameByResourceName(name);
    return fieldNames.contains(fieldName);
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
    final AndroidTargetData targetData = myPlatform.getSdkData().getTargetData(myPlatform.getTarget());
    if (targetData == null) {
      return null;
    }
    final AttributeDefinitions attrDefs = targetData.getAttrDefs(myModule.getProject());
    return attrDefs != null ? new MyAttributeDefinitions(attrDefs) : null;
  }

  private class MyAttributeDefinitions extends FilteredAttributeDefinitions {
    protected MyAttributeDefinitions(@NotNull AttributeDefinitions wrappee) {
      super(wrappee);
    }

    @Override
    protected boolean isAttributeAcceptable(@NotNull String name) {
      return isResourcePublic(ResourceType.ATTR.getName(), name);
    }
  }
}
