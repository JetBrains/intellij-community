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
package org.jetbrains.android.dom.drawable;

import com.android.resources.ResourceType;
import com.intellij.openapi.module.Module;
import com.intellij.psi.xml.XmlFile;
import com.intellij.psi.xml.XmlTag;
import com.intellij.util.ArrayUtil;
import org.jetbrains.android.dom.AndroidResourceDomFileDescription;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author Eugene.Kudelevsky
 */
public class LayerListDomFileDescription extends AndroidResourceDomFileDescription<LayerList> {
  @NonNls static final String[] POSSIBLE_ROOT_TAGS = {"layer-list", "transition"};

  public LayerListDomFileDescription() {
    super(LayerList.class, POSSIBLE_ROOT_TAGS[0], ResourceType.DRAWABLE.getName());
  }

  @Override
  public boolean acceptsOtherRootTagNames() {
    return true;
  }

  @Override
  public boolean isMyFile(@NotNull XmlFile file, @Nullable Module module) {
    if (!super.isMyFile(file, module)) {
      return false;
    }

    final XmlTag rootTag = file.getRootTag();
    if (rootTag == null) {
      return false;
    }

    return ArrayUtil.find(POSSIBLE_ROOT_TAGS, rootTag.getName()) >= 0;
  }
}
