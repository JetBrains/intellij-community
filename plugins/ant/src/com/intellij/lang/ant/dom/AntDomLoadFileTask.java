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

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.vfs.VfsUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiFileSystemItem;
import com.intellij.util.xml.Attribute;
import com.intellij.util.xml.Convert;
import com.intellij.util.xml.GenericAttributeValue;

import java.io.IOException;

/**
 * @author Eugene Zhuravlev
 */
public abstract class AntDomLoadFileTask extends AntDomPropertyDefiningTask {

  private static final Logger LOG = Logger.getInstance("#com.intellij.lang.ant.dom.AntDomLoadFileTask");
  
  private String myCachedText;

  @Attribute("srcfile")
  @Convert(value = AntPathValidatingConverter.class)
  public abstract GenericAttributeValue<PsiFileSystemItem> getSrcFile();

  @Attribute("encoding")
  public abstract GenericAttributeValue<String> getEncoding();
  
  protected String calcPropertyValue(String propertyName) {
    String text = myCachedText;
    if (text != null) {
      return text; 
    }
    final PsiFileSystemItem file = getSrcFile().getValue();
    if (!(file instanceof PsiFile)) {
      return "";
    }
    final VirtualFile vFile = ((PsiFile)file).getOriginalFile().getVirtualFile();
    if (vFile == null) {
      return "";
    }
    try {
      text = VfsUtil.loadText(vFile);
      myCachedText = text;
    }
    catch (IOException e) {
      LOG.info(e);
      text = "";
    }
    return text;
  }
}
