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
package org.jetbrains.android.dom.manifest;

import com.android.sdklib.SdkConstants;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleUtil;
import com.intellij.psi.xml.XmlFile;
import com.intellij.util.xml.DomFileDescription;
import org.jetbrains.android.facet.AndroidFacet;
import org.jetbrains.android.util.AndroidUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author yole
 */
public class ManifestDomFileDescription extends DomFileDescription<Manifest> {
  public ManifestDomFileDescription() {
    super(Manifest.class, "manifest");
  }

  public boolean isMyFile(@NotNull XmlFile file, @Nullable Module module) {
    return isManifestFile(file);
  }

  public static boolean isManifestFile(@NotNull XmlFile file) {
    if (!file.getName().equals(SdkConstants.FN_ANDROID_MANIFEST_XML)) {
      return false;
    }
    final Module module = ModuleUtil.findModuleForPsiElement(file);
    return module == null || AndroidFacet.getInstance(module) != null;
  }

  protected void initializeFileDescription() {
    registerNamespacePolicy(AndroidUtils.NAMESPACE_KEY, SdkConstants.NS_RESOURCES);
  }
}
