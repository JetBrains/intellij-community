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

import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiFileSystemItem;
import com.intellij.util.xml.Attribute;
import com.intellij.util.xml.Convert;
import com.intellij.util.xml.ConvertContext;
import com.intellij.util.xml.GenericAttributeValue;

/**
 * @author Eugene Zhuravlev
 */
public abstract class AntDomAnt extends AntDomElement {
  public static final String DEFAULT_ANTFILE_NAME = "build.xml";

  @Attribute("antfile")
  @Convert(value = AntFilePathConverter.class)
  public abstract GenericAttributeValue<PsiFileSystemItem> getAntFilePath();

  @Attribute("dir")
  @Convert(value = AntPathConverter.class)
  public abstract GenericAttributeValue<PsiFileSystemItem> getAntFileDir();

  @Attribute("target")
  @Convert(value = AntDomDefaultTargetConverter.class)
  public abstract GenericAttributeValue<TargetResolver.Result> getDefaultTarget();

  @Attribute("output")
  @Convert(value = AntPathConverter.class)
  public abstract GenericAttributeValue<PsiFileSystemItem> getOutputtFileName();

  @Attribute("inheritall")
  @Convert(value = AntBooleanConverterDefaultTrue.class)
  public abstract GenericAttributeValue<Boolean> isInheritAllProperties();

  @Attribute("inheritrefs")
  @Convert(value = AntBooleanConverterDefaultFalse.class)
  public abstract GenericAttributeValue<Boolean> isInheritRefsProperties();

  @Attribute("usenativebasedir")
  @Convert(value = AntBooleanConverterDefaultFalse.class)
  public abstract GenericAttributeValue<Boolean> isUseNativeBasedir();

  public static class  AntFilePathConverter extends AntPathConverter {
    public AntFilePathConverter() {
      super(true);
    }

    protected String getPathResolveRoot(ConvertContext context, AntDomProject antProject) {
      final AntDomAnt antElement = context.getInvocationElement().getParentOfType(AntDomAnt.class, false);
      if (antElement != null) {
        PsiFileSystemItem dir = antElement.getAntFileDir().getValue();
        if (dir == null) {
          if (antElement.isInheritAllProperties().getValue()) {
            dir = antProject.getProjectBasedir();
          }
        }
        if (dir != null) {
          final VirtualFile vFile = dir.getVirtualFile();
          if (vFile != null) {
            return vFile.getPath();
          }
        }
      }
      return super.getPathResolveRoot(context, antProject);
    }

    protected String getAttributeDefaultValue(ConvertContext context, GenericAttributeValue attribValue) {
      return DEFAULT_ANTFILE_NAME;
    }
  }
}
