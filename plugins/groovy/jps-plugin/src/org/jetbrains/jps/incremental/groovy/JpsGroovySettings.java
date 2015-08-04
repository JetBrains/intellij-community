/*
 * Copyright 2000-2013 JetBrains s.r.o.
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

/**
 * @author peter
 */
public class JpsGroovySettings extends JpsElementBase<JpsGroovySettings> {
  static final JpsElementChildRole<JpsGroovySettings> ROLE = JpsElementChildRoleBase.create("Groovy Compiler Configuration");
  public static final String DEFAULT_HEAP_SIZE = "400";
  public static final boolean DEFAULT_INVOKE_DYNAMIC = false;
  public static final boolean DEFAULT_TRANSFORMS_OK = false;

  public String configScript = "";
  public String heapSize = DEFAULT_HEAP_SIZE;
  public boolean invokeDynamic = DEFAULT_INVOKE_DYNAMIC;

  @Tag("excludes") public Element excludes = new Element("aaa");

  public boolean transformsOk = DEFAULT_TRANSFORMS_OK;
  private JpsCompilerExcludes myExcludeFromStubGeneration;

  public JpsGroovySettings() {
  }

  private JpsGroovySettings(JpsGroovySettings original) {
    heapSize = original.heapSize;
    invokeDynamic = original.invokeDynamic;
    configScript = original.configScript;
  }

  void initExcludes() {
    myExcludeFromStubGeneration = new JpsCompilerExcludesImpl();
    JpsJavaCompilerConfigurationSerializer.readExcludes(excludes, myExcludeFromStubGeneration);
  }

  @NotNull
  @Override
  public JpsGroovySettings createCopy() {
    return new JpsGroovySettings(this);
  }

  @Override
  public void applyChanges(@NotNull JpsGroovySettings modified) {
  }

  @NotNull
  public static JpsGroovySettings getSettings(@NotNull JpsProject project) {
    JpsGroovySettings settings = project.getContainer().getChild(ROLE);
    return settings == null ? new JpsGroovySettings() : settings;
  }

  public boolean isExcludedFromStubGeneration(File file) {
    return myExcludeFromStubGeneration != null && myExcludeFromStubGeneration.isExcluded(file);
  }
}
