/*
 * Copyright 2000-2012 JetBrains s.r.o.
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

import org.jetbrains.annotations.Nullable;
import org.jetbrains.jps.model.serialization.JpsModelSerializerExtension;
import org.jetbrains.jps.model.serialization.module.JpsModuleClasspathSerializer;

/**
 * @author nik
 */
public class JpsEclipseModelSerializerExtension extends JpsModelSerializerExtension {
  private static final JpsEclipseClasspathSerializer CLASSPATH_SERIALIZER = new JpsEclipseClasspathSerializer();

  @Nullable
  @Override
  public JpsModuleClasspathSerializer getClasspathSerializer() {
    return CLASSPATH_SERIALIZER;
  }
}
