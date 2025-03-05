// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.jps.eclipse.model;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.JDOMUtil;
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
import java.util.HashSet;
import java.util.List;
import java.util.Map;

public final class JpsEclipseClasspathSerializer extends JpsModuleClasspathSerializer {
  public static final @NonNls String CLASSPATH_STORAGE_ID = "eclipse";
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
      final Map<String, String> levels = new HashMap<>();
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

      final JpsEclipseClasspathReader reader = new JpsEclipseClasspathReader(classpathDir, paths, new HashSet<>(), levels);
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
