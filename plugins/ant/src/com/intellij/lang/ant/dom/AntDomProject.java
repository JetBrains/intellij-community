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

import com.intellij.lang.ant.config.AntBuildFile;
import com.intellij.lang.ant.config.AntConfigurationBase;
import com.intellij.lang.ant.config.impl.AntBuildFileImpl;
import com.intellij.lang.ant.config.impl.AntInstallation;
import com.intellij.lang.ant.config.impl.GlobalAntConfiguration;
import com.intellij.psi.xml.XmlTag;
import com.intellij.util.xml.Attribute;
import com.intellij.util.xml.GenericAttributeValue;
import com.intellij.util.xml.SubTagList;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * @author Eugene Zhuravlev
 *         Date: Apr 6, 2010
 */
@SuppressWarnings({"AbstractClassNeverImplemented"})
public abstract class AntDomProject extends AntDomElement {
  private ClassLoader myClassLoader;

  @Attribute("name")
  public abstract GenericAttributeValue<String> getName();

  @Attribute("default")
  public abstract GenericAttributeValue<String> getDefaultTargetName();

  @Attribute("basedir")
  public abstract GenericAttributeValue<String> getBasedir();

  @SubTagList("target")
  public abstract List<AntDomTarget> getTargets();

  @Nullable
  public final AntDomTarget getTarget(String name) {
    for (AntDomTarget target : getTargets()) {
      if (name.equals(target.getName().getValue())) {
        return target;
      }
    }
    return null;
  }

  @NotNull
  public final ClassLoader getClassLoader() {
    if (myClassLoader == null) {
      final XmlTag tag = getXmlTag();
      final AntBuildFileImpl buildFile = (AntBuildFileImpl)tag.getCopyableUserData(AntBuildFile.ANT_BUILD_FILE_KEY);
      if (buildFile != null) {
        myClassLoader = buildFile.getClassLoader();
      }
      else {
        final AntConfigurationBase configuration = AntConfigurationBase.getInstance(tag.getProject());
        AntInstallation antInstallation = null;
        if (configuration != null) {
          antInstallation = configuration.getProjectDefaultAnt();
        }
        if (antInstallation == null) {
          antInstallation = GlobalAntConfiguration.getInstance().getBundledAnt();
        }
        assert antInstallation != null;
        myClassLoader = antInstallation.getClassLoader();
      }
    }
    return myClassLoader;
  }
}
