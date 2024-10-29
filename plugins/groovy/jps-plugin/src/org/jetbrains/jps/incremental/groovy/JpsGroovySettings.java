// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.jps.incremental.groovy;

import com.intellij.util.xmlb.annotations.Tag;
import org.jdom.Element;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.jps.model.JpsElementChildRole;
import org.jetbrains.jps.model.JpsProject;
import org.jetbrains.jps.model.ex.JpsElementBase;
import org.jetbrains.jps.model.ex.JpsElementChildRoleBase;
import org.jetbrains.jps.model.java.compiler.JpsCompilerExcludes;
import org.jetbrains.jps.model.java.impl.compiler.JpsCompilerExcludesImpl;
import org.jetbrains.jps.model.serialization.java.compiler.JpsJavaCompilerConfigurationSerializer;

import java.io.File;

public class JpsGroovySettings extends JpsElementBase<JpsGroovySettings> {
  static final JpsElementChildRole<JpsGroovySettings> ROLE = JpsElementChildRoleBase.create("Groovy Compiler Configuration");
  public static final boolean DEFAULT_INVOKE_DYNAMIC = false;
  public static final boolean DEFAULT_TRANSFORMS_OK = false;

  public String configScript = "";
  public boolean invokeDynamic = DEFAULT_INVOKE_DYNAMIC;

  @Tag("excludes") public Element excludes = new Element("aaa");

  public boolean transformsOk = DEFAULT_TRANSFORMS_OK;
  private JpsCompilerExcludes myExcludeFromStubGeneration;

  public JpsGroovySettings() {
  }

  private JpsGroovySettings(JpsGroovySettings original) {
    invokeDynamic = original.invokeDynamic;
    configScript = original.configScript;
  }

  void initExcludes() {
    myExcludeFromStubGeneration = new JpsCompilerExcludesImpl();
    JpsJavaCompilerConfigurationSerializer.readExcludes(excludes, myExcludeFromStubGeneration);
  }

  @Override
  public @NotNull JpsGroovySettings createCopy() {
    return new JpsGroovySettings(this);
  }

  public static @NotNull JpsGroovySettings getSettings(@NotNull JpsProject project) {
    JpsGroovySettings settings = project.getContainer().getChild(ROLE);
    return settings == null ? new JpsGroovySettings() : settings;
  }

  public boolean isExcludedFromStubGeneration(File file) {
    return myExcludeFromStubGeneration != null && myExcludeFromStubGeneration.isExcluded(file);
  }
}
