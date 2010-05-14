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
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.Trinity;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiFileSystemItem;
import com.intellij.psi.xml.XmlTag;
import com.intellij.util.xml.*;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.Map;

/**
 * @author Eugene Zhuravlev
 *         Date: Apr 6, 2010
 */
@SuppressWarnings({"AbstractClassNeverImplemented"})
@DefinesXml
public abstract class AntDomProject extends AntDomElement {
  private static final Logger LOG = Logger.getInstance("#com.intellij.lang.ant.dom.AntDomProject");
  private ClassLoader myClassLoader;

  @Attribute("name")
  public abstract GenericAttributeValue<String> getName();

  @Attribute("default")
  @Convert(value = AntDomDefaultTargetConverter.class)
  public abstract GenericAttributeValue<Trinity<AntDomTarget, String, Map<String, AntDomTarget>>> getDefaultTarget();

  @Attribute("basedir")
  @Convert(value = AntPathConverter.class)
  public abstract GenericAttributeValue<PsiFileSystemItem> getBasedir();

  @Nullable
  public final String getProjectBasedirPath() {
    final String basedir = getBasedir().getStringValue();
    if (basedir != null) {
      final File file = new File(basedir);
      if (file.isAbsolute()) {
        try {
          return FileUtil.toSystemIndependentName(file.getCanonicalPath());
        }
        catch (IOException e) {
          LOG.info(e);
          return null;
        }
      }
    }
    final String selfDir = getContainingFileDir();
    if (basedir == null) {
      return selfDir;
    }
    // basedir is specified and is relative
    try {
      return FileUtil.toSystemIndependentName(new File(selfDir, basedir).getCanonicalPath());
    }
    catch (IOException e) {
      LOG.info(e);
      return null;
    }
  }

  @Nullable
  public final String getContainingFileDir() {
    final VirtualFile containingFile = getXmlTag().getContainingFile().getOriginalFile().getVirtualFile();
    return containingFile != null ? containingFile.getParent().getPath() : null;
  }

  @SubTagList("target")
  public abstract List<AntDomTarget> getDeclaredTargets();

  @SubTagList("import")
  public abstract List<AntDomImport> getDeclaredImports();

  @SubTagList("include")
  public abstract List<AntDomInclude> getDeclaredIncludes();

  @Nullable
  public final AntDomTarget findDeclaredTarget(String declaredName, DomElement contextElement) {
    for (AntDomTarget target : getDeclaredTargets()) {
      if (declaredName.equals(target.getName().getRawText())) {
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
