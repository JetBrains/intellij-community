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

import com.android.SdkConstants;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.util.Computable;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.vfs.VfsUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiDirectory;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiManager;
import com.intellij.psi.util.CachedValue;
import com.intellij.psi.util.CachedValueProvider;
import com.intellij.psi.util.CachedValuesManager;
import com.intellij.psi.util.PsiModificationTracker;
import com.intellij.psi.xml.XmlFile;
import com.intellij.xml.XmlSchemaProvider;
import gnu.trove.THashMap;
import org.jetbrains.android.dom.manifest.Manifest;
import org.jetbrains.android.dom.manifest.ManifestDomFileDescription;
import org.jetbrains.android.facet.AndroidFacet;
import org.jetbrains.android.util.AndroidCommonUtils;
import org.jetbrains.android.util.AndroidResourceUtil;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.net.URL;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * Created by IntelliJ IDEA.
 * User: Eugene.Kudelevsky
 * Date: Aug 12, 2009
 * Time: 6:49:18 PM
 * To change this template use File | Settings | File Templates.
 */
public class AndroidXmlSchemaProvider extends XmlSchemaProvider {
  private static final Key<Map<String, CachedValue<XmlFile>>> DESCRIPTORS_MAP_IN_MODULE = Key.create("ANDROID_DESCRIPTORS_MAP_IN_MODULE");

  @NonNls private static final String NAMESPACE_PREFIX = "http://schemas.android.com/apk/res/";

  @Override
  public XmlFile getSchema(@NotNull @NonNls String url, @Nullable final Module module, @NotNull PsiFile baseFile) {
    if (module == null) return null;
    assert AndroidFacet.getInstance(module) != null;

    Map<String, CachedValue<XmlFile>> descriptors = module.getUserData(DESCRIPTORS_MAP_IN_MODULE);
    if (descriptors == null) {
      descriptors = new THashMap<String, CachedValue<XmlFile>>();
      module.putUserData(DESCRIPTORS_MAP_IN_MODULE, descriptors);
    }
    CachedValue<XmlFile> reference = descriptors.get(url);
    if (reference != null) {
      return reference.getValue();
    }
    CachedValuesManager manager = CachedValuesManager.getManager(module.getProject());
    reference = manager.createCachedValue(new CachedValueProvider<XmlFile>() {
      public Result<XmlFile> compute() {
        final URL resource = AndroidXmlSchemaProvider.class.getResource("android.xsd");
        final VirtualFile fileByURL = VfsUtil.findFileByURL(resource);
        XmlFile result = (XmlFile)PsiManager.getInstance(module.getProject()).findFile(fileByURL).copy();
        return new Result<XmlFile>(result, PsiModificationTracker.MODIFICATION_COUNT);
      }
    }, false);

    descriptors.put(url, reference);
    return reference.getValue();
  }

  @Override
  public boolean isAvailable(@NotNull final XmlFile file) {
    return ApplicationManager.getApplication().runReadAction(new Computable<Boolean>() {
      public Boolean compute() {
        if (isXmlResourceFile(file) ||
            ManifestDomFileDescription.isManifestFile(file)) {
          return AndroidFacet.getInstance(file) != null;
        }
        return false;
      }
    });
  }

  private static boolean isXmlResourceFile(XmlFile file) {
    if (!AndroidResourceUtil.isInResourceSubdirectory(file, null)) {
      return false;
    }

    final PsiDirectory parent = file.getParent();
    if (parent == null) {
      return false;
    }

    final String resType = AndroidCommonUtils.getResourceTypeByDirName(parent.getName());
    if (resType == null) {
      return false;
    }

    return !resType.equals("raw");
  }

  @NotNull
  @Override
  public Set<String> getAvailableNamespaces(@NotNull XmlFile file, @Nullable String tagName) {
    Set<String> result = new HashSet<String>();
    AndroidFacet facet = AndroidFacet.getInstance(file);
    if (facet != null) {
      String localNs = getLocalXmlNamespace(facet);
      if (localNs != null) {
        result.add(localNs);
      }
      result.add(SdkConstants.NS_RESOURCES);
    }
    return result;
  }

  @Override
  public String getDefaultPrefix(@NotNull @NonNls String namespace, @NotNull XmlFile context) {
    return "android";
  }

  @Nullable
  private static String getLocalXmlNamespace(@NotNull AndroidFacet facet) {
    final Manifest manifest = facet.getManifest();
    if (manifest != null) {
      String aPackage = ApplicationManager.getApplication().runReadAction(new Computable<String>() {
        @Nullable
        public String compute() {
          return manifest.getPackage().getValue();
        }
      });
      if (aPackage != null && aPackage.length() != 0) {
        return NAMESPACE_PREFIX + aPackage;
      }
    }
    return null;
  }
}
