/*
 * Copyright 2000-2008 JetBrains s.r.o.
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

package org.jetbrains.groovy.compiler.rt;

import org.codehaus.groovy.control.CompilerConfiguration;

import java.net.URL;
import java.net.MalformedURLException;
import java.util.List;
import java.io.File;

/**
 * @author ilyas
 */
public class GroovyCompilerUtil {
  static URL[] convertClasspathToUrls(CompilerConfiguration compilerConfiguration) {
    try {
      return classpathAsUrls(compilerConfiguration);
    } catch (MalformedURLException e) {
      throw new RuntimeException(e);
    }
  }

  static URL[] classpathAsUrls(CompilerConfiguration compilerConfiguration) throws MalformedURLException {
    List classpath = compilerConfiguration.getClasspath();
    URL[] classpathUrls = new URL[classpath.size()];
    for (int i = 0; i < classpathUrls.length; i++) {
      String classpathEntry = (String) classpath.get(i);
      classpathUrls[i] = new File(classpathEntry).toURI().toURL();
    }
    return classpathUrls;
  }
}
