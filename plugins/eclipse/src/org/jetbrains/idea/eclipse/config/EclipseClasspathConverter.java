/*
 * Copyright 2000-2015 JetBrains s.r.o.
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
package org.jetbrains.idea.eclipse.config;

import com.intellij.openapi.module.Module;
import com.intellij.openapi.roots.CompilerModuleExtension;
import com.intellij.openapi.roots.ModifiableRootModel;
import com.intellij.openapi.roots.impl.storage.ClasspathStorageProvider;
import org.jdom.Element;
import org.jdom.JDOMException;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.idea.eclipse.EclipseXml;
import org.jetbrains.idea.eclipse.conversion.EclipseClasspathReader;
import org.jetbrains.idea.eclipse.conversion.IdeaSpecificSettings;

import java.io.IOException;
import java.util.List;

public final class EclipseClasspathConverter implements ClasspathStorageProvider.ClasspathConverter {
  private final Module module;

  public EclipseClasspathConverter(@NotNull Module module) {
    this.module = module;
  }

  @NotNull
  @Override
  public List<String> getFilePaths() {
    return getFileSet().getFilePaths();
  }

  @Override
  @NotNull
  public ClasspathSaveSession startExternalization() {
    return new ClasspathSaveSession(module);
  }

  @NotNull
  public CachedXmlDocumentSet getFileSet() {
    return EclipseClasspathStorageProvider.getFileCache(module);
  }

  @Override
  public void readClasspath(@NotNull ModifiableRootModel model) throws IOException {
    try {
      CachedXmlDocumentSet fileSet = getFileSet();
      String path = fileSet.getParent(EclipseXml.PROJECT_FILE);
      Element classpath = null;
      if (!fileSet.exists(EclipseXml.PROJECT_FILE)) {
        classpath = fileSet.load(EclipseXml.CLASSPATH_FILE, false);
        if (classpath == null) {
          return;
        }

        path = fileSet.getParent(EclipseXml.CLASSPATH_FILE);
      }

      EclipseClasspathReader classpathReader = new EclipseClasspathReader(path, module.getProject(), null);
      classpathReader.init(model);

      if (classpath == null) {
        classpath = fileSet.load(EclipseXml.CLASSPATH_FILE, false);
      }

      if (classpath == null) {
        EclipseClasspathReader.setOutputUrl(model, path + "/bin");
      }
      else {
        classpathReader.readClasspath(model, classpath);
      }

      Element eml = fileSet.load(model.getModule().getName() + EclipseXml.IDEA_SETTINGS_POSTFIX, false);
      if (eml == null) {
        model.getModuleExtension(CompilerModuleExtension.class).setExcludeOutput(false);
      }
      else {
        new IdeaSpecificSettings().readIdeaSpecific(eml, model, null, null);
      }
    }
    catch (JDOMException e) {
      throw new IOException(e);
    }
  }
}
