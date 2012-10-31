package org.jetbrains.jps.eclipse.model;

import com.intellij.openapi.util.JDOMUtil;
import com.intellij.util.containers.HashSet;
import org.jdom.Document;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.idea.eclipse.EclipseXml;
import org.jetbrains.jps.model.module.JpsModule;
import org.jetbrains.jps.model.serialization.JpsMacroExpander;
import org.jetbrains.jps.model.serialization.module.JpsModuleClasspathSerializer;

import java.io.File;
import java.util.List;

/**
 * @author nik
 */
public class JpsEclipseClasspathSerializer extends JpsModuleClasspathSerializer {
  @NonNls public static final String CLASSPATH_STORAGE_ID = "eclipse";

  public JpsEclipseClasspathSerializer() {
    super(CLASSPATH_STORAGE_ID);
  }

  @Override
  public void loadClasspath(@NotNull JpsModule module,
                            @Nullable String classpathDir,
                            @NotNull String baseModulePath,
                            JpsMacroExpander expander, List<String> paths) {
    try {
      final Document document = JDOMUtil.loadDocument(new File(classpathDir, EclipseXml.DOT_CLASSPATH_EXT));
      final JpsEclipseClasspathReader reader = new JpsEclipseClasspathReader(baseModulePath, paths, new HashSet<String>());
      reader.readClasspath(module, null, document.getRootElement(), expander);//todo
    }
    catch (Exception e) {
      e.printStackTrace();
    }
  }
}
