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

import com.intellij.lang.ant.AntSupport;
import com.intellij.lang.ant.config.AntConfigurationBase;
import com.intellij.openapi.util.Key;
import com.intellij.psi.PsiFile;
import com.intellij.psi.xml.XmlElement;
import com.intellij.psi.xml.XmlFile;
import com.intellij.psi.xml.XmlTag;
import com.intellij.util.xml.*;

import java.util.List;

/**
 * @author Eugene Zhuravlev
 *         Date: Apr 6, 2010
 */
@SuppressWarnings({"AbstractClassNeverImplemented"})
public abstract class AntDomElement implements DomElement {
  public static enum Role {
    TASK, DATA_TYPE
  }
  public static final Key<Role> ROLE = Key.create("element_role");

  @Attribute("id")
  public abstract GenericAttributeValue<String> getId();

  @Attribute("refid")
  @Convert(value = AntDomRefIdConverter.class)
  public abstract GenericAttributeValue<AntDomElement> getRefId();

  public final AntDomProject getAntProject() {
    return getParentOfType(AntDomProject.class, false);
  }

  public final AntDomProject getContextAntProject() {
    final AntConfigurationBase antConfig = AntConfigurationBase.getInstance(getManager().getProject());
    final XmlElement xmlElement = getXmlElement();
    if (xmlElement == null) {
      return getAntProject();
    }
    PsiFile containingFile = xmlElement.getContainingFile();
    if (containingFile != null) {
      containingFile = containingFile.getOriginalFile();
    }
    if (!(containingFile instanceof XmlFile)) {
      return getAntProject();
    }
    final XmlFile contextFile = antConfig.getEffectiveContextFile(((XmlFile)containingFile));
    if (contextFile == null) {
      return getAntProject();
    }
    return AntSupport.getAntDomProject(contextFile);
  }

  public final List<AntDomElement> getAntChildren() {
    return DomUtil.getDefinedChildrenOfType(this, AntDomElement.class, true, false);
  }

  public String toString() {
    final XmlTag tag = getXmlTag();
    if (tag == null) {
      return super.toString();
    }
    final String name = tag.getName();
    if ("".equals(name)) {
      return super.toString();
    }
    return name;
  }

}
