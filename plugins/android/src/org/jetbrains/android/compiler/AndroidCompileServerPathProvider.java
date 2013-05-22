package org.jetbrains.android.compiler;

import com.google.common.collect.Maps;
import com.intellij.compiler.server.CompileServerPathProvider;
import com.intellij.openapi.application.PathManager;
import org.jetbrains.annotations.NotNull;

import java.util.Collections;
import java.util.List;

/**
 * @author Eugene.Kudelevsky
 */
public class AndroidCompileServerPathProvider implements CompileServerPathProvider {
  @NotNull
  @Override
  public List<String> getClassPath() {
    // guava jar
    return Collections.singletonList(PathManager.getJarPathForClass(Maps.class));
  }
}
