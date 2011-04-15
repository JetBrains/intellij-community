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

package org.jetbrains.android.dom;

import com.android.sdklib.SdkConstants;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.util.Computable;
import com.intellij.psi.xml.XmlFile;
import com.intellij.util.xml.DomElement;
import com.intellij.util.xml.DomFileDescription;
import org.jetbrains.android.resourceManagers.ResourceManager;
import org.jetbrains.android.util.AndroidUtils;
import org.jetbrains.android.facet.AndroidFacet;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Created by IntelliJ IDEA.
 * User: Eugene.Kudelevsky
 * Date: Mar 27, 2009
 * Time: 2:30:41 PM
 * To change this template use File | Settings | File Templates.
 */
public abstract class AndroidResourceDomFileDescription<T extends DomElement> extends DomFileDescription<T> {
  private final String[] myResourceTypes;

  public AndroidResourceDomFileDescription(final Class<T> rootElementClass,
                                           @NonNls final String rootTagName,
                                           @Nullable String... resourceTypes) {
    super(rootElementClass, rootTagName);
    myResourceTypes = resourceTypes;
  }

  @Override
  public boolean isMyFile(@NotNull final XmlFile file, @Nullable Module module) {
    return doIsMyFile(file, myResourceTypes);
  }

  public static boolean doIsMyFile(final XmlFile file, final String[] resourceTypes) {
    return ApplicationManager.getApplication().runReadAction(new Computable<Boolean>() {
      public Boolean compute() {
        if (file.getProject().isDisposed()) {
          return false;
        }
        for (String resourceType : resourceTypes) {
          if (ResourceManager.isInResourceSubdirectory(file, resourceType)) {
            return AndroidFacet.getInstance(file) != null;
          }
        }
        return false;
      }
    });
  }

  protected void initializeFileDescription() {
    registerNamespacePolicy(AndroidUtils.NAMESPACE_KEY, SdkConstants.NS_RESOURCES);
  }

  @NotNull
  public String[] getResourceTypes() {
    return myResourceTypes;
  }
}
