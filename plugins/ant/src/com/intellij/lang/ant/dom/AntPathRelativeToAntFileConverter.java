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
package com.intellij.lang.ant.dom;

import com.intellij.util.xml.ConvertContext;
import com.intellij.util.xml.GenericAttributeValue;
import org.jetbrains.annotations.Nullable;

/**
 * @author Eugene Zhuravlev
 */
public class AntPathRelativeToAntFileConverter extends AntPathConverter {
  protected AntDomProject getEffectiveAntProject(GenericAttributeValue attribValue) {
    return attribValue.getParentOfType(AntDomProject.class, false);
  }

  @Nullable
  protected String getPathResolveRoot(ConvertContext context, AntDomProject antProject) {
    return antProject.getContainingFileDir();
  }
}
