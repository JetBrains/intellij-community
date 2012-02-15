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
package org.jetbrains.android.dom.resources;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.util.Computable;
import com.intellij.util.xml.DomFileDescription;
import com.intellij.psi.xml.XmlFile;
import com.intellij.openapi.module.Module;
import org.jetbrains.android.util.AndroidResourceUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author yole
 */
public class ResourcesDomFileDescription extends DomFileDescription<Resources> {
  public ResourcesDomFileDescription() {
    super(Resources.class, "resources");
  }

  @Override
  public boolean isMyFile(@NotNull final XmlFile file, @Nullable Module module) {
    return ApplicationManager.getApplication().runReadAction(new Computable<Boolean>() {
      public Boolean compute() {
        return AndroidResourceUtil.isInResourceSubdirectory(file, "values");
      }
    });
  }
}
