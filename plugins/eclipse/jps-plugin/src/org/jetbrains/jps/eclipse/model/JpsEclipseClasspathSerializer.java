/*
 * Copyright 2000-2016 JetBrains s.r.o.
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
package org.jetbrains.jps.eclipse.model;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.JDOMUtil;
import com.intellij.util.containers.HashSet;
import org.jdom.Element;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.idea.eclipse.EclipseXml;
import org.jetbrains.jps.model.library.sdk.JpsSdkType;
import org.jetbrains.jps.model.module.JpsDependenciesList;
import org.jetbrains.jps.model.module.JpsModule;
import org.jetbrains.jps.model.serialization.JpsMacroExpander;
import org.jetbrains.jps.model.serialization.module.JpsModuleClasspathSerializer;

import java.io.File;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * @author nik
 */
public class JpsEclipseClasspathSerializer extends JpsModuleClasspathSerializer {
  @NonNls public static final String CLASSPATH_STORAGE_ID = "eclipse";
  private static final Logger LOG = Logger.getInstance(JpsEclipseClasspathSerializer.class);

  public JpsEclipseClasspathSerializer() {
    super(CLASSPATH_STORAGE_ID);
  }

  @Override
  public void loadClasspath(@NotNull JpsModule module,
                            @Nullable String classpathDir,
                            @NotNull String baseModulePath,
                            JpsMacroExpander expander,
                            List<String> paths,
                            JpsSdkType<?> projectSdkType) {
    final JpsDependenciesList dependenciesList = module.getDependenciesList();
    dependenciesList.clear();
    try {
      if (classpathDir == null) classpathDir = baseModulePath;
      final File classpathFile = new File(classpathDir, EclipseXml.DOT_CLASSPATH_EXT);
      if (!classpathFile.exists()) return; //no classpath file - no compilation

      final String eml = module.getName() + EclipseXml.IDEA_SETTINGS_POSTFIX;
      final File emlFile = new File(baseModulePath, eml);
      final Map<String, String> levels = new HashMap<String, String>();
      final JpsIdeaSpecificSettings settings;
      final Element root;
      if (emlFile.isFile()) {
        root = JDOMUtil.load(emlFile);
        settings = new JpsIdeaSpecificSettings(expander);
        settings.initLevels(root, module, levels);
      } else {
        settings = null;
        root = null;
      }

      final JpsEclipseClasspathReader reader = new JpsEclipseClasspathReader(classpathDir, paths, new HashSet<String>(), levels);
      reader.readClasspath(module, null, JDOMUtil.load(classpathFile), expander);//todo
      if (settings != null) {
        settings.updateEntries(root, module, projectSdkType);
      }
    }
    catch (Exception e) {
      LOG.info(e);
      throw new RuntimeException(e);
    }
  }
}
